package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.http.HttpRequest;

public class BitcoinCoreRpcMonitor extends RpcMonitor<HttpRequest> {

    @Override
    protected void _cancelRequest() {
        _connection.cancel();
    }

    public BitcoinCoreRpcMonitor() { }
}
