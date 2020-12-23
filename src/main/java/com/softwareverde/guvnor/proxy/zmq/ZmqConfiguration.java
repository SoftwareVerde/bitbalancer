package com.softwareverde.guvnor.proxy.zmq;

import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.NotificationType;

public interface ZmqConfiguration {
    Integer getPort(NotificationType zmqNotificationType);
    List<NotificationType> getSupportedMessageTypes();
}
