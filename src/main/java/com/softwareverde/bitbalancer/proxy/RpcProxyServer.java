package com.softwareverde.bitbalancer.proxy;

import com.softwareverde.bitbalancer.proxy.node.selector.HashMapNodeSelector;
import com.softwareverde.bitbalancer.proxy.node.selector.NodeSelector;
import com.softwareverde.bitbalancer.proxy.rpc.ChainHeight;
import com.softwareverde.bitbalancer.proxy.rpc.RpcConfiguration;
import com.softwareverde.bitbalancer.proxy.rpc.connector.BitBalancerRpcConnector;
import com.softwareverde.bitbalancer.proxy.zmq.ZmqConfiguration;
import com.softwareverde.bitbalancer.proxy.zmq.ZmqNotificationPublisherThread;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.rpc.RpcNotification;
import com.softwareverde.bitcoin.rpc.RpcNotificationCallback;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.concurrent.ConcurrentHashMap;

public class RpcProxyServer {
    protected final Integer _port;
    protected final HttpServer _httpServer;
    protected final List<RpcConfiguration> _rpcConfigurations;
    protected final NodeSelector _nodeSelector;
    protected final BlockTemplateManager _blockTemplateManager;

    protected final ConcurrentHashMap<RpcNotificationType, ZmqNotificationPublisherThread> _zmqPublisherThreads = new ConcurrentHashMap<>();
    protected final CircleBuffer<RpcNotification> _recentNotifications = new CircleBuffer<>(32);

    protected final SleepyService _chainWorkMonitor;

    /**
     * Adds the Notification to the _recentNotifications set.
     *  Returns true if the Notification did not already exist within the _recentNotifications set.
     */
    protected Boolean _registerRecentNotification(final RpcNotification notification) {
        synchronized (_recentNotifications) {
            for (final RpcNotification recentNotification : _recentNotifications) {
                if (Util.areEqual(recentNotification, notification)) {
                    return false;
                }
            }

            _recentNotifications.push(notification);
            return true;
        }
    }

    protected void _relayNotification(final RpcNotification notification) {
        final RpcNotificationType notificationType = notification.rpcNotificationType;
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
        if (bestChainHeight != null) {
            for (final RpcConfiguration rpcConfiguration : _rpcConfigurations) {
                final ChainHeight chainHeight = rpcConfiguration.getChainHeight();

                if (bestChainHeight.isBetterThan(chainHeight)) {
                    final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                    final ChainHeight newChainHeight = bitcoinRpcConnector.getChainHeight();
                    if ( (newChainHeight != null) && newChainHeight.isBetterThan(chainHeight) ) {
                        Logger.debug("Updating chainHeight for " + rpcConfiguration + ": " + newChainHeight);
                        rpcConfiguration.setChainHeight(newChainHeight);
                        chainHeightWasUpdated = true;
                    }
                }
            }
        }
        else {
            chainHeightWasUpdated = true; // bestChainHeight can be null during http hook notification...
        }

        if (chainHeightWasUpdated) {
            // Refresh the block template after a ChainHeight update to ensure new nodes agree on the cached template.
            if (_blockTemplateManager instanceof CachingBlockTemplateManager) {
                Logger.debug("ChainHeight updated for at least one node; refreshing template.");
                ((CachingBlockTemplateManager) _blockTemplateManager).updateBlockTemplate();
            }
        }
    }

