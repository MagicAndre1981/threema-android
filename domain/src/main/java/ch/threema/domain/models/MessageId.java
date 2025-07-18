/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Wrapper class for message IDs (consisting of 8 bytes, chosen by the sender and not guaranteed
 * to be unique across multiple senders).
 */
public class MessageId implements Serializable {

    private final byte[] messageId;

    /**
     * Create a MessageId from a String
     *
     * @throws ThreemaException If the message id is {@code null} or has an invalid length
     */
    public static MessageId fromString(@Nullable String messageId) throws ThreemaException {
        if (messageId == null) {
            throw new ThreemaException("Message id is null");
        }
        try {
            return new MessageId(Utils.hexStringToByteArray(messageId));
        } catch (BadMessageIdException e) {
            throw new ThreemaException("Message id is invalid", e);
        }
    }

    /**
     * Create a new random MessageId.
     */
    @NonNull
    public static MessageId random() {
        var messageId = new byte[ProtocolDefines.MESSAGE_ID_LEN];
        new SecureRandom().nextBytes(messageId);
        return new MessageId(messageId);
    }

    /**
     * Create a MessageId from an 8-byte array.
     *
     * @throws BadMessageIdException If the source array has the wrong length.
     */
    public MessageId(@NonNull byte[] messageId) throws BadMessageIdException {
        if (messageId.length != ProtocolDefines.MESSAGE_ID_LEN) {
            throw new BadMessageIdException();
        }
        this.messageId = messageId;
    }

    /**
     * Create a MessageId from an array, starting at the specified offset.
     */
    public MessageId(byte[] data, int offset) {
        messageId = new byte[ProtocolDefines.MESSAGE_ID_LEN];
        System.arraycopy(data, offset, messageId, 0, ProtocolDefines.MESSAGE_ID_LEN);
    }

    /**
     * Create a MessageId from an (unsigned) long in little-endian format
     */
    public MessageId(long messageIdLong) {
        messageId = ByteBuffer.allocate(ProtocolDefines.MESSAGE_ID_LEN).order(ByteOrder.LITTLE_ENDIAN).putLong(messageIdLong).array();
    }

    public byte[] getMessageId() {
        return messageId;
    }

    public long getMessageIdLong() {
        return ByteBuffer.wrap(messageId).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    @Override
    public String toString() {
        return Utils.byteArrayToHexString(messageId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (obj.getClass() != getClass())
            return false;

        return Arrays.equals(messageId, ((MessageId) obj).messageId);
    }

    @Override
    public int hashCode() {
        /* message IDs are usually random, so just taking the first four bytes is fine */
        return messageId[0] << 24 | (messageId[1] & 0xFF) << 16 | (messageId[2] & 0xFF) << 8 | (messageId[3] & 0xFF);
    }

    public static class BadMessageIdException extends IllegalArgumentException {}
}
