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

import java.net.InetSocketAddress;

import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtmpClient extends IoHandlerAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(RtmpClient.class);
	
	private static final int CONNECT_TIMEOUT = 3000;	
	private RtmpSession session;
	
	public static void main(String[] args) {		
		String host = "localhost";
		int port = 1935;
		String app = "vod";				
		String playName = "mp4:sample1_150kbps.f4v";		
		String saveFileName = "test.flv";			
    	RtmpSession session = new RtmpSession(host, port, app, playName, saveFileName, true); 
    	session.initSwfVerification("videoPlayer.swf");
    	connect(session);    	
	}
	
	private RtmpClient() { }
	
	public static void connect(RtmpSession session) {
		RtmpClient client = new RtmpClient();
		client.session = session;
		SocketConnector connector = new SocketConnector();
		connector.getFilterChain().addLast("crypto", new RtmpeIoFilter());
		connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new RtmpCodecFactory()));		
		connector.connect(new InetSocketAddress(session.getHost(), session.getPort()), client);		
	}   
    
    @Override
    public void sessionOpened(IoSession ioSession) {
    	session.setDecoderOutput(new MinaIoSessionOutput(ioSession));
    	session.putInto(ioSession);
    	logger.info("session opened, starting handshake");
        ioSession.write(Handshake.generateClientRequest1(session));      
    }
    
    @Override
    public void exceptionCaught(IoSession ioSession, Throwable cause) throws Exception {    		
    	logger.error("exceptionCaught: ", cause);  
    	disconnect(ioSession);    	
    }
    
    public static void disconnect(IoSession ioSession) {
		ioSession.close().join(CONNECT_TIMEOUT);
		RtmpSession session = RtmpSession.getFrom(ioSession);
		session.getFlvWriter().close();		
		logger.info("disconnected, bytes read: " + ioSession.getReadBytes());
		System.exit(0);    	
    }
    
	private static class RtmpCodecFactory implements ProtocolCodecFactory {
		
		private ProtocolEncoder encoder = new RtmpEncoder();
		private ProtocolDecoder decoder = new RtmpDecoder();

		public ProtocolDecoder getDecoder() {
			return decoder;
		}

		public ProtocolEncoder getEncoder() {
			return encoder;
		}		
	}  
	
	/**
	 * implementation used for connecting to a network stream
	 */
	private static class MinaIoSessionOutput implements DecoderOutput {
		
		private IoSession ioSession;
		
		public MinaIoSessionOutput(IoSession ioSession) {
			this.ioSession = ioSession;
		}
				
		public void write(Object packet) {
			ioSession.write(packet);
		}

		public void disconnect() {
			RtmpClient.disconnect(ioSession);
		}		
	}	
}
