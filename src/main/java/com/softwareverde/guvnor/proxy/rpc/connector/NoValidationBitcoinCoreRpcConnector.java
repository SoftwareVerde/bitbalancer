package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.guvnor.proxy.rpc.RpcCredentials;

public class NoValidationBitcoinCoreRpcConnector extends BitcoinCoreRpcConnector {
    public static final String IDENTIFIER = "NO_VALIDATE";

    public NoValidationBitcoinCoreRpcConnector(final BitcoinNodeAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
        super(bitcoinNodeAddress, rpcCredentials);
    }

    @Override
    public Boolean validateBlockTemplate(final BlockTemplate blockTemplate, final Monitor monitor) {
        if (monitor instanceof BitcoinCoreRpcMonitor) {
            ((BitcoinCoreRpcMonitor) monitor).beforeRequestStart(null);
            ((BitcoinCoreRpcMonitor) monitor).afterRequestEnd();
        }

        return null;
    }
}
