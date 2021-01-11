package com.softwareverde.bitbalancer.proxy.rpc.connector;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.core.MutableRequest;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;
import com.softwareverde.bitbalancer.proxy.rpc.ChainHeight;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

public class BitcoinCoreRpcConnector extends com.softwareverde.bitcoin.rpc.core.BitcoinCoreRpcConnector implements BitBalancerRpcConnector {

    public BitcoinCoreRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeAddress) {
        this(bitcoinNodeAddress, null);
    }

    public BitcoinCoreRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
        super(bitcoinNodeAddress, rpcCredentials);
    }

    @Override
    public ChainHeight getChainHeight(final Monitor monitor) {
        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", _nextRequestId.getAndIncrement());
            json.put("method", "getblockchaininfo");

            { // Method Parameters
                final Json paramsJson = new Json(true);
                json.put("params", paramsJson);
            }

            requestPayload = StringUtil.stringToBytes(json.toString());
        }

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);
        // request.setHeader("host", "127.0.0.1");
        // request.setHeader("content-length", String.valueOf(requestPayload.length));
        // request.setHeader("connection", "close");

        final Json resultJson;
        {
            final Response response = this.handleRequest(request, monitor);
            final String rawResponse = StringUtil.bytesToString(response.getContent());
            if (! Json.isJson(rawResponse)) {
                Logger.debug("Received error from " + _toString() +": " + rawResponse.replaceAll("[\\n\\r]+", "/"));
                return null;
            }
            final Json responseJson = Json.parse(rawResponse);

            final String errorString = responseJson.getString("error");
            if (! Util.isBlank(errorString)) {
                Logger.debug("Received error from " + _toString() + ": " + errorString);
                return null;
            }

            resultJson = responseJson.get("result");
        }

        final Long blockHeight = resultJson.getLong("blocks");
        final ChainWork chainWork = ChainWork.fromHexString(resultJson.getString("chainwork"));

        return new ChainHeight(blockHeight, chainWork);
    }

    @Override
    public Boolean isSuccessfulResponse(final Response response, final Container<String> errorStringContainer) {
        return com.softwareverde.bitcoin.rpc.core.BitcoinCoreRpcConnector.isSuccessfulResponse(response, null, errorStringContainer);
    }
}
