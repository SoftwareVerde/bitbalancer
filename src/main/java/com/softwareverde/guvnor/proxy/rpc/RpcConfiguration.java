package com.softwareverde.guvnor.proxy.rpc;

import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.util.Util;

public class RpcConfiguration {
    protected final BitcoinRpcConnector _bitcoinRpcConnector;
    protected final Integer _hierarchy;

    public RpcConfiguration(final BitcoinRpcConnector bitcoinRpcConnector) {
        _bitcoinRpcConnector = bitcoinRpcConnector;
        _hierarchy = null;
    }

    public RpcConfiguration(final BitcoinRpcConnector bitcoinRpcConnector, final Integer preferenceOrder) {
        _bitcoinRpcConnector = bitcoinRpcConnector;
        _hierarchy = preferenceOrder;
    }

    public BitcoinRpcConnector getBitcoinRpcConnector() {
        return _bitcoinRpcConnector;
    }

    public Integer getHierarchy() {
        return _hierarchy;
    }

    public String getHost() {
        return _bitcoinRpcConnector.getHost();
    }

    public Integer getPort() {
        return _bitcoinRpcConnector.getPort();
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof RpcConfiguration)) { return false; }

        final String host = _bitcoinRpcConnector.getHost();
        final Integer port = _bitcoinRpcConnector.getPort();

        final RpcConfiguration rpcConfiguration = (RpcConfiguration) object;

        if (! Util.areEqual(_hierarchy, rpcConfiguration.getHierarchy())) { return false; }
        if (! Util.areEqual(host, rpcConfiguration.getHost())) { return false; }
        if (! Util.areEqual(port, rpcConfiguration.getPort())) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        final String host = _bitcoinRpcConnector.getHost();
        final Integer port = _bitcoinRpcConnector.getPort();

        return (Util.coalesce(_hierarchy).hashCode() + host.hashCode() + port.hashCode());
    }
}
