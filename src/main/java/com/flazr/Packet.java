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

import static com.flazr.Header.Type.LARGE;
import static com.flazr.Header.Type.MEDIUM;
import static com.flazr.Header.Type.TINY;
import static com.flazr.Packet.Type.CONTROL_MESSAGE;
import static com.flazr.Packet.Type.SERVER_BANDWIDTH;

import org.apache.mina.common.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Packet {
	
	private static final Logger logger = LoggerFactory.getLogger(Packet.class);
	
	public static enum Type implements ByteToEnum.Convert {
		
		CHUNK_SIZE(0x01),
		// unknown 0x02
		BYTES_READ(0x03),
		CONTROL_MESSAGE(0x04),
		SERVER_BANDWIDTH(0x05),
		CLIENT_BANDWIDTH(0x06),
		// unknown 0x07
		AUDIO_DATA(0x08),
		VIDEO_DATA(0x09),
		// unknown 0x0A - 0x0E
		FLEX_STREAM_SEND(0x0F),
		FLEX_SHARED_OBJECT(0x10),
		FLEX_MESSAGE(0x11),
		NOTIFY(0x12),
		SHARED_OBJECT(0x13),
		INVOKE(0x14),
		FLV_DATA(0x16);
		
		private final byte value;
		
		private Type(int value) {
			this.value = (byte) value;
		}
		
        public byte byteValue() {
            return value;
        }

        private static ByteToEnum<Type> converter = new ByteToEnum<Type>(Type.values());
        
        public static Type parseByte(byte b) {
            return converter.parseByte(b);
        }    
        
        @Override
        public String toString() {
            return converter.toString(this);
        }		
		
	}	
	
	private Header header;
	private ByteBuffer data;	
	private boolean complete;
	
	public Packet() { }
	
	public Packet(Header header, ByteBuffer data) {
		this.header = header;
		data.flip();
		this.data = data;
		header.setSize(data.limit());
	}				
	
	public Packet(Header header, int dataSize) {
		this.header = header;
		data = ByteBuffer.allocate(dataSize);
	}
	
    public static Packet serverBw(int value) {    	
    	Header header = new Header(LARGE, 2, SERVER_BANDWIDTH);    	
    	ByteBuffer body = ByteBuffer.allocate(8);
    	body.putInt(value);
    	return new Packet(header, body);
    }
    
    public static Packet ping(int type, int target, int bufferTime) {    	
    	Header header = new Header(MEDIUM, 2, CONTROL_MESSAGE);    	
    	ByteBuffer body = ByteBuffer.allocate(10);
    	body.putShort((short) type);
    	body.putInt(target);
    	if(type == 3) {
    		body.putInt(bufferTime);
    	}
    	return new Packet(header, body);    	    
    }	
	
	public Header getHeader() {
		return header;
	}
	
	public ByteBuffer getData() {
		return data;
	}
	
	public void setData(ByteBuffer data) {
		this.data = data;
	}
	
	public boolean isComplete() {
		return complete;
	}
	
	public boolean decode(ByteBuffer in, RtmpSession session) {

		final int position = in.position();

		header = new Header();     				
        
    	if(!header.decode(in, session)) {
    		return false;
    	}    	    	    	
    	    	    	    	        	
    	final int channelId = header.getChannelId();    	
    	Packet prevPacket = session.getPrevPacketsIn().get(channelId);
    	
    	int toReadRemaining = header.getSize();    	
    	if(prevPacket != null) {
    		toReadRemaining -= prevPacket.getData().position();
    	}
    	
		final int chunkSize = session.getChunkSize();		
		final int toReadNow = toReadRemaining > chunkSize ? chunkSize : toReadRemaining;
    	
		if (in.remaining() < toReadNow) {
			return false;
		}		
		
		session.getPrevHeadersIn().put(channelId, header);
    	
		if (prevPacket == null) {			
			prevPacket = new Packet(header, header.getSize());			
			session.getPrevPacketsIn().put(channelId, prevPacket);			
		} else {			
			logger.debug("continuing prev header: " + prevPacket.header);			
			header.setRelative(prevPacket.header.isRelative());
		}
		
        if(logger.isDebugEnabled()) {        	
        	byte[] bytes = new byte[in.position() - position];
        	for(int i = 0; i < bytes.length; i++) {
        		bytes[i] = in.get(position + i);
        	}       	
        	logger.debug("decoded header: " + header +  " <-- " + Utils.toHex(bytes));
        }		

		data = prevPacket.getData();						
		byte[] bytes = new byte[toReadNow];
		in.get(bytes);		
		data.put(bytes);
		
		if(logger.isDebugEnabled()) {
			logger.debug("chunk bytes read {}: {}", toReadNow, Utils.toHex(bytes));
		}		
		
		if(data.position() == header.getSize()) {
			complete = true;
			session.getPrevPacketsIn().remove(channelId);			
			data.flip();
			if(logger.isDebugEnabled()) {
				logger.debug("decoded packet: " + toString());
			}
		}
		
		return true;		
	}
	
	public ByteBuffer encode(final int chunkSize) {
    	ByteBuffer buffer = ByteBuffer.allocate(1024);
    	header.encode(buffer);
    	int remaining = header.getSize();    	     
    	while(true) {
    		final int toWrite = remaining > chunkSize ? chunkSize : remaining;
    		byte[] bytes = new byte[toWrite];
    		data.get(bytes);
    		buffer.put(bytes);
    		remaining -= chunkSize;
    		if(remaining > 0) {
    			Header tiny = new Header(TINY, header.getChannelId(), header.getPacketType());
    			tiny.encode(buffer);
    		} else {
    			break;
    		}        		
    	}        	
    	buffer.flip();
    	return buffer;
	}
	
	@Override
	public String toString() {
		return data.toString();
	}

}
