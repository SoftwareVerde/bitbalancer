package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

import java.util.HashMap;

public class ZmqConfigurationCore implements ZmqConfiguration {
    protected final HashMap<NotificationType, Integer> _ports = new HashMap<>();

    public ZmqConfigurationCore() { }

    public void setPort(final NotificationType notificationType, final Integer port) {
        _ports.put(notificationType, port);
    }

    @Override
    public Integer getPort(final NotificationType notificationType) {
        return _ports.get(notificationType);
    }

    @Override
    public List<NotificationType> getSupportedMessageTypes() {
        return new MutableList<>(_ports.keySet());
    }
}
