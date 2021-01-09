package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;

public class BitcoinVerdeRpcConnector extends com.softwareverde.bitcoin.rpc.BitcoinVerdeRpcConnector implements GuvnorRpcConnector {
    public BitcoinVerdeRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeRpcAddress) {
        this(bitcoinNodeRpcAddress, null);
    }

    public BitcoinVerdeRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeRpcAddress, final RpcCredentials rpcCredentials) {
        super(bitcoinNodeRpcAddress, rpcCredentials);
    }

    @Override
    public ChainHeight getChainHeight(final Monitor monitor) {
        final String host = _bitcoinNodeRpcAddress.getHost();
        final Integer port = _bitcoinNodeRpcAddress.getPort();

        final Long blockHeight;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            final Json responseJson = nodeJsonRpcConnection.getBlockHeight();
            if (responseJson == null) {
                Logger.warn("Unable to get block height from node.");
                return null;
            }

            blockHeight = responseJson.getLong("blockHeight");
        }

        final ChainWork chainWork;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            final Json responseJson = nodeJsonRpcConnection.getBlockHeader(blockHeight);
            if (responseJson == null) {
                Logger.warn("Unable to get chain work from node.");
                return null;
            }

            final Json blockJson = responseJson.get("block");
            final String chainWorkString = blockJson.getString("chainWork");
            chainWork = ChainWork.fromHexString(chainWorkString);
        }

        return new ChainHeight(blockHeight, chainWork);
    }

    @Override
    public Boolean isSuccessfulResponse(final Response response, final Container<String> errorStringContainer) {
        errorStringContainer.value = null;
        if (response == null) { return false; }

        if (! Util.areEqual(Response.Codes.OK, response.getCode())) {
            return false;
        }

        final Json responseJson;
        {
            final String rawResponse = StringUtil.bytesToString(response.getContent());
            responseJson = Json.parse(rawResponse);
        }

        return responseJson.get("wasSuccess", false);
    }
}
