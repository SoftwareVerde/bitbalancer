package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.guvnor.proxy.rpc.connector.MutableRequest;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcProxyServer {
    protected static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger(1);
    protected static final ChainHeight UNKNOWN_CHAIN_HEIGHT = new ChainHeight(0L, null);

    protected final Integer _port;
    protected final HttpServer _httpServer;
    protected final HashMap<RpcConfiguration, ChainHeight> _rpcConfigurations = new HashMap<>();

    protected ChainHeight _getChainHeight(final BitcoinRpcConnector bitcoinRpcConnector) {
        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", NEXT_REQUEST_ID.getAndIncrement());
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
            final Response response = bitcoinRpcConnector.handleRequest(request);
            final Json responseJson = Json.parse(StringUtil.bytesToString(response.getContent()));

            final String errorString = responseJson.getString("error");
            if (! Util.isBlank(errorString)) {
                Logger.debug(errorString);
                return null;
            }

            resultJson = responseJson.get("result");
        }

        final Long blockHeight = resultJson.getLong("blocks");
        final ChainWork chainWork = ChainWork.fromHexString(resultJson.getString("chainwork"));

        return new ChainHeight(blockHeight, chainWork);
    }

    public RpcProxyServer(final Integer port, final List<RpcConfiguration> rpcConfigurations) {
        _port = port;
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            _rpcConfigurations.put(rpcConfiguration, UNKNOWN_CHAIN_HEIGHT);
        }

        final Endpoint endpoint;
        {
            final NodeSelector nodeSelector = new NodeSelector() {
                @Override
                public RpcConfiguration selectBestNode() {
                    int bestHierarchy = Integer.MIN_VALUE;
                    ChainHeight bestChainHeight = UNKNOWN_CHAIN_HEIGHT;
                    RpcConfiguration bestRpcConfiguration = null;

                    for (final RpcConfiguration rpcConfiguration : _rpcConfigurations.keySet()) {
                        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                        final ChainHeight chainHeight = _getChainHeight(bitcoinRpcConnector);
                        if (chainHeight == null) { continue; }

                        // Update the node's ChainWork...
                        _rpcConfigurations.put(rpcConfiguration, chainHeight);

                        final int compareValue = chainHeight.compareTo(bestChainHeight);
                        if (compareValue < 0) { continue; }

                        final int hierarchy = rpcConfiguration.getHierarchy();
                        if ( (compareValue > 0) || (hierarchy <= bestHierarchy) ) {
                            bestChainHeight = chainHeight;
                            bestHierarchy = hierarchy;
                            bestRpcConfiguration = rpcConfiguration;
                        }
                    }

                    Logger.debug("Selected: " + (bestRpcConfiguration != null ? (bestRpcConfiguration.getHost() + ":" + bestRpcConfiguration.getPort()) : null) + " " + bestChainHeight);
                    return bestRpcConfiguration;
                }
            };

            endpoint = new Endpoint(
                new RpcProxyHandler(nodeSelector)
            );
            endpoint.setPath("/");
            endpoint.setStrictPathEnabled(true);
        }

        _httpServer = new HttpServer();
        _httpServer.setPort(_port);
        _httpServer.enableEncryption(false);
        _httpServer.redirectToTls(false);
        _httpServer.addEndpoint(endpoint);
    }

    public void start() {
        _httpServer.start();
    }

    public void stop() {
        _httpServer.stop();
    }

    public Integer getPort() {
        return _port;
    }
}
