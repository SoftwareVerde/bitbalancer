package com.softwareverde.bitbalancer.proxy.rpc.connector;

import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;

public class BchdRpcConnector extends BitcoinCoreRpcConnector {
    public static final String IDENTIFIER = "BCHD";

    public BchdRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
        super(bitcoinNodeAddress, rpcCredentials);
    }

    @Override
    public Boolean validateBlockTemplate(final BlockTemplate blockTemplate, final Monitor monitor) {
        return null;
    }
}
