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

import org.apache.mina.common.CloseFuture;
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
	
	private IoSession ioSession;
	private RtmpSession session;
	private SocketConnector connector;
	
	public static void main(String[] args) {
    	RtmpSession session = new RtmpSession("rtmpe://localhost/vod/mp4:sample1_150kbps.f4v", "test.flv");
    	session.initSwfVerification("videoPlayer.swf");
    	session.setPlayDuration(5000);
    	connect(session);    	
	}
	
	private RtmpClient(RtmpSession session) { 
		this.session = session;
		connector = new SocketConnector();
		connector.getFilterChain().addLast("crypto", new RtmpeIoFilter());
		connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new RtmpCodecFactory()));				
	}
	
	public static void connect(RtmpSession session) {
		RtmpClient client = new RtmpClient(session);	
		client.connector.connect(new InetSocketAddress(session.getHost(), session.getPort()), client);		
	}   
    
    @Override
    public void sessionOpened(IoSession ioSession) {
    	this.ioSession = ioSession;
    	session.setDecoderOutput(new MinaIoSessionOutput(this));
    	session.putInto(ioSession);
    	logger.info("session opened, starting handshake");
        ioSession.write(Handshake.generateClientRequest1(session));      
    }
    
    @Override
    public void exceptionCaught(IoSession ioSession, Throwable cause) throws Exception {    		
    	logger.error("exceptionCaught: ", cause);  
    	disconnect();    	
    }
    
    public void disconnect() {		
		session.getOutputWriter().close();		
		logger.info("disconnecting, bytes read: " + ioSession.getReadBytes());
		connector.setWorkerTimeout(0);		
		CloseFuture future = ioSession.close();
		logger.info("closing connection, waiting for thread exit");
		future.join();
		logger.info("connection closed successfully");	    	
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
				
		private RtmpClient client;
		
		public MinaIoSessionOutput(RtmpClient client) {			
			this.client = client;
		}
				
		public void write(Object packet) {
			client.ioSession.write(packet);
		}

		public void disconnect() {
			client.disconnect();			
		}		
	}	
}
