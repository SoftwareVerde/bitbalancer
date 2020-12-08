package com.softwareverde.guvnor.proxy.rpc;

import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;

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
}
