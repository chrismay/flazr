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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.common.IoSession;

public class RtmpSession {		
	
	private static final String RTMP_SESSION_KEY = "RTMP_SESSION_KEY";	
	
	private boolean serverHandshakeReceived;	
	private Map<Integer, Header> prevHeadersIn = new ConcurrentHashMap<Integer, Header>();
	private Map<Integer, Header> prevHeadersOut = new ConcurrentHashMap<Integer, Header>();
	private Map<Integer, Packet> prevPacketsIn = new ConcurrentHashMap<Integer, Packet>();
	private Map<Integer, String> invokedMethods = new ConcurrentHashMap<Integer, String>();	
	private int chunkSize = 128;
	private int nextInvokeId;	
	private int bytesReadLastSent;
	private int bytesRead;	
	private Map<String, Object> connectParams;
	private String playName;
	private int playStart;
	private int playDuration = -2;
	private FlvWriter flvWriter;
	private DecoderOutput decoderOutput;
	private String host;
	private int port;	
	private InvokeResultHandler invokeResultHandler;
	
	public RtmpSession() { }
	
	public RtmpSession(String host, int port, String app, String playName, String saveFileName) {
		this.host = host;
		this.port = port;		
		this.playName = playName;
		flvWriter = new FlvWriter(saveFileName);		
		String tcUrl = "rtmp://" + host + ":" + port + "/" + app;		
		connectParams = new HashMap<String, Object>();
		connectParams.put("objectEncoding", 0);
		connectParams.put("app", app);		
		connectParams.put("flashVer", "WIN 9,0,115,0");
		connectParams.put("fpad", false);
		connectParams.put("tcUrl", tcUrl);
		connectParams.put("audioCodecs", 1639);
		connectParams.put("videoFunction", 1);		
		connectParams.put("capabilities", 15);		
		connectParams.put("videoCodecs", 252);	
		invokeResultHandler = new DefaultInvokeResultHandler();
	}
	
	public static RtmpSession getFrom(IoSession ioSession) {
		return (RtmpSession) ioSession.getAttribute(RTMP_SESSION_KEY);
	}
	
	public void putInto(IoSession ioSession) {
		ioSession.setAttribute(RTMP_SESSION_KEY, this);
	}	
	
	public void send(Handshake handshake) {
		decoderOutput.write(handshake);
	}	
	
	public void send(Packet packet) {
		decoderOutput.write(packet);
	}
	
	public void send(Invoke invoke) {
		send(invoke.encode(this));
	}
	
	public String resultFor(Invoke invoke) {
		return getInvokedMethods().get(invoke.getSequenceId());
	}
	
	//==========================================================================
	
	public InvokeResultHandler getInvokeResultHandler() {
		return invokeResultHandler;
	}	

	public void setInvokeResultHandler(InvokeResultHandler invokeResultHandler) {
		this.invokeResultHandler = invokeResultHandler;
	}
	
	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}
	
	public int getPlayStart() {
		return playStart;
	}
	
	public void setPlayStart(int playStart) {
		this.playStart = playStart;
	}
	
	public DecoderOutput getDecoderOutput() {
		return decoderOutput;
	}
	
	public void setDecoderOutput(DecoderOutput decoderOutput) {
		this.decoderOutput = decoderOutput;
	}
	
	public FlvWriter getFlvWriter() {
		return flvWriter;
	}	
	
	public void setFlvWriter(FlvWriter flvWriter) {
		this.flvWriter = flvWriter;
	}
	
	public int getPlayDuration() {
		return playDuration;
	}
	
	public void setPlayDuration(int playDuration) {
		this.playDuration = playDuration;
	}
	
	public String getPlayName() {
		return playName;
	}
	
	public void setPlayName(String playName) {
		this.playName = playName;
	}
	
	public Map<String, Object> getConnectParams() {
		return connectParams;
	}
	
	public void setConnectParams(Map<String, Object> connectParams) {
		this.connectParams = connectParams;
	}
	
	public int incrementBytesRead(int size) {
		bytesRead = bytesRead + size;
		return bytesRead;
	}
	
	public int getBytesRead() {
		return bytesRead;
	}
	
	public int getBytesReadLastSent() {
		return bytesReadLastSent;
	}
	
	public void setBytesReadLastSent(int bytesReadLastSent) {
		this.bytesReadLastSent = bytesReadLastSent;
	}
	
	public Map<Integer, String> getInvokedMethods() {
		return invokedMethods;
	}
	
	public int getNextInvokeId() {
		return ++nextInvokeId;
	}
	
	public boolean isServerHandshakeReceived() {
		return serverHandshakeReceived;
	}
	
	public void setServerHandshakeReceived(boolean serverHandshakeReceived) {
		this.serverHandshakeReceived = serverHandshakeReceived;
	}
	
	public Map<Integer, Header> getPrevHeadersIn() {
		return prevHeadersIn;
	}
	
	public Map<Integer, Header> getPrevHeadersOut() {
		return prevHeadersOut;
	}
	
	public Map<Integer, Packet> getPrevPacketsIn() {
		return prevPacketsIn;
	}
	
	public int getChunkSize() {
		return chunkSize;
	}
	
	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

}