    protected void _onBlockNotification(final RpcConfiguration rpcConfiguration, final RpcNotification notification, final BlockHeader blockHeader) {
        // Update the node's ChainWork...
        Logger.trace("Requesting chainHeight for " + rpcConfiguration + " after " + notification.rpcNotificationType + " notification.");
        final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
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

    protected void _subscribeToNotifications(final RpcConfiguration rpcConfiguration) {
        final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
        if (! bitcoinRpcConnector.supportsNotifications()) { return; }

        final Boolean hasBlockEndpoint = bitcoinRpcConnector.supportsNotification(RpcNotificationType.BLOCK);
        final Boolean hasBlockHashEndpoint = bitcoinRpcConnector.supportsNotification(RpcNotificationType.BLOCK_HASH);

        final RpcNotificationCallback callback = new RpcNotificationCallback() {
            @Override
            public void onNewNotification(final RpcNotification notification) {
                Logger.trace("Notification from " + rpcConfiguration + ": " + notification.rpcNotificationType);

                BlockHeader blockHeader = null;
                boolean shouldUpdateChainHeight = false;
                if (hasBlockHashEndpoint) {
                    if (Util.areEqual(RpcNotificationType.BLOCK_HASH, notification.rpcNotificationType)) {
                        shouldUpdateChainHeight = true;
                    }
                }
                else if (hasBlockEndpoint) {
                    if (Util.areEqual(RpcNotificationType.BLOCK, notification.rpcNotificationType)) {
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
                    _onBlockNotification(rpcConfiguration, notification, blockHeader);
                }

                final Boolean isUniqueNotification = _registerRecentNotification(notification);
                if (isUniqueNotification) {
                    Logger.trace("Relaying: " + notification.rpcNotificationType + " " + HexUtil.toHexString(notification.payload.getBytes(0, Sha256Hash.BYTE_COUNT)) + " +" + (notification.payload.getByteCount() - Sha256Hash.BYTE_COUNT) + " from " + rpcConfiguration);
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
            final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
            final ChainHeight chainHeight = bitcoinRpcConnector.getChainHeight();
            if (chainHeight != null) {
                rpcConfiguration.setChainHeight(chainHeight);
            }
        }

        if (zmqConfiguration != null) {
            for (final RpcNotificationType zmqNotificationType : zmqConfiguration.getSupportedMessageTypes()) {
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
                new RpcProxyHandler(_nodeSelector, _blockTemplateManager, zmqConfiguration)
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
            public void relayNotification(final RpcNotification notification) {
                _updateChainHeights(null);

                final Boolean isUniqueNotification = _registerRecentNotification(notification);
                if (isUniqueNotification) {
                    Logger.trace("Relaying: " + notification.rpcNotificationType + " " + HexUtil.toHexString(notification.payload.getBytes(0, Sha256Hash.BYTE_COUNT)) + " +" + (notification.payload.getByteCount() - Sha256Hash.BYTE_COUNT) + " from http hook");
                    _relayNotification(notification);
                }
            }
        };

        final Endpoint blockNotifyEndpoint;
        {
            blockNotifyEndpoint = new Endpoint(
                new NotifyEndpoint(RpcNotificationType.BLOCK, notifyContext)
            );
            blockNotifyEndpoint.setPath("/api/v1/publish/block/raw");
            blockNotifyEndpoint.setStrictPathEnabled(true);
        }

        final Endpoint blockHashNotifyEndpoint;
        {
            blockHashNotifyEndpoint = new Endpoint(
                new NotifyEndpoint(RpcNotificationType.BLOCK_HASH, notifyContext, Sha256Hash.BYTE_COUNT)
            );
            blockHashNotifyEndpoint.setPath("/api/v1/publish/block/hash");
            blockHashNotifyEndpoint.setStrictPathEnabled(true);
        }

        final Endpoint transactionNotifyEndpoint;
        {
            transactionNotifyEndpoint = new Endpoint(
                new NotifyEndpoint(RpcNotificationType.TRANSACTION, notifyContext)
            );
            transactionNotifyEndpoint.setPath("/api/v1/publish/transaction/raw");
            transactionNotifyEndpoint.setStrictPathEnabled(true);
        }

        final Endpoint transactionHashNotifyEndpoint;
        {
            transactionHashNotifyEndpoint = new Endpoint(
                new NotifyEndpoint(RpcNotificationType.TRANSACTION_HASH, notifyContext, Sha256Hash.BYTE_COUNT)
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
                        final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
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
