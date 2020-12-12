package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.bytearray.ByteArray;

public class Notification {
    public static class MessageType {
        public static final String BLOCK_HASH = "pubhashblock";
        public static final String TRANSACTION_HASH = "pubhashtx";
        public static final String BLOCK = "pubrawblock";
        public static final String TRANSACTION = "pubrawtx";
    }

    public final String messageType;
    public final ByteArray payload;

    public Notification(final String messageType, final ByteArray payload) {
        this.messageType = messageType;
        this.payload = payload;
    }
}
