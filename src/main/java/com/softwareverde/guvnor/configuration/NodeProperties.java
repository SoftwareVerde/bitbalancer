package com.softwareverde.guvnor.configuration;

import com.softwareverde.guvnor.proxy.NotificationType;

import java.util.HashMap;
import java.util.Map;

public class NodeProperties {
    protected final String _host;
    protected final Integer _port;
    protected final Boolean _isSecure;
    protected final String _rpcUsername;
    protected final String _rpcPassword;
    protected final Map<NotificationType, Integer> _zmqPorts;

    public NodeProperties(final String host, final Integer port, final Boolean isSecure, final String rpcUsername, final String rpcPassword, final Map<NotificationType, Integer> zmqPorts) {
        _host = host;
        _port = port;
        _isSecure = isSecure;
        _rpcUsername = rpcUsername;
        _rpcPassword = rpcPassword;

        if ( (zmqPorts != null) && (! zmqPorts.isEmpty()) ) {
            _zmqPorts = new HashMap<>(0);
            _zmqPorts.putAll(zmqPorts);
        }
        else {
            _zmqPorts = null;
        }
    }

    public String getHost() {
        return _host;
    }

    public Integer getPort() {
        return _port;
    }

    public Boolean isSecure() {
        return _isSecure;
    }

    public String getRpcUsername() {
        return _rpcUsername;
    }

    public String getRpcPassword() {
        return _rpcPassword;
    }

    public Map<NotificationType, Integer> getZmqPorts() {
        return _zmqPorts;
    }
}
