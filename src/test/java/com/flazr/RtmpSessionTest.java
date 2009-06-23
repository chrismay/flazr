package com.flazr;

import static org.junit.Assert.*;

import org.junit.Test;

public class RtmpSessionTest {
	
	@Test
	public void testParseUrls() {
		RtmpSession session = new RtmpSession("rtmpe://foo:12/bar/baz", "test");
		assertTrue(session.isEncrypted());
	}

}
