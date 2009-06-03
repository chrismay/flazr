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

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtmpEncoder implements ProtocolEncoder {
	
	private static final Logger logger = LoggerFactory.getLogger(RtmpEncoder.class);
	
	public void encode(IoSession ioSession, Object object, ProtocolEncoderOutput out) {		
		
		RtmpSession session = RtmpSession.getFrom(ioSession);
		
		if(object instanceof Handshake) {
			Handshake hs = (Handshake) object;
			out.write(hs.getData());							
			if(session.isServerHandshakeReceived()) {				
				if(logger.isDebugEnabled()) {
					logger.debug("sent client handshake part 2: " + hs.getData());
				}
			} else {
				if(logger.isDebugEnabled()) {
					logger.debug("sent client handshake part 1: " + hs.getData());
				}
			}			
			return;
		}
		
		if(!session.isHandshakeComplete()) {
			logger.info("handshake complete, sending first packet after");
			session.setHandshakeComplete(true);
		}
                
    	Packet packet = (Packet) object;
    	ByteBuffer buffer = packet.encode(session.getChunkSize());
    	out.write(buffer);    	
    	if(logger.isDebugEnabled()) {
    		logger.debug("sent packet data: " + buffer);
    	}       

	}
	
	public void dispose(IoSession session) throws Exception { }	

}
