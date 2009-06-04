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

public class FlvWriter {
	
	private static final Logger logger = LoggerFactory.getLogger(FlvWriter.class);
	
	private ByteBuffer out;
	private FileChannel channel;
	private FileOutputStream fos;	
	
	private int videoTs;
	private int audioTs;
	private int lastLoggedSeconds;	
	
	public FlvWriter(String fileName) {		
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
		double duration = videoTs / 1000;
		logger.info("closed file, video duration: " + duration + " seconds");
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
	
	public void write(Packet packet) {		
		Header header = packet.getHeader();				
		if(header.getSize() == 0) {
			logger.debug("skipping packet where size == 0");
			return;
		}
		if(logger.isDebugEnabled()) {
			logger.debug("writing FLV data");
		}
		write(header.getPacketType(), packet.getData(), header.getTime(), header.isRelative());
	}		
	
	public void writeFlvData(ByteBuffer data) {
		if(logger.isDebugEnabled()) {
			logger.debug("writing FLV data (bulk)");
		}
		while(true) {
			if(data.remaining() < 1) {
				break;
			}			
			Type packetType = Type.parseByte(data.get());			
			int size = Utils.readInt24(data);			
			int timestamp = Utils.readInt24(data);			
			data.getInt(); // 4 bytes of zeros (reserved)
			byte[] bytes = new byte[size];
			data.get(bytes);
			ByteBuffer temp = ByteBuffer.wrap(bytes);
			write(packetType, temp, timestamp, false);  
			data.getInt(); // FLV tag size (size + 11)
		}		
	}
	
	private void write(Type packetType, ByteBuffer data, final int timer, boolean relative) {	
		
		int timestamp = 0;
		if(relative) {
			if(packetType == Type.VIDEO_DATA) {							
				videoTs += timer;
				timestamp = videoTs;
			} else if(packetType == Type.AUDIO_DATA){				
				audioTs += timer;			
				timestamp = audioTs;
			}
		} else {			
			timestamp = timer;
			if(packetType == Type.VIDEO_DATA) {
				videoTs = timestamp;
			} else if(packetType == Type.AUDIO_DATA) {
				audioTs = timestamp;
			}			
		}
			
		int seconds = videoTs / 1000;
		if(seconds >= lastLoggedSeconds + 10) {
			logger.info("video write progress: " + seconds + " seconds");
			lastLoggedSeconds += 10;
		}		
		
		out.clear();
		out.put(packetType.byteValue());		
		Utils.writeInt24(out, data.limit()); // data size
		Utils.writeInt24(out, timestamp);
		out.putInt(0); // 4 bytes of zeros (reserved)	
		out.flip();
		write(out);			
		write(data);		
		
		out.clear();
		out.putInt(data.limit() + 11); // previous tag size
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
