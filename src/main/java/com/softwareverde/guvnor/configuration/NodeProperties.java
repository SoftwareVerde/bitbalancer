package com.softwareverde.guvnor.configuration;

public class NodeProperties {
    protected final String _host;
    protected final Integer _port;
    protected final Boolean _isSecure;
    protected final String _rpcUsername;
    protected final String _rpcPassword;

    public NodeProperties(final String host, final Integer port, final Boolean isSecure, final String rpcUsername, final String rpcPassword) {
        _host = host;
        _port = port;
        _isSecure = isSecure;
        _rpcUsername = rpcUsername;
        _rpcPassword = rpcPassword;
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
}
