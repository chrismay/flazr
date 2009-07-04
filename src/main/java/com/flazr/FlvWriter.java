/*
 * Copyright 2002-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flazr;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import org.apache.mina.common.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flazr.Packet.Type;

public class FlvWriter implements OutputWriter {
	
	private static final Logger logger = LoggerFactory.getLogger(FlvWriter.class);
		
	private ByteBuffer out;
	private FileChannel channel;
	private FileOutputStream fos;	
	private WriterStatus status;	
	
	public FlvWriter(int seekTime, String fileName) {	
		status = new WriterStatus(seekTime);
		try {
			File file = new File(fileName);
			fos = new FileOutputStream(file);
			channel = fos.getChannel();
			logger.info("opened file for writing: " + file.getAbsolutePath());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		out = ByteBuffer.allocate(1024);
		out.setAutoExpand(true);		
		writeHeader();	
	}
	
	public void close() {
		try {
			channel.close();
			fos.close();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}		
		status.logFinalVideoDuration();
	}
	
	private void writeHeader() {
		out.put((byte) 0x46); // F
		out.put((byte) 0x4C); // L
		out.put((byte) 0x56); // V
		out.put((byte) 0x01); // version		
		out.put((byte) 0x05); // flags: audio + video
		out.putInt(0x09); // header size = 9
		out.putInt(0); // previous tag size, here = 0
		out.flip();
		write(out);				
	}	
	
	public synchronized void write(Packet packet) {			
		Header header = packet.getHeader();
		int time = status.getChannelAbsoluteTime(header);
		write(header.getPacketType(), packet.getData(), time);
	}		
	
	public synchronized void writeFlvData(ByteBuffer data) {
		while(data.hasRemaining()) {		
			Type packetType = Type.parseByte(data.get());												
			int size = Utils.readInt24(data);			
			int timestamp = Utils.readInt24(data);
			status.updateVideoChannelTime(timestamp);
			data.getInt(); // 4 bytes of zeros (reserved)
			byte[] bytes = new byte[size];
			data.get(bytes);
			ByteBuffer temp = ByteBuffer.wrap(bytes);
			write(packetType, temp, timestamp);  
			data.getInt(); // FLV tag size (size + 11)
		}		
	}
	
	public synchronized void write(Type packetType, ByteBuffer data, final int time) {		
		if(logger.isDebugEnabled()) {
			logger.debug("writing FLV tag {} t{} {}", new Object[]{ packetType, time, data});
		}				
		out.clear();
		out.put(packetType.byteValue());		
		final int size = data.limit();
		Utils.writeInt24(out, size);
		Utils.writeInt24(out, time);
		out.putInt(0); // 4 bytes of zeros (reserved)	
		out.flip();
		write(out);			
		write(data);		
		//==========
		out.clear();
		out.putInt(size + 11); // previous tag size
		out.flip();
		write(out);
	}
	
	private void write(ByteBuffer buffer) {		
		try {
			channel.write(buffer.buf());
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}		
	
}
