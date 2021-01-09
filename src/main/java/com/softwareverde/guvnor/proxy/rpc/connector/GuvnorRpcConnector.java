package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnector;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.util.Container;

public interface GuvnorRpcConnector extends BitcoinMiningRpcConnector {
    ChainHeight getChainHeight(Monitor monitor);

    default ChainHeight getChainHeight() {
        return this.getChainHeight(null);
    }

    Boolean isSuccessfulResponse(final Response response, final Container<String> errorStringContainer);
}
