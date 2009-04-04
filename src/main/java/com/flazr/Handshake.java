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

import java.util.Random;

import org.apache.mina.common.ByteBuffer;

public class Handshake {		
	
	public static final int HANDSHAKE_SIZE = 1536;	
	public static final int HANDSHAKE_SIZE_SERVER = 2 * HANDSHAKE_SIZE + 1;	
	
	private ByteBuffer data;	
	
	public ByteBuffer getData() {
		return data;
	}
	
	public static Handshake generateClientRequest1(RtmpSession session) {
		ByteBuffer buf = ByteBuffer.allocate(HANDSHAKE_SIZE + 1);
		Handshake hs = new Handshake();
		buf.put((byte) 0x03);
        Utils.writeInt32Reverse(buf, (int) System.currentTimeMillis() & 0x7FFFFFFF);
        buf.put(new byte[] { 0x0A, 0x00, 0x0C, 0x02 }); // flash player version             
		byte[] randomBytes = new byte[1528];
		Random random = new Random();
		random.nextBytes(randomBytes);
		buf.put(randomBytes);        
        buf.rewind();
        buf.limit(HANDSHAKE_SIZE + 1);
        hs.data = buf;
		return hs;
	}
	
	public boolean decodeServerResponse(ByteBuffer in, RtmpSession session) {	
    	if(in.remaining() < HANDSHAKE_SIZE_SERVER) {    		
    		return false;
    	}				
		byte[] bytes = new byte[HANDSHAKE_SIZE_SERVER];
		in.get(bytes);
		data = ByteBuffer.wrap(bytes);
		return true;
	}
	
	public Handshake generateClientRequest2() {		
		data.get(); // skip first byte
		byte[] bytes = new byte[HANDSHAKE_SIZE];
		data.get(bytes); // copy first half of server response
		Handshake hs = new Handshake();
		hs.data = ByteBuffer.wrap(bytes);
		return hs;
	}

}
