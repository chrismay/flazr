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
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtmpeIoFilter extends IoFilterAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(RtmpeIoFilter.class);

	@Override
	public void messageReceived(NextFilter nextFilter, IoSession ioSession, Object message) throws Exception {
		RtmpSession session = RtmpSession.getFrom(ioSession);
		if(!session.isEncrypted() || !session.isHandshakeComplete() || !(message instanceof ByteBuffer)) {
			if(logger.isDebugEnabled()) {
				logger.debug("not decrypting message received");
			}
            nextFilter.messageReceived(ioSession, message);
            return;			
		}
		ByteBuffer in = (ByteBuffer) message;
		byte[] encrypted = new byte[in.remaining()];
		in.get(encrypted);
		if(logger.isDebugEnabled()) {
			in.rewind();
			logger.debug("decrypting buffer: " + in);
		}    	
		in.release();
		byte[] plain = session.getCipherIn().update(encrypted);		
		ByteBuffer out = ByteBuffer.wrap(plain);
		if(logger.isDebugEnabled()) {			
			logger.debug("decrypted buffer: " + out);
		}		
		nextFilter.messageReceived(ioSession, out);
	}
	
	@Override
	public void filterWrite(NextFilter nextFilter, IoSession ioSession, WriteRequest writeRequest) throws Exception {
		RtmpSession session = RtmpSession.getFrom(ioSession);
		if(!session.isEncrypted() || !session.isHandshakeComplete()) {
			if(logger.isDebugEnabled()) {
				logger.debug("not encrypting write request");
			}
			nextFilter.filterWrite(ioSession, writeRequest);
            return;			
		}
		
        ByteBuffer in = (ByteBuffer) writeRequest.getMessage();
        if (!in.hasRemaining()) {
            // Ignore empty buffers
            nextFilter.filterWrite(ioSession, writeRequest);
        } else {        	
    		if(logger.isDebugEnabled()) {    			
    			logger.debug("encrypting buffer: " + in);
    		}         	
			byte[] plain = new byte[in.remaining()];
			in.get(plain);
			in.release();
			byte[] encrypted = session.getCipherOut().update(plain);
			ByteBuffer out = ByteBuffer.wrap(encrypted);
    		if(logger.isDebugEnabled()) {    			
    			logger.debug("encrypted buffer: " + out);
    		}  			
            nextFilter.filterWrite(ioSession, new WriteRequest(out, writeRequest.getFuture()));
        }		
	}
	
}
