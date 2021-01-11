package com.softwareverde.bitbalancer.proxy.rpc.connector;

import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;

public class NoValidationBitcoinCoreRpcConnector extends BitcoinCoreRpcConnector {
    public static final String IDENTIFIER = "NO_VALIDATE";

    public NoValidationBitcoinCoreRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
        super(bitcoinNodeAddress, rpcCredentials);
    }

    @Override
    public Boolean validateBlockTemplate(final BlockTemplate blockTemplate, final Monitor monitor) {
        return null;
    }
}
