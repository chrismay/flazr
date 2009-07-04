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
        	if(!Handshake.decodeServerResponse(in, session)) {        		
        		return false;
        	}
        	session.setServerHandshakeReceived(true);        	
    		logger.info("server handshake processed, sending reply");
    		session.send(Handshake.generateClientRequest2(session));    		
    		session.send(new Invoke("connect", 3, session.getConnectParams()));
    		if(session.getSaveAsFileName() == null) {
    			logger.info("'save as' file name is null, stream will not be saved");
    			session.setOutputWriter(new DummyWriter(session.getPlayStart()));
    		} else {
    			session.setOutputWriter(new FlvWriter(session.getPlayStart(), session.getSaveAsFileName()));
    		}    		
			return true;        	
        }                
        
        final int position = in.position();
        Packet packet = new Packet();              
        
    	if(!packet.decode(in, session)) {
    		in.position(position);
    		return false;
    	}    	
    	
		if (!packet.isComplete()) { // but finished decoding chunk
			return true;
		}
		
		if(logger.isDebugEnabled()) {
			logger.debug("packet complete: " + packet);			
		}		
		
		ByteBuffer data = packet.getData();
    	
		switch(packet.getHeader().getPacketType()) {
			case CHUNK_SIZE:
				int newChunkSize = data.getInt();
				session.setChunkSize(newChunkSize);
				logger.info("new chunk size is: " + newChunkSize);
				break;
			case CONTROL_MESSAGE:					
				short type = data.getShort();				
				if(type == 6) {
					int time = data.getInt();
					data.rewind();
					logger.info("server ping: " + packet);	
					Packet pong = Packet.ping(7, time, -1); // 7 == pong type
					logger.info("client pong: " + pong);
					session.send(pong);
				} else if(type == 0x001A) {
					logger.info("server swf verification request: " + packet);
					byte[] swfv = session.getSwfVerification();
					if(swfv == null) {
						logger.warn("not sending swf verification response! connect parameters not set"
								+ ", server likely to stop responding");						
					} else {
						Packet pong = Packet.swfVerification(session.getSwfVerification());
						logger.info("sending client swf verification response: " + pong);
						session.send(pong);
					}
				} else {					
					logger.debug("not handling unknown control message type: " + type + " " + packet);						
				}
				break;
			case AUDIO_DATA:
			case VIDEO_DATA:				
				session.getOutputWriter().write(packet);
				break;
			case FLV_DATA:
				session.getOutputWriter().writeFlvData(data);				
				break;				
			case NOTIFY:			
				AmfObject notify = new AmfObject();
				notify.decode(data, false);
				String notifyMethod = notify.getFirstPropertyAsString();
				logger.info("server notify: " + notify);
				if(notifyMethod.equals("onMetaData")) {
					logger.info("notify is 'onMetadata', writing metadata");
					data.rewind();
					session.getOutputWriter().write(packet);
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
					logger.info("unhandled server invoke: " + serverInvoke);
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
