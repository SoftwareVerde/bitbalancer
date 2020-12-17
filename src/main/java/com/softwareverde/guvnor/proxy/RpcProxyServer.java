package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
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
import com.softwareverde.util.HexUtil;
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

    protected final ConcurrentHashMap<NotificationType, ZmqNotificationPublisherThread> _zmqPublisherThreads = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<RpcConfiguration, List<ZmqNotificationThread>> _zmqNotificationThreads = new ConcurrentHashMap<>();

    protected final SleepyService _chainWorkMonitor;

    protected ChainHeight _getChainHeight(final RpcConfiguration rpcConfiguration) {
        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();

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
                Logger.debug("Received error from " + rpcConfiguration + ": " + errorString);
                return null;
            }

            resultJson = responseJson.get("result");
        }

        final Long blockHeight = resultJson.getLong("blocks");
        final ChainWork chainWork = ChainWork.fromHexString(resultJson.getString("chainwork"));

        return new ChainHeight(blockHeight, chainWork);
    }

    protected Map<NotificationType, String> _getZmqEndpoints(final RpcConfiguration rpcConfiguration) {
        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
        final String baseEndpointUri = ("tcp://" + bitcoinRpcConnector.getHost() + ":");

        if (rpcConfiguration.hasZmqPorts()) {
            final HashMap<NotificationType, String> zmqEndpoints = new HashMap<>();
            for (final NotificationType notificationType : NotificationType.values()) {
                final Integer zmqPort = rpcConfiguration.getZmqPort(notificationType);
                if (zmqPort == null) { continue; }

                final String endpointUri = (baseEndpointUri + zmqPort);
                zmqEndpoints.put(notificationType, endpointUri);
            }
            return zmqEndpoints;
        }

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

        Logger.trace("Attempting to collect ZMQ configuration for node: " + rpcConfiguration);

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);

        final HashMap<NotificationType, String> zmqEndpoints = new HashMap<>();
        final Json resultJson;
        {
            final Response response = bitcoinRpcConnector.handleRequest(request);
            final Json responseJson = Json.parse(StringUtil.bytesToString(response.getContent()));

            final String errorString = responseJson.getString("error");
            if (! Util.isBlank(errorString)) {
                Logger.debug("Received error from " + rpcConfiguration + ": " + errorString);
                return zmqEndpoints;
            }

            resultJson = responseJson.get("result");
        }

        for (int i = 0; i < resultJson.length(); ++i) {
            final Json configJson = resultJson.get(i);
            final String messageTypeString = configJson.getString("type");
            final NotificationType notificationType = ZmqMessageTypeConverter.fromPublishString(messageTypeString);
            final String address = configJson.getString("address");

            final Integer port;
            {
                final int colonIndex = address.lastIndexOf(':');
                if (colonIndex < 0) { continue; }

                final int portBeginIndex = (colonIndex + 1);
                if (portBeginIndex >= address.length()) { continue; }

                port = Util.parseInt(address.substring(portBeginIndex));
            }

            final String endpointUri = (baseEndpointUri + port);
            zmqEndpoints.put(notificationType, endpointUri);
        }

        return zmqEndpoints;
    }

    protected RpcConfiguration _selectBestRpcConfiguration() {
        return _selectBestRpcConfiguration(null);
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final NotificationType requiredZmqNotificationType) {
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
        final NotificationType notificationType = notification.notificationType;
        final ZmqNotificationPublisherThread publisherThread = _zmqPublisherThreads.get(notificationType);
        if (publisherThread != null) {
            publisherThread.sendMessage(notification);
        }
    }

    /**
     * Refreshes all nodes' chainHeights that fall below `bestChainHeight`.
     */
    protected void _updateChainHeights(final ChainHeight bestChainHeight) {
        for (final RpcConfiguration rpcConfiguration : _rpcConfigurations.keySet()) {
            final ChainHeight chainHeight = _rpcConfigurations.get(rpcConfiguration);

            if (bestChainHeight.compareTo(chainHeight) > 0) {
                final ChainHeight newChainHeight = _getChainHeight(rpcConfiguration);
                if (newChainHeight != null) {
                    Logger.debug("Updating chainHeight for " + rpcConfiguration + ": " + newChainHeight);
                    _rpcConfigurations.put(rpcConfiguration, newChainHeight);
                }
            }
        }
    }

    protected void _subscribeToNotifications(final RpcConfiguration rpcConfiguration) {
        {
            final List<ZmqNotificationThread> zmqNotificationThread = _zmqNotificationThreads.get(rpcConfiguration);
            if (zmqNotificationThread != null) { return; }
        }

        final Map<NotificationType, String> zmqEndpoints = _getZmqEndpoints(rpcConfiguration);

        final Boolean hasBlockEndpoint = zmqEndpoints.containsKey(NotificationType.BLOCK);
        final Boolean hasBlockHashEndpoint = zmqEndpoints.containsKey(NotificationType.BLOCK_HASH);

        final ZmqNotificationThread.Callback callback = new ZmqNotificationThread.Callback() {
            @Override
            public void onNewNotification(final Notification notification) {
                boolean shouldUpdateChainHeight = false;
                if (hasBlockHashEndpoint) {
                    if (Util.areEqual(NotificationType.BLOCK_HASH, notification.notificationType)) {
                        shouldUpdateChainHeight = true;
                    }
                }
                else if (hasBlockEndpoint) {
                    if (Util.areEqual(NotificationType.BLOCK, notification.notificationType)) {
                        shouldUpdateChainHeight = true;
                    }
                }

                if (shouldUpdateChainHeight) {
                    // Update the node's ChainWork...
                    final ChainHeight chainHeight = _getChainHeight(rpcConfiguration);
                    if (chainHeight != null) {
                        Logger.debug("Updating chainHeight for " + rpcConfiguration + ": " + chainHeight);
                        final ChainHeight oldChainHeight = _rpcConfigurations.put(rpcConfiguration, chainHeight);

                        if (chainHeight.compareTo(oldChainHeight) > 0) {
                            Logger.debug("New best block detected. Refreshing all nodes' chainHeights.");
                            _updateChainHeights(chainHeight);
                        }
                    }
                }

                final RpcConfiguration bestRpcConfiguration = _selectBestRpcConfiguration(notification.notificationType);
                if ( (bestRpcConfiguration != null) && Util.areEqual(bestRpcConfiguration, rpcConfiguration) ) {
                    // If the notification if from the highest ChainHeight node with ZMQ enabled for this message type, then relay the message.
                    Logger.trace("Relaying: " + notification.notificationType + " " + HexUtil.toHexString(notification.payload.getBytes(0, 32)) + " +" + (notification.payload.getByteCount() - 32) + " from " + rpcConfiguration);
                    _relayNotification(notification);
                }
            }
        };

        final MutableList<ZmqNotificationThread> zmqNotificationThreads = new MutableList<>();
        for (final NotificationType endpointType : zmqEndpoints.keySet()) {
            final String endpoint = zmqEndpoints.get(endpointType);
            final ZmqNotificationThread newNotificationThread = new ZmqNotificationThread(endpointType, endpoint, callback);
            zmqNotificationThreads.add(newNotificationThread);
        }
        _zmqNotificationThreads.put(rpcConfiguration, zmqNotificationThreads);

        for (final ZmqNotificationThread notificationThread : zmqNotificationThreads) {
            notificationThread.start();
        }
    }

    public RpcProxyServer(final Integer port, final List<RpcConfiguration> rpcConfigurations, final ZmqConfiguration zmqConfiguration) {
        _port = port;
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            _rpcConfigurations.put(rpcConfiguration, UNKNOWN_CHAIN_HEIGHT);
            _subscribeToNotifications(rpcConfiguration);
        }
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            final ChainHeight chainHeight = _getChainHeight(rpcConfiguration);
            if (chainHeight != null) {
                _rpcConfigurations.put(rpcConfiguration, chainHeight);
            }
        }

        if (zmqConfiguration != null) {
            for (final NotificationType zmqNotificationType : zmqConfiguration.getSupportedMessageTypes()) {
                final Integer zmqPort = zmqConfiguration.getPort(zmqNotificationType);
                final ZmqNotificationPublisherThread zmqNotificationPublisherThread = ZmqNotificationPublisherThread.newZmqNotificationPublisherThread(zmqNotificationType, "*", zmqPort);
                _zmqPublisherThreads.put(zmqNotificationType, zmqNotificationPublisherThread);
            }
        }
        for (final ZmqNotificationPublisherThread zmqNotificationPublisherThread : _zmqPublisherThreads.values()) {
            zmqNotificationPublisherThread.start();
        }

        final Endpoint rpcProxyEndpoint;
        {
            final NodeSelector nodeSelector = new NodeSelector() {
                @Override
                public RpcConfiguration selectBestNode() {
                    final RpcConfiguration bestRpcConfiguration = _selectBestRpcConfiguration();
                    if (bestRpcConfiguration == null) {
                        Logger.debug("No node available.");
                        return null;
                    }

                    final ChainHeight bestChainHeight = _rpcConfigurations.get(bestRpcConfiguration);
                    Logger.debug("Selected: " + bestRpcConfiguration.getHost() + ":" + bestRpcConfiguration.getPort() + " " + bestChainHeight);
                    return bestRpcConfiguration;
                }
            };

            rpcProxyEndpoint = new Endpoint(
                new RpcProxyHandler(nodeSelector)
            );
            rpcProxyEndpoint.setPath("/");
            rpcProxyEndpoint.setStrictPathEnabled(true);
        }

        final NotifyEndpoint.Context notifyContext = new NotifyEndpoint.Context() {
            @Override
            public RpcConfiguration getBestRpcConfiguration() {
                return _selectBestRpcConfiguration();
            }

            @Override
            public List<RpcConfiguration> getRpcConfigurations() {
                return new MutableList<RpcConfiguration>(_rpcConfigurations.keySet());
            }

            @Override
            public void relayNotification(final Notification notification) {
                Logger.trace("Relaying from notification endpoint: " + notification.notificationType + " " + HexUtil.toHexString(notification.payload.getBytes(0, 32)) + " +" + (notification.payload.getByteCount() - 32));
                _relayNotification(notification);
            }
        };

        final Endpoint blockNotifyEndpoint;
        {
            blockNotifyEndpoint = new Endpoint(
                new NotifyEndpoint(NotificationType.BLOCK, notifyContext)
            );
            blockNotifyEndpoint.setPath("/api/v1/publish/block/raw");
            blockNotifyEndpoint.setStrictPathEnabled(true);
        }

        final Endpoint blockHashNotifyEndpoint;
        {
            blockHashNotifyEndpoint = new Endpoint(
                new NotifyEndpoint(NotificationType.BLOCK_HASH, notifyContext, Sha256Hash.BYTE_COUNT)
            );
            blockHashNotifyEndpoint.setPath("/api/v1/publish/block/hash");
            blockHashNotifyEndpoint.setStrictPathEnabled(true);
        }

        final Endpoint transactionNotifyEndpoint;
        {
            transactionNotifyEndpoint = new Endpoint(
                new NotifyEndpoint(NotificationType.TRANSACTION, notifyContext)
            );
            transactionNotifyEndpoint.setPath("/api/v1/publish/transaction/raw");
            transactionNotifyEndpoint.setStrictPathEnabled(true);
        }

        final Endpoint transactionHashNotifyEndpoint;
        {
            transactionHashNotifyEndpoint = new Endpoint(
                new NotifyEndpoint(NotificationType.TRANSACTION_HASH, notifyContext, Sha256Hash.BYTE_COUNT)
            );
            transactionHashNotifyEndpoint.setPath("/api/v1/publish/transaction/hash");
            transactionHashNotifyEndpoint.setStrictPathEnabled(true);
        }

        _httpServer = new HttpServer();
        _httpServer.setPort(_port);
        _httpServer.enableEncryption(false);
        _httpServer.redirectToTls(false);
        _httpServer.addEndpoint(rpcProxyEndpoint);
        _httpServer.addEndpoint(blockNotifyEndpoint);
        _httpServer.addEndpoint(blockHashNotifyEndpoint);
        _httpServer.addEndpoint(transactionNotifyEndpoint);
        _httpServer.addEndpoint(transactionHashNotifyEndpoint);

        _chainWorkMonitor = new SleepyService() {
            @Override
            protected void _onStart() { }

            @Override
            protected Boolean _run() {
                final ChainHeight bestChainHeight;
                {
                    final RpcConfiguration bestRpcConfiguration = _selectBestRpcConfiguration();
                    bestChainHeight = Util.coalesce(_rpcConfigurations.get(bestRpcConfiguration), UNKNOWN_CHAIN_HEIGHT);
                }

                for (final RpcConfiguration rpcConfiguration : _rpcConfigurations.keySet()) {
                    final ChainHeight chainHeight = _rpcConfigurations.get(rpcConfiguration);

                    if (bestChainHeight.compareTo(chainHeight) > 0) {
                        final ChainHeight newChainHeight = _getChainHeight(rpcConfiguration);
                        if ( (newChainHeight != null) && (newChainHeight.compareTo(chainHeight) > 0) ) {
                            Logger.debug("Updating chainHeight for " + rpcConfiguration + ": " + newChainHeight);
                            _rpcConfigurations.put(rpcConfiguration, newChainHeight);
                        }
                    }
                }

                try {
                    Thread.sleep(15000L);
                    return true;
                }
                catch (final Exception Exception) {
                    return false;
                }
            }

            @Override
            protected void _onSleep() { }
        };
    }

    public void start() {
        _httpServer.start();
        _chainWorkMonitor.start();
    }

    public void stop() {
        _httpServer.stop();
        _chainWorkMonitor.stop();
    }

    public Integer getPort() {
        return _port;
    }
}
