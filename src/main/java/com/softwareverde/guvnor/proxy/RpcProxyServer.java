package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.guvnor.proxy.node.selector.HashMapNodeSelector;
import com.softwareverde.guvnor.proxy.node.selector.NodeSelector;
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

import java.util.concurrent.ConcurrentHashMap;

public class RpcProxyServer {
    protected final Integer _port;
    protected final HttpServer _httpServer;
    protected final List<RpcConfiguration> _rpcConfigurations;
    protected final NodeSelector _nodeSelector;
    protected final BlockTemplateManager _blockTemplateManager;

    protected final ConcurrentHashMap<NotificationType, ZmqNotificationPublisherThread> _zmqPublisherThreads = new ConcurrentHashMap<>();

    protected final SleepyService _chainWorkMonitor;

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
        boolean chainHeightWasUpdated = false;
        for (final RpcConfiguration rpcConfiguration : _rpcConfigurations) {
            final ChainHeight chainHeight = rpcConfiguration.getChainHeight();

            if (bestChainHeight.isBetterThan(chainHeight)) {
                final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                final ChainHeight newChainHeight = bitcoinRpcConnector.getChainHeight();
                if ( (newChainHeight != null) && newChainHeight.isBetterThan(chainHeight) ) {
                    Logger.debug("Updating chainHeight for " + rpcConfiguration + ": " + newChainHeight);
                    rpcConfiguration.setChainHeight(newChainHeight);
                    chainHeightWasUpdated = true;
                }
            }
        }

        if (chainHeightWasUpdated) {
            // Refresh the block template after a ChainHeight update to ensure new nodes agree on the cached template.
            if (_blockTemplateManager instanceof CachingBlockTemplateManager) {
                Logger.debug("ChainHeight updated for at least one node; refreshing template.");
                ((CachingBlockTemplateManager) _blockTemplateManager).updateBlockTemplate();
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
                Logger.trace("Notification from " + rpcConfiguration + ": " + notification.notificationType);

                BlockHeader blockHeader = null;
                boolean shouldUpdateChainHeight = false;
                if (hasBlockHashEndpoint) {
                    if (Util.areEqual(NotificationType.BLOCK_HASH, notification.notificationType)) {
                        shouldUpdateChainHeight = true;
                    }
                }
                else if (hasBlockEndpoint) {
                    if (Util.areEqual(NotificationType.BLOCK, notification.notificationType)) {
                        shouldUpdateChainHeight = true;

                        final String blockHeaderHexString = StringUtil.bytesToString(
                            notification.payload.getBytes(0, (BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT * 2))
                        );
                        final ByteArray blockHeaderBytes = ByteArray.fromHexString(blockHeaderHexString);
                        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
                        blockHeader = blockHeaderInflater.fromBytes(blockHeaderBytes);
                    }
                }

                if (shouldUpdateChainHeight) {
                    // Update the node's ChainWork...
                    Logger.trace("Requesting chainHeight for " + rpcConfiguration + " after " + notification.notificationType + " notification.");
                    final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                    final ChainHeight chainHeight = bitcoinRpcConnector.getChainHeight();
                    if (chainHeight != null) {
                        final ChainHeight previousBestChainHeight = _nodeSelector.getBestChainHeight();

                        Logger.debug("Updating chainHeight for " + rpcConfiguration + ": " + chainHeight);
                        rpcConfiguration.setChainHeight(chainHeight);

                        if (chainHeight.isBetterThan(previousBestChainHeight)) {
                            if (_blockTemplateManager instanceof CachingBlockTemplateManager) {
                                ((CachingBlockTemplateManager) _blockTemplateManager).onNewBlock(blockHeader, chainHeight);
                            }

                            Logger.debug("New best block detected (" + chainHeight + "). Refreshing all nodes' chainHeights.");
                            _updateChainHeights(chainHeight);
                        }
                    }
                    else {
                        Logger.debug("Unable to get chainHeight for " + rpcConfiguration + " after block notification.");
                    }
                }

                final RpcConfiguration bestRpcConfiguration = _nodeSelector.selectBestNode(notification.notificationType);
                if ( (bestRpcConfiguration != null) && Util.areEqual(bestRpcConfiguration, rpcConfiguration) ) {
                    // If the notification if from the highest ChainHeight node with ZMQ enabled for this message type, then relay the message.
                    Logger.trace("Relaying: " + notification.notificationType + " " + HexUtil.toHexString(notification.payload.getBytes(0, 32)) + " +" + (notification.payload.getByteCount() - 32) + " from " + rpcConfiguration);
                    _relayNotification(notification);
                }
            }
        };

        bitcoinRpcConnector.subscribeToNotifications(callback);
    }

    public RpcProxyServer(final Integer port, final List<RpcConfiguration> rpcConfigurations, final ZmqConfiguration zmqConfiguration, final Long blockTemplateCacheDuration) {
        _port = port;
        _rpcConfigurations = rpcConfigurations.asConst();
        _nodeSelector = new HashMapNodeSelector(_rpcConfigurations);

        if (Util.coalesce(blockTemplateCacheDuration) > 0L) {
            final CachingBlockTemplateManager blockTemplateManager = new CachingBlockTemplateManager(_nodeSelector);
            blockTemplateManager.setBlockTemplatePollPeriod(blockTemplateCacheDuration);
            _blockTemplateManager = blockTemplateManager;
        }
        else {
            _blockTemplateManager = new BlockTemplateManager(_nodeSelector);
        }

        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            rpcConfiguration.setChainHeight(ChainHeight.UNKNOWN_CHAIN_HEIGHT);
            _subscribeToNotifications(rpcConfiguration);
        }
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
            final ChainHeight chainHeight = bitcoinRpcConnector.getChainHeight();
            if (chainHeight != null) {
                rpcConfiguration.setChainHeight(chainHeight);
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

            rpcProxyEndpoint = new Endpoint(
                new RpcProxyHandler(_nodeSelector, _blockTemplateManager)
            );
            rpcProxyEndpoint.setPath("/");
            rpcProxyEndpoint.setStrictPathEnabled(true);
        }

        final NotifyEndpoint.Context notifyContext = new NotifyEndpoint.Context() {
            @Override
            public RpcConfiguration getBestRpcConfiguration() {
                return _nodeSelector.selectBestNode();
            }

            @Override
            public List<RpcConfiguration> getRpcConfigurations() {
                return _rpcConfigurations;
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
                    final RpcConfiguration bestRpcConfiguration = _nodeSelector.selectBestNode();
                    bestChainHeight = bestRpcConfiguration.getChainHeight();
                }

                for (final RpcConfiguration rpcConfiguration : _rpcConfigurations) {
                    final ChainHeight chainHeight = rpcConfiguration.getChainHeight();

                    if (bestChainHeight.isBetterThan(chainHeight)) {
                        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                        final ChainHeight newChainHeight = bitcoinRpcConnector.getChainHeight();
                        if ( (newChainHeight != null) && newChainHeight.isBetterThan(chainHeight) ) {
                            Logger.debug("Updating chainHeight for " + rpcConfiguration + ": " + newChainHeight);
                            rpcConfiguration.setChainHeight(newChainHeight);
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
        if (_blockTemplateManager instanceof CachingBlockTemplateManager) {
            ((CachingBlockTemplateManager) _blockTemplateManager).start();
        }
    }

    public void stop() {
        _httpServer.stop();
        _chainWorkMonitor.stop();
        if (_blockTemplateManager instanceof CachingBlockTemplateManager) {
            ((CachingBlockTemplateManager) _blockTemplateManager).stop();
        }
    }

    public Integer getPort() {
        return _port;
    }
}
