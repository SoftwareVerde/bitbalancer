package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcProxyServer {
    protected static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger(1);
    protected static final ChainHeight UNKNOWN_CHAIN_HEIGHT = new ChainHeight(0L, null);

    protected final Integer _port;
    protected final HttpServer _httpServer;
    protected final HashMap<RpcConfiguration, ChainHeight> _rpcConfigurations = new HashMap<>();

    protected final ConcurrentHashMap<RpcConfiguration, List<ZmqNotificationThread>> _zmqNotificationThreads = new ConcurrentHashMap<>();

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

    protected Map<String, String> _getZmqEndpoints(final BitcoinRpcConnector bitcoinRpcConnector) {
        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", NEXT_REQUEST_ID.getAndIncrement());
            json.put("method", "getzmqnotifications");

            { // Method Parameters
                final Json paramsJson = new Json(true);
                json.put("params", paramsJson);
            }

            requestPayload = StringUtil.stringToBytes(json.toString());
        }

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);

        final HashMap<String, String> zmqEndpoints = new HashMap<>();
        final Json resultJson;
        {
            final Response response = bitcoinRpcConnector.handleRequest(request);
            final Json responseJson = Json.parse(StringUtil.bytesToString(response.getContent()));

            final String errorString = responseJson.getString("error");
            if (! Util.isBlank(errorString)) {
                Logger.debug(errorString);
                return zmqEndpoints;
            }

            resultJson = responseJson;
        }


        for (int i = 0; i < resultJson.length(); ++i) {
            final String messageType = resultJson.getString("type");
            final String address = resultJson.getString("address");
            final Integer port;
            {
                final int colonIndex = address.lastIndexOf(':');
                if (colonIndex < 0) { continue; }

                final int portBeginIndex = (colonIndex + 1);
                if (portBeginIndex >= address.length()) { continue; }

                port = Util.parseInt(address.substring(portBeginIndex));
            }

            final String endpointUri = ("tcp://" + bitcoinRpcConnector.getHost() + ":" + port);
            zmqEndpoints.put(messageType, endpointUri);
        }

        return zmqEndpoints;
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final String requiredZmqNotificationType) {
        int bestHierarchy = Integer.MIN_VALUE;
        ChainHeight bestChainHeight = UNKNOWN_CHAIN_HEIGHT;
        RpcConfiguration bestRpcConfiguration = null;

        for (final RpcConfiguration rpcConfiguration : _rpcConfigurations.keySet()) {
            final ChainHeight chainHeight = _rpcConfigurations.get(rpcConfiguration);

            if (requiredZmqNotificationType != null) {
                boolean hasNotificationType = false;
                final List<ZmqNotificationThread> zmqNotifications = _zmqNotificationThreads.get(rpcConfiguration);
                for (final ZmqNotificationThread zmqNotificationThread : zmqNotifications) {
                    if (Util.areEqual(requiredZmqNotificationType, zmqNotificationThread.getMessageType())) {
                        hasNotificationType = true;
                        break;
                    }
                }

                if (! hasNotificationType) { continue; }
            }

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

        return bestRpcConfiguration;
    }

    protected void _relayNotification(final Notification notification) {
        // TODO
    }

    protected void _subscribeToNotifications(final RpcConfiguration rpcConfiguration) {
        {
            final List<ZmqNotificationThread> zmqNotificationThread = _zmqNotificationThreads.get(rpcConfiguration);
            if (zmqNotificationThread != null) { return; }
        }

        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
        final Map<String, String> zmqEndpoints = _getZmqEndpoints(bitcoinRpcConnector);

        final Boolean hasBlockEndpoint = zmqEndpoints.containsKey(Notification.MessageType.BLOCK);
        final Boolean hasBlockHashEndpoint = zmqEndpoints.containsKey(Notification.MessageType.BLOCK_HASH);

        final ZmqNotificationThread.Callback callback = new ZmqNotificationThread.Callback() {
            @Override
            public void onNewNotification(final Notification notification) {
                boolean shouldUpdateChainHeight = false;
                if (hasBlockHashEndpoint) {
                    if (Util.areEqual(Notification.MessageType.BLOCK_HASH, notification.messageType)) {
                        shouldUpdateChainHeight = true;
                    }
                }
                else if (hasBlockEndpoint) {
                    if (Util.areEqual(Notification.MessageType.BLOCK, notification.messageType)) {
                        shouldUpdateChainHeight = true;
                    }
                }

                if (shouldUpdateChainHeight) {
                    // Update the node's ChainWork...
                    final ChainHeight chainHeight = _getChainHeight(bitcoinRpcConnector);
                    if (chainHeight != null) {
                        _rpcConfigurations.put(rpcConfiguration, chainHeight);
                    }
                }

                final RpcConfiguration bestRpcConfiguration = _selectBestRpcConfiguration(notification.messageType);
                if ( (bestRpcConfiguration != null) && Util.areEqual(bestRpcConfiguration, rpcConfiguration) ) {
                    // If the notification if from the highest ChainHeight node with ZMQ enabled for this message type, then relay the message.
                    _relayNotification(notification);
                }
            }
        };

        final MutableList<ZmqNotificationThread> zmqNotificationThreads = new MutableList<>();
        for (final String endpointType : zmqEndpoints.keySet()) {
            final String endpoint = zmqEndpoints.get(endpointType);
            final ZmqNotificationThread newNotificationThread = new ZmqNotificationThread(endpointType, endpoint, callback);
            zmqNotificationThreads.add(newNotificationThread);
        }
        _zmqNotificationThreads.put(rpcConfiguration, zmqNotificationThreads);

        for (final ZmqNotificationThread notificationThread : zmqNotificationThreads) {
            notificationThread.start();
        }
    }

    public RpcProxyServer(final Integer port, final List<RpcConfiguration> rpcConfigurations) {
        _port = port;
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            _rpcConfigurations.put(rpcConfiguration, UNKNOWN_CHAIN_HEIGHT);
            _subscribeToNotifications(rpcConfiguration);
        }
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
            final ChainHeight chainHeight = _getChainHeight(bitcoinRpcConnector);
            if (chainHeight != null) {
                _rpcConfigurations.put(rpcConfiguration, chainHeight);
            }
        }

        final Endpoint endpoint;
        {
            final NodeSelector nodeSelector = new NodeSelector() {
                @Override
                public RpcConfiguration selectBestNode() {
                    final RpcConfiguration bestRpcConfiguration = _selectBestRpcConfiguration(null);
                    if (bestRpcConfiguration == null) {
                        Logger.debug("No node available.");
                        return null;
                    }

                    final ChainHeight bestChainHeight = _rpcConfigurations.get(bestRpcConfiguration);
                    Logger.debug("Selected: " + bestRpcConfiguration.getHost() + ":" + bestRpcConfiguration.getPort() + " " + bestChainHeight);
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
