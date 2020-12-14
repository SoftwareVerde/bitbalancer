package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.list.List;

public interface ZmqConfiguration {
    Integer getPort(NotificationType zmqNotificationType);
    List<NotificationType> getSupportedMessageTypes();
}
