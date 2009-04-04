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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultInvokeResultHandler implements InvokeResultHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(InvokeResultHandler.class);
	
	public void handle(Invoke invoke, RtmpSession session) {		
		String resultFor = session.getInvokedMethods().get(invoke.getSequenceId());
		logger.info("result for method call: " + resultFor);		
		if(resultFor.equals("connect")) {										
			session.send(Packet.serverBw(0x001312d0)); // hard coded for now	
			session.send(Packet.ping(3, 0, 300)); 
			session.send(new Invoke("createStream", 3));												
		} else if(resultFor.equals("createStream")) {
			int streamId = invoke.getLastArgAsInt();
			logger.info("value of streamId to play: " + streamId);			
			Invoke play = new Invoke(streamId, "play", 8, null, 
					session.getPlayName(), session.getPlayStart(), session.getPlayDuration());			
			session.send(play);		
		} else {
			logger.warn("un-handled server result for: " + resultFor);
		}						
	}	

}
