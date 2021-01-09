package com.softwareverde.guvnor.configuration;

import com.softwareverde.bitcoin.rpc.RpcNotificationType;

import java.util.HashMap;
import java.util.Map;

public class NodeProperties {
    protected final String _name;
    protected final String _host;
    protected final Integer _port;
    protected final Boolean _isSecure;
    protected final String _rpcUsername;
    protected final String _rpcPassword;
    protected final Map<RpcNotificationType, Integer> _zmqPorts;
    protected final String _connectorIdentifier;
    protected final Long _maxTimeoutMs;

    public NodeProperties(final String name, final String host, final Integer port, final Boolean isSecure, final String rpcUsername, final String rpcPassword, final Map<RpcNotificationType, Integer> zmqPorts, final String connectorIdentifier, final Long maxTimeoutMs) {
        _name = name;
        _host = host;
        _port = port;
        _isSecure = isSecure;
        _rpcUsername = rpcUsername;
        _rpcPassword = rpcPassword;
        _maxTimeoutMs = maxTimeoutMs;

        if ( (zmqPorts != null) && (! zmqPorts.isEmpty()) ) {
            _zmqPorts = new HashMap<>(0);
            _zmqPorts.putAll(zmqPorts);
        }
        else {
            _zmqPorts = null;
        }

        _connectorIdentifier = connectorIdentifier;
    }

    public String getName() { return _name; }

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

    public Map<RpcNotificationType, Integer> getZmqPorts() {
        return _zmqPorts;
    }

    public String getConnectorIdentifier() {
        return _connectorIdentifier;
    }

    public Long getMaxTimeoutMs() {
        return _maxTimeoutMs;
    }
}
