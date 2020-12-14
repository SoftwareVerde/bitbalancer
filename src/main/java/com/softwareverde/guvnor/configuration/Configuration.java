package com.softwareverde.guvnor.configuration;

import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.NotificationType;

import java.util.HashMap;
import java.util.Map;

public class Configuration {
    protected final Integer _rpcPort;
    protected final List<NodeProperties> _nodeProperties;
    protected final HashMap<NotificationType, Integer> _zmqPorts = new HashMap<>();

    protected Configuration(final Integer rpcPort, final List<NodeProperties> nodeProperties, final Map<NotificationType, Integer> zmqPorts) {
        _rpcPort = rpcPort;
        _nodeProperties = nodeProperties;
        if (zmqPorts != null) {
            _zmqPorts.putAll(zmqPorts);
        }
    }

    public Integer getRpcPort() {
        return _rpcPort;
    }

    public List<NodeProperties> getNodeProperties() {
        return _nodeProperties;
    }

    public Integer getZmqPort(final NotificationType notificationType) {
        return _zmqPorts.get(notificationType);
    }
}
