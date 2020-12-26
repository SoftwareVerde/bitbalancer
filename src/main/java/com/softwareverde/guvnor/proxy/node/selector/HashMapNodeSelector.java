package com.softwareverde.guvnor.proxy.node.selector;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HashMapNodeSelector implements NodeSelector {
    protected final Map<RpcConfiguration, ChainHeight> _rpcConfigurations;

    protected RpcConfiguration _selectBestRpcConfiguration() {
        return _selectBestRpcConfiguration(null, null);
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final NotificationType requiredNotificationType) {
        return _selectBestRpcConfiguration(requiredNotificationType, null);
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final List<RpcConfiguration> excludedConfigurations) {
        return _selectBestRpcConfiguration(null, excludedConfigurations);
    }

    protected RpcConfiguration _selectBestRpcConfiguration(final NotificationType requiredNotificationType, final List<RpcConfiguration> excludedConfigurations) {
        int bestHierarchy = Integer.MIN_VALUE;
        ChainHeight bestChainHeight = ChainHeight.UNKNOWN_CHAIN_HEIGHT;
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

    public HashMapNodeSelector() {
        _rpcConfigurations = new ConcurrentHashMap<>();
    }

    public HashMapNodeSelector(final Map<RpcConfiguration, ChainHeight> rpcConfigurations) {
        _rpcConfigurations = rpcConfigurations;
    }

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
    public RpcConfiguration selectBestNode(final List<RpcConfiguration> excludedConfiguration) {
        final RpcConfiguration bestRpcConfiguration = _selectBestRpcConfiguration(excludedConfiguration);
        if (bestRpcConfiguration == null) {
            Logger.debug("No node available.");
            return null;
        }

        final ChainHeight bestChainHeight = _rpcConfigurations.get(bestRpcConfiguration);
        Logger.debug("Selected: " + bestRpcConfiguration + " " + bestChainHeight);
        return bestRpcConfiguration;
    }

    @Override
    public RpcConfiguration selectBestNode(final NotificationType requiredNotificationType) {
        return _selectBestRpcConfiguration(requiredNotificationType);
    }

    @Override
    public List<RpcConfiguration> getNodes() {
        return new MutableList<>(_rpcConfigurations.keySet());
    }
}
