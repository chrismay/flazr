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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.common.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flazr.Packet.Type;

public class FlvWriter {
	
	private static final Logger logger = LoggerFactory.getLogger(FlvWriter.class);
	
	private ByteBuffer out;
	private FileChannel channel;
	private FileOutputStream fos;	
	
	private Map<Integer, Integer> channelTimeMap = new ConcurrentHashMap<Integer, Integer>();
	private int videoChannel = -1;
	private double lastLoggedSeconds;		
	
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
		Integer time = channelTimeMap.get(videoChannel);
		if(time == null) {
			return;
		}
		logger.info("closed file, video duration: " + time / 1000 + " seconds");
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
		final int channelId = header.getChannelId();		
		Integer channelTime = channelTimeMap.get(channelId);		
		if(channelTime == null) {
			channelTime = 0;
		}
		if(videoChannel == -1 && header.getPacketType() == Type.VIDEO_DATA) {
			videoChannel = channelId;
			logger.info("video channel set to: " + videoChannel);
		}
		channelTime = channelTime + header.getTime();		
		channelTimeMap.put(channelId, channelTime);	
		write(header.getPacketType(), packet.getData(), channelTime);
	}		
	
	public synchronized void writeFlvData(ByteBuffer data) {
		while(data.hasRemaining()) {		
			Type packetType = Type.parseByte(data.get());												
			int size = readInt24(data);			
			int timestamp = readInt24(data);
			if(videoChannel == -1) {
				throw new RuntimeException("video channel not initialized!");
			}			
			channelTimeMap.put(videoChannel, timestamp); // absolute
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
		//==========	
		if(packetType == Type.VIDEO_DATA) {
			double seconds = time / 1000;
			if(seconds >= lastLoggedSeconds + 10) {
				logger.info("video write progress: " + seconds + " seconds");
				lastLoggedSeconds = seconds;
			}
		}
	}
	
	private void write(ByteBuffer buffer) {		
		try {
			channel.write(buffer.buf());
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private int readInt24(ByteBuffer in) {
		int val = 0;
		val += (in.get() & 0xFF) * 256 * 256;
		val += (in.get() & 0xFF) * 256;
		val += (in.get() & 0xFF);
		return val;
	}		
	
}
