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

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.mina.common.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMPE support added based on the public spec 
 * available at http://lkcl.net/rtmp/RTMPE.txt
 * thanks also to the detailed breakdown of the handshake created by the 
 * rtmpd / crtmpserver project [ http://www.rtmpd.com ]
 * and available at http://www.rtmpd.com/export/419/trunk/docs/server.xlsx
 */
public class Handshake {

	private static final Logger logger = LoggerFactory.getLogger(Handshake.class);

	private static final int HANDSHAKE_SIZE = 1536;	

	private static final int SHA256_DIGEST_LENGTH = 32;		

	private static final byte[] SERVER_CONST = "Genuine Adobe Flash Media Server 001".getBytes();

	private static final byte[] CLIENT_CONST = "Genuine Adobe Flash Player 001".getBytes();
	
	private static final byte[] RANDOM_CRUD = Utils.fromHex(
		"F0EEC24A8068BEE82E00D0D1029E7E576EEC5D2D29806FAB93B8E636CFEB31AE"
	);	

	private static final byte[] SERVER_CONST_CRUD = concat(SERVER_CONST, RANDOM_CRUD);

	private static final byte[] CLIENT_CONST_CRUD = concat(CLIENT_CONST, RANDOM_CRUD);

    private static final byte[] DH_MODULUS_BYTES = Utils.fromHex(
    	  "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74"
    	+ "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437"
    	+ "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
    	+ "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381FFFFFFFFFFFFFFFF"
    );

    private static final BigInteger DH_MODULUS = new BigInteger(1, DH_MODULUS_BYTES);

    private static final BigInteger DH_BASE = BigInteger.valueOf(2);    

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] c = new byte[a.length + b.length];
		System.arraycopy(a, 0, c, 0, a.length);
		System.arraycopy(b, 0, c, a.length, b.length);
		return c;
	}

	private static int addBytes(byte[] bytes) {
		if(bytes.length != 4) {
			throw new RuntimeException("unexpected byte array size: " + bytes.length);
		}
		int result = 0;
		for(int i = 0; i < bytes.length; i++) {
			result += bytes[i] & 0xff;
		}
		return result;
	}
	
	private static int calculateOffset(byte[] pointer, int modulus, int increment) {
		int offset = addBytes(pointer);
		offset %= modulus;
		offset += increment;
		return offset;
	}

	private static byte[] getFourBytesFrom(ByteBuffer buf, int offset) {
		int initial = buf.position();
		buf.position(offset);
		byte[] bytes = new byte[4];
		buf.get(bytes);
		buf.position(initial);
		return bytes;
	}

	private static KeyPair generateKeyPair(RtmpSession session) {
		DHParameterSpec keySpec = new DHParameterSpec(DH_MODULUS, DH_BASE);
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DH");
			keyGen.initialize(keySpec);
			KeyPair keyPair = keyGen.generateKeyPair();
		    KeyAgreement keyAgreement = KeyAgreement.getInstance("DH");
		    keyAgreement.init(keyPair.getPrivate());
		    session.setKeyAgreement(keyAgreement);
			return keyPair;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static byte[] getPublicKey(KeyPair keyPair) {
		 DHPublicKey publicKey = (DHPublicKey) keyPair.getPublic();
	     BigInteger	dh_Y = publicKey.getY();	     
	     byte[] result = dh_Y.toByteArray();
	     logger.debug("public key as bytes, len = [" + result.length + "]: " + Utils.toHex(result));
	     byte[] temp = new byte[128];
	     if(result.length < 128) {
	    	 System.arraycopy(result, 0, temp, 128 - result.length, result.length);
	    	 result = temp;
	    	 logger.debug("padded public key length to 128");
	     } else if(result.length > 128){
	    	 System.arraycopy(result, result.length - 128, temp, 0, 128);
	    	 result = temp;
	    	 logger.debug("truncated public key length to 128");
	     }
	     return result;
	}

	private static byte[] getSharedSecret(byte[] otherPublicKeyBytes, KeyAgreement keyAgreement) {
		BigInteger otherPublicKeyInt = new BigInteger(1, otherPublicKeyBytes);
		try {
			KeyFactory keyFactory = KeyFactory.getInstance("DH");
			KeySpec otherPublicKeySpec = new DHPublicKeySpec(otherPublicKeyInt, DH_MODULUS, DH_BASE);
			PublicKey otherPublicKey = keyFactory.generatePublic(otherPublicKeySpec);
		    keyAgreement.doPhase(otherPublicKey, true);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	    byte[] sharedSecret = keyAgreement.generateSecret();
	    logger.debug("shared secret (" + sharedSecret.length + " bytes): " + Utils.toHex(sharedSecret));
	    return sharedSecret;
	}

	private static byte[] sha256(byte[] message, byte[] key) {
        Mac mac;
        try {
			mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
        } catch(Exception e) {
        	throw new RuntimeException(e);
        }
		return mac.doFinal(message);
	}

	private ByteBuffer data;

	public ByteBuffer getData() {
		return data;
	}

	public static Handshake generateClientRequest1(RtmpSession session) {		
		ByteBuffer buf = ByteBuffer.allocate(HANDSHAKE_SIZE);
        Utils.writeInt32Reverse(buf, (int) System.currentTimeMillis() & 0x7FFFFFFF);
        buf.put(new byte[] { 0x09, 0x00, 0x7c, 0x02 }); // flash player version 9.0.124.2
		byte[] randomBytes = new byte[HANDSHAKE_SIZE - 8]; // 4 + 4 bytes [time, version] done already
		Random random = new Random();
		random.nextBytes(randomBytes);
		buf.put(randomBytes);
		buf.flip();
        if(session.isEncrypted()) {
        	logger.info("creating client handshake part 1 for encryption");
	        KeyPair keyPair = generateKeyPair(session);
	        byte[] clientPublicKey = getPublicKey(keyPair);
	        byte[] dhPointer = getFourBytesFrom(buf, HANDSHAKE_SIZE - 4);
	        int dhOffset = calculateOffset(dhPointer, 632, 772);
	        buf.position(dhOffset);
	        buf.put(clientPublicKey);
	        session.setClientPublicKey(clientPublicKey);
	        logger.debug("client public key: " + Utils.toHex(clientPublicKey));

	        byte[] digestPointer = getFourBytesFrom(buf, 8);
	        int digestOffset = calculateOffset(digestPointer, 728, 12);
	        buf.rewind();
	        int messageLength = HANDSHAKE_SIZE - SHA256_DIGEST_LENGTH;
	        byte[] message = new byte[messageLength];
	        buf.get(message, 0, digestOffset);
	        int afterDigestOffset = digestOffset + SHA256_DIGEST_LENGTH;
	        buf.position(afterDigestOffset);
	        buf.get(message, digestOffset, HANDSHAKE_SIZE - afterDigestOffset);
			byte[] digest = sha256(message, CLIENT_CONST);
			buf.position(digestOffset);
			buf.put(digest);
			buf.rewind();
			session.setClientDigest(digest);
        }

        Handshake hs = new Handshake();
        hs.data = ByteBuffer.allocate(HANDSHAKE_SIZE + 1);
		if(session.isEncrypted()) {
			hs.data.put((byte) 0x06);
		} else {
			hs.data.put((byte) 0x03);
		}
		hs.data.put(buf);
		hs.data.flip();
		return hs;
	}

	public boolean decodeServerResponse(ByteBuffer in, RtmpSession session) {
    	if(in.remaining() < 1 + HANDSHAKE_SIZE + HANDSHAKE_SIZE) {
    		return false;
    	}
		byte[] bytes = new byte[1 + HANDSHAKE_SIZE + HANDSHAKE_SIZE];
		in.get(bytes);
		data = ByteBuffer.wrap(bytes);
		
		// TODO validate bytes[0] is 0x03 or 0x06 (encryption)
		
		ByteBuffer buf = ByteBuffer.allocate(HANDSHAKE_SIZE);
		buf.put(bytes, 1, HANDSHAKE_SIZE);
		buf.flip();		
		logger.debug("server response part 1: " + buf);		

		if(session.isEncrypted()) {
			logger.info("processing server response for encryption");			
			// TODO validate time and version ?
			byte[] serverTime = new byte[4];
			buf.get(serverTime);
			logger.debug("server time: " + Utils.toHex(serverTime));

			byte[] serverVersion = new byte[4];
			buf.get(serverVersion);
			logger.debug("server version: " + Utils.toHex(serverVersion));

			byte[] digestPointer = new byte[4]; // position 8
			buf.get(digestPointer);
			int digestOffset = calculateOffset(digestPointer, 728, 12);
	        buf.rewind();

	        int messageLength = HANDSHAKE_SIZE - SHA256_DIGEST_LENGTH;
	        byte[] message = new byte[messageLength];
			buf.get(message, 0, digestOffset);
			int afterDigestOffset = digestOffset + SHA256_DIGEST_LENGTH;
			buf.position(afterDigestOffset);
			buf.get(message, digestOffset, HANDSHAKE_SIZE - afterDigestOffset);
			byte[] digest = sha256(message, SERVER_CONST);
			byte[] serverDigest = new byte[SHA256_DIGEST_LENGTH];
			buf.position(digestOffset);
			buf.get(serverDigest);

			byte[] serverPublicKey = new byte[128];
			if(Arrays.equals(digest, serverDigest)) {
				logger.info("type 1 digest comparison success");
				byte[] dhPointer = getFourBytesFrom(buf, HANDSHAKE_SIZE - 4);
				int dhOffset = calculateOffset(dhPointer, 632, 772);
				buf.position(dhOffset);
				buf.get(serverPublicKey);
				session.setServerDigest(serverDigest);
			} else {
				logger.warn("type 1 digest comparison failed, trying type 2 algorithm");
				digestPointer = getFourBytesFrom(buf, 772);
				digestOffset = calculateOffset(digestPointer, 728, 776);
				message = new byte[messageLength];
				buf.rewind();
				buf.get(message, 0, digestOffset);
				afterDigestOffset = digestOffset + SHA256_DIGEST_LENGTH;
				buf.position(afterDigestOffset);
				buf.get(message, digestOffset, HANDSHAKE_SIZE - afterDigestOffset);
				digest = sha256(message, SERVER_CONST);
				serverDigest = new byte[SHA256_DIGEST_LENGTH];
				buf.position(digestOffset);
				buf.get(serverDigest);
				if(Arrays.equals(digest, serverDigest)) {
					logger.info("type 2 digest comparison success");
					byte[] dhPointer = getFourBytesFrom(buf, 768);
					int dhOffset = calculateOffset(dhPointer, 632, 8);
					buf.position(dhOffset);
					buf.get(serverPublicKey);
					session.setServerDigest(serverDigest);
				} else {
					throw new RuntimeException("type 2 digest comparison failed");
				}
			}
			logger.debug("server public key: " + Utils.toHex(serverPublicKey));			
			byte[] sharedSecret = getSharedSecret(serverPublicKey, session.getKeyAgreement());					

			byte[] digestOut = sha256(serverPublicKey, sharedSecret);
			try {
				Cipher cipherOut = Cipher.getInstance("RC4");
				cipherOut.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(digestOut, 0, 16, "RC4"));
				session.setCipherOut(cipherOut);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}

			byte[] digestIn = sha256(session.getClientPublicKey(), sharedSecret);
			try {
				Cipher cipherIn = Cipher.getInstance("RC4");
				cipherIn.init(Cipher.DECRYPT_MODE, new SecretKeySpec(digestIn, 0, 16, "RC4"));
				session.setCipherIn(cipherIn);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		ByteBuffer partTwo = ByteBuffer.allocate(HANDSHAKE_SIZE);
		partTwo.put(bytes, 1 + HANDSHAKE_SIZE, HANDSHAKE_SIZE);
		partTwo.flip();	
		
		logger.debug("server response part 2: " + partTwo);
		
		// validate server response part 2, not really required for client, but just to show off ;)
		if(session.isEncrypted()) {
			byte[] firstFourBytes = getFourBytesFrom(partTwo, 0);			
			if(Arrays.equals(new byte[]{0, 0, 0, 0}, firstFourBytes)) {
				logger.warn("server response part 2 first four bytes are zero, did handshake fail ?");
			}			
			byte[] message = new byte[HANDSHAKE_SIZE - SHA256_DIGEST_LENGTH];
			partTwo.get(message);
			byte[] digest = sha256(session.getClientDigest(), SERVER_CONST_CRUD);
			byte[] signature = sha256(message, digest);
			byte[] serverSignature = new byte[SHA256_DIGEST_LENGTH];			
			partTwo.get(serverSignature);
			if(Arrays.equals(signature, serverSignature)) {
				logger.info("server response part 2 validation success, is Flash Player v9 handshake");
			} else {
				logger.warn("server response part 2 validation failed, not Flash Player v9 handshake");
			}			
		} else {
			// TODO validate if server echoed client request 1			
		}

		// swf verification
		if(session.getSwfHash() != null) {
			byte[] bytesFromServer = new byte[SHA256_DIGEST_LENGTH];
			buf.position(HANDSHAKE_SIZE - SHA256_DIGEST_LENGTH);
			buf.get(bytesFromServer);
			byte[] bytesFromServerHash = sha256(session.getSwfHash().getBytes(), bytesFromServer);
			// construct SWF verification pong payload
			ByteBuffer swfv = ByteBuffer.allocate(42);
			swfv.put((byte) 0x01);
			swfv.put((byte) 0x01);
			swfv.putInt(session.getSwfSize());
			swfv.putInt(session.getSwfSize());
			swfv.put(bytesFromServerHash);
			byte[] swfvBytes = new byte[42];
			swfv.flip();
			swfv.get(swfvBytes);
			session.setSwfVerification(swfvBytes);
			logger.info("initialized swf verification response from swfSize = "
					+ session.getSwfSize() + " & swfHash = '"
					+ session.getSwfHash() + "': " + Utils.toHex(swfvBytes));
		}

		return true;
	}

	public Handshake generateClientRequest2(RtmpSession session) {
		// TODO validate serverResponsePart2
		if(session.isEncrypted()) { // encryption
			logger.info("creating client handshake part 2 for encryption");
			byte[] randomBytes = new byte[HANDSHAKE_SIZE];
			Random random = new Random();
			random.nextBytes(randomBytes);
			ByteBuffer buf = ByteBuffer.wrap(randomBytes);
			byte[] digest = sha256(session.getServerDigest(), CLIENT_CONST_CRUD);
			byte[] message = new byte[HANDSHAKE_SIZE - SHA256_DIGEST_LENGTH];
			buf.rewind();
			buf.get(message);
			byte[] signature = sha256(message, digest);
			buf.put(signature);
			buf.rewind();

			// update 'encoder / decoder state' for the RC4 keys
			// both parties *pretend* as if handshake part 2 (1536 bytes) was encrypted
			// effectively this hides / discards the first few bytes of encrypted session
			// which is known to increase the secure-ness of RC4
			// RC4 state is just a function of number of bytes processed so far
			// that's why we just run 1536 arbitrary bytes through the keys below
			byte[] dummyBytes = new byte[HANDSHAKE_SIZE];
			session.getCipherIn().update(dummyBytes);
			session.getCipherOut().update(dummyBytes);

			Handshake hs = new Handshake();
			hs.data = buf;
			return hs;
		} else {
			data.get(); // skip first byte
			byte[] bytes = new byte[HANDSHAKE_SIZE];
			data.get(bytes); // copy first half of server response
			Handshake hs = new Handshake();
			hs.data = ByteBuffer.wrap(bytes);
			return hs;
		}
	}

}
