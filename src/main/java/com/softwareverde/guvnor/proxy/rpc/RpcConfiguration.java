package com.softwareverde.guvnor.proxy.rpc;

import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;

public class RpcConfiguration {
    protected final BitcoinNodeAddress _bitcoinNodeAddress;
    protected final BitcoinRpcConnector _bitcoinRpcConnector;

    public RpcConfiguration(final BitcoinNodeAddress bitcoinNodeAddress, final BitcoinRpcConnector bitcoinRpcConnector) {
        _bitcoinNodeAddress = bitcoinNodeAddress;
        _bitcoinRpcConnector = bitcoinRpcConnector;
    }

    public BitcoinNodeAddress getBitcoinNodeAddress() {
        return _bitcoinNodeAddress;
    }

    public BitcoinRpcConnector getBitcoinRpcConnector() {
        return _bitcoinRpcConnector;
    }
}
