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
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtmpDecoder extends CumulativeProtocolDecoder {		
	
	private static final Logger logger = LoggerFactory.getLogger(RtmpDecoder.class);				

	@Override
	protected boolean doDecode(IoSession ioSession, ByteBuffer in, ProtocolDecoderOutput _unused) {
		return decode(in, RtmpSession.getFrom(ioSession));
	}
	
	public static boolean decode(ByteBuffer in, RtmpSession session) {					
		
        if(!session.isServerHandshakeReceived()) {
        	Handshake hs = new Handshake();
        	if(!hs.decodeServerResponse(in, session)) {        		
        		return false;
        	}
        	session.setServerHandshakeReceived(true);        	
    		logger.info("received server handshake, sending reply");
    		session.send(hs.generateClientRequest2());    		
    		session.send(new Invoke("connect", 3, session.getConnectParams()));        	
			return true;        	
        }                
        
        final int position = in.position();
        Packet packet = new Packet();              
        
    	if(!packet.decode(in, session)) {
    		logger.debug("buffering...");
    		in.position(position);
    		return false;
    	}    
    	
    	final int bytesReadNow = in.position() - position;    	
    	final int bytesReadSoFar = session.incrementBytesRead(bytesReadNow);		
		
		if(bytesReadSoFar > session.getBytesReadLastSent() + 600 * 1024) {
			Header brHeader = new Header(Header.Type.MEDIUM, 2, Packet.Type.BYTES_READ);
			ByteBuffer brBody = ByteBuffer.allocate(4);
			brBody.putInt(bytesReadSoFar);
			Packet brp = new Packet(brHeader, brBody);
			logger.info("sending bytes read " + bytesReadSoFar + ": " + brp);			
			session.send(brp);
			session.setBytesReadLastSent(bytesReadSoFar);
		}    	
    	
		if (!packet.isComplete()) { // but finished decoding chunk
			return true;
		}		
		
		ByteBuffer data = packet.getData();		   	
    	
		switch(packet.getHeader().getPacketType()) {
			case CHUNK_SIZE:
				int newChunkSize = data.getInt();
				session.setChunkSize(newChunkSize);
				logger.info("new chunk size is: " + newChunkSize);
				break;
			case CONTROL_MESSAGE:				
				if(logger.isDebugEnabled()) {					
					logger.debug("received control message: " + packet);
				}					
				short type = data.getShort();				
				if(type == 6) {
					int time = data.getInt();
					data.rewind();
					logger.info("server ping: " + packet);	
					Packet pong = Packet.ping(7, time, -1); // 7 == pong type
					logger.info("client pong: " + pong);
					session.send(pong);
				}
				break;
			case AUDIO_DATA:
			case VIDEO_DATA:				
				session.getFlvWriter().write(packet);
				break;
			case FLV_DATA:
				session.getFlvWriter().writeFlvData(data);				
				break;				
			case NOTIFY:			
				AmfObject notify = new AmfObject();
				notify.decode(data, false);
				String notifyMethod = notify.getFirstPropertyAsString();
				logger.info("server notify: " + notify);
				if(notifyMethod.equals("onMetaData")) {
					logger.info("notify is 'onMetadata', writing metadata");
					data.rewind();
					session.getFlvWriter().write(packet);
				}
				break;
			case INVOKE:			
				Invoke serverInvoke = new Invoke();
				serverInvoke.decode(packet);				
				String methodName = serverInvoke.getMethodName();
				if(methodName.equals("_result")) {
					session.getInvokeResultHandler().handle(serverInvoke, session);					
				} else if(methodName.equals("onStatus")) {
					AmfObject temp = serverInvoke.getSecondArgAsAmfObject();
					String code = (String) temp.getProperty("code").getValue();					
					logger.info("onStatus code: " + code);
					if(code.equals("NetStream.Failed") 
							|| code.equals("NetStream.Play.Failed") || code.equals("NetStream.Play.Stop")) {
						logger.info("disconnecting");
						session.getDecoderOutput().disconnect();
					}
				} else {
					logger.warn("unhandled server invoke: " + serverInvoke);
				}
				break;
			case BYTES_READ:
			case SERVER_BANDWIDTH:
			case CLIENT_BANDWIDTH:
				logger.info("ignoring received packet: " + packet.getHeader());
				break;				
			default:
				throw new RuntimeException("unknown packet type: "  + packet.getHeader());
		}		
		
		return true;
	}  

}
