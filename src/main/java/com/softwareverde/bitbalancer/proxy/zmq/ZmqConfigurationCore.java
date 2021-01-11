package com.softwareverde.bitbalancer.proxy.zmq;

import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

import java.util.HashMap;

public class ZmqConfigurationCore implements ZmqConfiguration {
    protected final HashMap<RpcNotificationType, Integer> _ports = new HashMap<>();

    public ZmqConfigurationCore() { }

    public void setPort(final RpcNotificationType notificationType, final Integer port) {
        _ports.put(notificationType, port);
    }

    @Override
    public Integer getPort(final RpcNotificationType notificationType) {
        return _ports.get(notificationType);
    }

    @Override
    public List<RpcNotificationType> getSupportedMessageTypes() {
        return new MutableList<>(_ports.keySet());
    }
}
