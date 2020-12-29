package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;

public class BitcoinVerdeRpcMonitor extends RpcMonitor<NodeJsonRpcConnection> {

    @Override
    protected void _cancelRequest() {
        _connection.close();
    }

    public BitcoinVerdeRpcMonitor() { }
}
