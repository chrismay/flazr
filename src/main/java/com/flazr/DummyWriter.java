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

public class DummyWriter implements OutputWriter {		
	
	private WriterStatus status;
	
	public DummyWriter(int seekTime) {
		status = new WriterStatus(seekTime);
	}

	public void close() {
		status.logFinalVideoDuration();
	}

	public synchronized void write(Packet packet) {
		status.getChannelAbsoluteTime(packet.getHeader());
	}

	public synchronized void writeFlvData(ByteBuffer data) {
		while(data.hasRemaining()) {			
			data.get(); // packet type											
			int size = Utils.readInt24(data);			
			int timestamp = Utils.readInt24(data);
			status.updateVideoChannelTime(timestamp);			
			data.position(data.position() + 4 + size + 4);
			// (4) zeros / reserved
			// (size) data
			// (4) tag size
		}				
	}		

}
