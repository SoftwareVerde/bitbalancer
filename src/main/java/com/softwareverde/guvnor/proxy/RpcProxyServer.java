package com.softwareverde.guvnor.proxy;

import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.NotificationCallback;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.guvnor.proxy.zmq.ZmqConfiguration;
import com.softwareverde.guvnor.proxy.zmq.ZmqNotificationPublisherThread;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcProxyServer {
    protected static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger(1);
    protected static final ChainHeight UNKNOWN_CHAIN_HEIGHT = new ChainHeight(0L, null);

    protected final Integer _port;
    protected final HttpServer _httpServer;
    protected final HashMap<RpcConfiguration, ChainHeight> _rpcConfigurations = new HashMap<>();

    protected final ConcurrentHashMap<NotificationType, ZmqNotificationPublisherThread> _zmqPublisherThreads = new ConcurrentHashMap<>();

    protected final SleepyService _chainWorkMonitor;

    protected RpcConfiguration _selectBestRpcConfiguration() {
        return _selectBestRpcConfiguration(null);
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final NotificationType requiredNotificationType) {
        int bestHierarchy = Integer.MIN_VALUE;
        ChainHeight bestChainHeight = UNKNOWN_CHAIN_HEIGHT;
        RpcConfiguration bestRpcConfiguration = null;

        for (final RpcConfiguration rpcConfiguration : _rpcConfigurations.keySet()) {
            final ChainHeight chainHeight = _rpcConfigurations.get(rpcConfiguration);

            if (requiredNotificationType != null) {
                final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                final boolean hasNotificationType = bitcoinRpcConnector.supportsNotification(requiredNotificationType);
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
            final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();

            if (bestChainHeight.compareTo(chainHeight) > 0) {
                final ChainHeight newChainHeight = bitcoinRpcConnector.getChainHeight();
                if (newChainHeight != null) {
                    Logger.debug("Updating chainHeight for " + rpcConfiguration + ": " + newChainHeight);
                    _rpcConfigurations.put(rpcConfiguration, newChainHeight);
                }
            }
        }
    }

    protected void _subscribeToNotifications(final RpcConfiguration rpcConfiguration) {
        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
        if (! bitcoinRpcConnector.supportsNotifications()) { return; }

        final Boolean hasBlockEndpoint = bitcoinRpcConnector.supportsNotification(NotificationType.BLOCK);
        final Boolean hasBlockHashEndpoint = bitcoinRpcConnector.supportsNotification(NotificationType.BLOCK_HASH);

        final NotificationCallback callback = new NotificationCallback() {
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
                    final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                    final ChainHeight chainHeight = bitcoinRpcConnector.getChainHeight();
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

        bitcoinRpcConnector.subscribeToNotifications(callback);
    }

    public RpcProxyServer(final Integer port, final List<RpcConfiguration> rpcConfigurations, final ZmqConfiguration zmqConfiguration) {
        _port = port;
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            _rpcConfigurations.put(rpcConfiguration, UNKNOWN_CHAIN_HEIGHT);
            _subscribeToNotifications(rpcConfiguration);
        }
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
            final ChainHeight chainHeight = bitcoinRpcConnector.getChainHeight();
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
                    Logger.debug("Selected: " + bestRpcConfiguration + " " + bestChainHeight);
                    return bestRpcConfiguration;
                }

                @Override
                public List<RpcConfiguration> getNodes() {
                    return new MutableList<>(_rpcConfigurations.keySet());
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
                        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                        final ChainHeight newChainHeight = bitcoinRpcConnector.getChainHeight();
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
