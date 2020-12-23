package com.softwareverde.guvnor.proxy.rpc;

import com.softwareverde.guvnor.proxy.Notification;

public interface NotificationCallback {
    void onNewNotification(Notification notification);
}