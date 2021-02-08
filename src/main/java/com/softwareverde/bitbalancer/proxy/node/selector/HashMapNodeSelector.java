package com.softwareverde.bitbalancer.proxy.node.selector;

import com.softwareverde.bitbalancer.proxy.rpc.ChainHeight;
import com.softwareverde.bitbalancer.proxy.rpc.RpcConfiguration;
import com.softwareverde.bitbalancer.proxy.rpc.connector.BitBalancerRpcConnector;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.Logger;

public class HashMapNodeSelector implements NodeSelector {
    protected final List<RpcConfiguration> _rpcConfigurations;
    protected final Integer _maxOrphanDepth;

    protected RpcConfiguration _selectBestRpcConfiguration() {
        return _selectBestRpcConfiguration(null, null);
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final RpcNotificationType requiredNotificationType) {
        return _selectBestRpcConfiguration(requiredNotificationType, null);
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final List<RpcConfiguration> excludedConfigurations) {
        return _selectBestRpcConfiguration(null, excludedConfigurations);
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final RpcNotificationType requiredNotificationType, final List<RpcConfiguration> excludedConfigurations) {
        int bestHierarchy = Integer.MAX_VALUE;
        ChainHeight bestChainHeight = ChainHeight.UNKNOWN_CHAIN_HEIGHT;
        RpcConfiguration bestRpcConfiguration = null;

        // Select the best hierarchical node first in order to prefer it while using _maxOrphanDepth...
        for (final RpcConfiguration rpcConfiguration : _rpcConfigurations) {
            final Integer hierarchy = rpcConfiguration.getHierarchy();
            if (hierarchy <= bestHierarchy) {
                bestHierarchy = hierarchy;
                bestRpcConfiguration = rpcConfiguration;
            }
        }

        for (final RpcConfiguration rpcConfiguration : _rpcConfigurations) {
            final ChainHeight chainHeight = rpcConfiguration.getChainHeight();

            if (requiredNotificationType != null) {
                final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
                final boolean hasNotificationType = bitcoinRpcConnector.supportsNotification(requiredNotificationType);
                if (! hasNotificationType) { continue; }
            }

            final boolean isBetterChainHeight = chainHeight.isBetterThan(bestChainHeight, _maxOrphanDepth);
            final boolean areEqualChainHeights = chainHeight.equals(bestChainHeight);
            final boolean isLesserChainHeight = (! isBetterChainHeight) && (! areEqualChainHeights);
            if (isLesserChainHeight) { continue; }

            final int hierarchy = rpcConfiguration.getHierarchy();
            if ( isBetterChainHeight || (areEqualChainHeights && (hierarchy <= bestHierarchy)) ) {
                if ( (excludedConfigurations != null) && excludedConfigurations.contains(rpcConfiguration) ) {
                    continue;
                }

                bestChainHeight = chainHeight;
                bestHierarchy = hierarchy;
                bestRpcConfiguration = rpcConfiguration;
            }
        }

        return bestRpcConfiguration;
    }

    public HashMapNodeSelector(final List<RpcConfiguration> rpcConfigurations) {
        this(rpcConfigurations, 0);
    }

    public HashMapNodeSelector(final List<RpcConfiguration> rpcConfigurations, final Integer maxOrphanDepth) {
        _rpcConfigurations = rpcConfigurations.asConst();
        _maxOrphanDepth = maxOrphanDepth;
    }

    @Override
    public RpcConfiguration selectBestNode() {
        final RpcConfiguration bestRpcConfiguration = _selectBestRpcConfiguration();
        if (bestRpcConfiguration == null) {
            Logger.debug("No node available.");
            return null;
        }

        final ChainHeight bestChainHeight = bestRpcConfiguration.getChainHeight();
        Logger.debug("Selected: " + bestRpcConfiguration + " " + bestChainHeight);
        return bestRpcConfiguration;
    }

    @Override
    public RpcConfiguration selectBestNode(final List<RpcConfiguration> excludedConfiguration) {
        final RpcConfiguration bestRpcConfiguration = _selectBestRpcConfiguration(excludedConfiguration);
        if (bestRpcConfiguration == null) {
            Logger.debug("No node available.");
            return null;
        }

        final ChainHeight bestChainHeight = bestRpcConfiguration.getChainHeight();
        Logger.debug("Selected: " + bestRpcConfiguration + " " + bestChainHeight);
        return bestRpcConfiguration;
    }

    @Override
    public RpcConfiguration selectBestNode(final RpcNotificationType requiredNotificationType) {
        return _selectBestRpcConfiguration(requiredNotificationType);
    }

    @Override
    public List<RpcConfiguration> getNodes() {
        return _rpcConfigurations;
    }

    @Override
    public ChainHeight getBestChainHeight() {
        ChainHeight bestChainHeight = ChainHeight.UNKNOWN_CHAIN_HEIGHT;
        for (final RpcConfiguration rpcConfiguration : _rpcConfigurations) {
            final ChainHeight chainHeight = rpcConfiguration.getChainHeight();
            final boolean isBetterChainHeight = chainHeight.isBetterThan(bestChainHeight);
            if (isBetterChainHeight) {
                bestChainHeight = chainHeight;
            }
        }
        return bestChainHeight;
    }
}