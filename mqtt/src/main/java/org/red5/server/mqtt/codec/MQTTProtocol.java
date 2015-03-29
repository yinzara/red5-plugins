/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.red5.server.mqtt.codec;

import java.io.UnsupportedEncodingException;

import org.apache.mina.core.buffer.IoBuffer;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.red5.server.mqtt.codec.exception.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Protocol data utilities.
 *
 * @author andrea
 * @author Paul Gregoire
 */
public class MQTTProtocol {

	private static final Logger log = LoggerFactory.getLogger(MQTTProtocol.class);
	
	public static final int MAX_LENGTH_LIMIT = 268435455;

	public static final byte VERSION_3_1 = 3;

	public static final byte VERSION_3_1_1 = 4;
	
	public static byte readMessageType(IoBuffer in) {
		byte h1 = in.get();
		byte messageType = (byte) ((h1 & 0x00F0) >> 4);
		return messageType;
	}

	public static boolean checkHeaderAvailability(IoBuffer in) {
		if (in.remaining() < 1) {
			return false;
		}
		//byte h1 = in.get();
		//byte messageType = (byte) ((h1 & 0x00F0) >> 4);
		in.skip(1); //skip the messageType byte
		int remainingLength = decodeRemainingLenght(in);
		if (remainingLength == -1) {
			return false;
		}
		//check remaining length
		if (in.remaining() < remainingLength) {
			return false;
		}
		//return messageType == type ? MessageDecoderResult.OK : MessageDecoderResult.NOT_OK;
		return true;
	}

	/**
	 * Decode the variable remaining length as defined in MQTT v3.1 specification (section 2.1).
	 *  
	 * @return the decoded length or -1 if needed more data to decode the length field.
	 */
	public static int decodeRemainingLenght(IoBuffer in) {
		int multiplier = 1;
		int value = 0;
		byte digit;
		do {
			if (in.remaining() < 1) {
				return -1;
			}
			digit = in.get();
			value += (digit & 0x7F) * multiplier;
			multiplier *= 128;
		} while ((digit & 0x80) != 0);
		return value;
	}

	/**
	 * Encode the value in the format defined in specification as variable length array.
	 * 
	 * @throws IllegalArgumentException
	 *             if the value is not in the specification bounds [0..268435455].
	 */
	public static byte[] encodeRemainingLength(int value) throws CorruptedFrameException {
		if (value > MAX_LENGTH_LIMIT || value < 0) {
			throw new CorruptedFrameException("Value should in range 0.." + MAX_LENGTH_LIMIT + " found " + value);
		}
		byte[] encoded = new byte[4];
		byte digit;
		int i = 0;
		do {
			digit = (byte) (value % 128);
			value = value / 128;
			// if there are more digits to encode, set the top bit of this digit
			if (value > 0) {
				digit = (byte) (digit | 0x80);
			}
			encoded[i++] = digit;
		} while (value > 0);
		return encoded;
	}

	/**
	 * Load a string from the given buffer, reading first the two bytes of len and then the UTF-8 bytes of the string.
	 * 
	 * @return the decoded string or null if NEED_DATA
	 */
	public static String decodeString(IoBuffer in) throws UnsupportedEncodingException {
		if (in.remaining() < 2) {
			return null;
		}
		//int strLen = Utils.readWord(in);
		int strLen = in.getUnsignedShort();
		if (in.remaining() < strLen) {
			return null;
		}
		byte[] strRaw = new byte[strLen];
		in.get(strRaw);
		return new String(strRaw, "UTF-8");
	}

	/**
	 * Return the bytes with string encoded as MSB, LSB and UTF-8 encoded string content.
	 */
	public static byte[] encodeString(String str) {
		IoBuffer out = IoBuffer.allocate(2).setAutoExpand(true);
		try {
			byte[] raw = str.getBytes("UTF-8");
			//Utils.writeWord(out, raw.length);
			out.putShort((short) raw.length);
			out.put(raw);
			out.flip();
		} catch (UnsupportedEncodingException ex) {
			log.error(null, ex);
			return null;
		}
		return out.array();
	}

	/**
	 * Return the number of bytes to encode the given remaining length value
	 */
	public static int numBytesToEncode(int len) {
		if (0 <= len && len <= 127)
			return 1;
		if (128 <= len && len <= 16383)
			return 2;
		if (16384 <= len && len <= 2097151)
			return 3;
		if (2097152 <= len && len <= 268435455)
			return 4;
		throw new IllegalArgumentException("value should be in the range [0..268435455]");
	}

	public static byte encodeFlags(AbstractMessage message) {
		byte flags = 0;
		if (message.isDupFlag()) {
			flags |= 0x08;
		}
		if (message.isRetainFlag()) {
			flags |= 0x01;
		}
		flags |= ((message.getQos().ordinal() & 0x03) << 1);
		return flags;
	}

}