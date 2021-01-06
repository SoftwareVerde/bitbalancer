package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.Util;

public class Notification {
    public final NotificationType notificationType;
    public final ByteArray payload;

    public Notification(final NotificationType notificationType, final ByteArray payload) {
        this.notificationType = notificationType;
        this.payload = payload;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof Notification)) { return false; }

        final Notification notification = (Notification) object;
        if (! Util.areEqual(this.notificationType, notification.notificationType)) { return false; }
        if (! Util.areEqual(this.payload, notification.payload)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        final int notificationTypeHashCode = (this.notificationType == null ? 0 : this.notificationType.hashCode());
        final int payloadHashCode = (this.payload == null ? 0 : this.payload.hashCode());
        return (notificationTypeHashCode + payloadHashCode);
    }
}
