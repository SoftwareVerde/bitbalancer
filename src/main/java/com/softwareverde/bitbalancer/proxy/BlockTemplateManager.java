package com.softwareverde.bitbalancer.proxy;

import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.list.List;
import com.softwareverde.bitbalancer.proxy.node.selector.NodeSelector;
import com.softwareverde.bitbalancer.proxy.rpc.ChainHeight;
import com.softwareverde.bitbalancer.proxy.rpc.RpcConfiguration;
import com.softwareverde.bitbalancer.proxy.rpc.connector.BitBalancerRpcConnector;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockTemplateManager {
    protected final NodeSelector _nodeSelector;

    protected BlockTemplate _onGetBlockTemplateFailure() {
        boolean wasSuccessful = true;
        int bestValidCount = 0;
        RpcConfiguration bestRpcConfiguration = null;

        final ConcurrentHashMap<RpcConfiguration, BlockTemplate> blockTemplates = new ConcurrentHashMap<>();
        final ConcurrentHashMap<RpcConfiguration, AtomicInteger> countOfValidBlockTemplates = new ConcurrentHashMap<>();

        final List<RpcConfiguration> rpcConfigurations = _nodeSelector.getNodes();

        final ChainHeight bestChainHeight = _nodeSelector.getBestChainHeight();

        try {
            final BatchRunner<RpcConfiguration> batchRunner = new BatchRunner<>(1, true);

            { // Get block templates from each of the nodes...
                batchRunner.run(rpcConfigurations, new BatchRunner.Batch<RpcConfiguration>() {
                    @Override
                    public void run(final List<RpcConfiguration> batch) {
                        final RpcConfiguration rpcConfiguration = batch.get(0); // Workaround for non-specialization of BatchRunner of size 1.
                        final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();

                        final Monitor monitor = bitcoinRpcConnector.getMonitor();
                        final Long maxTimeoutMs = rpcConfiguration.getMaxTimeoutMs();
                        if (maxTimeoutMs != null) {
                            monitor.setMaxDurationMs(maxTimeoutMs);
                        }

                        final NanoTimer nanoTimer = new NanoTimer();
                        nanoTimer.start();
                        final BlockTemplate blockTemplate = bitcoinRpcConnector.getBlockTemplate(monitor);
                        nanoTimer.stop();
                        Logger.debug("Template acquired from " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");

                        if (blockTemplate != null) {
                            blockTemplates.put(rpcConfiguration, blockTemplate);
                        }

                        countOfValidBlockTemplates.put(rpcConfiguration, new AtomicInteger(0));

                        if (blockTemplate == null) {
                            Logger.debug("Failed to obtain template from " + rpcConfiguration + " in " + monitor.getDurationMs() + "ms.");
                        }
                        else {
                            Logger.info("Obtained template (height=" + blockTemplate.getBlockHeight() + ") from " + rpcConfiguration + " in " + monitor.getDurationMs() + "ms.");
                        }
                    }
                });
            }

            { // Validate each of the templates against each of the nodes...
                for (final RpcConfiguration rpcConfigurationForTemplate : rpcConfigurations) {
                    final BlockTemplate blockTemplate = blockTemplates.get(rpcConfigurationForTemplate);
                    if (blockTemplate == null) { continue; }

                    batchRunner.run(rpcConfigurations, new BatchRunner.Batch<RpcConfiguration>() {
                        @Override
                        public void run(final List<RpcConfiguration> batch) {
                            final RpcConfiguration rpcConfigurationForValidation = batch.get(0); // Workaround for non-specialization of BatchRunner of size 1.
                            final BitBalancerRpcConnector bitcoinRpcConnectorForValidation = rpcConfigurationForValidation.getBitcoinRpcConnector();

                            final Monitor monitor = bitcoinRpcConnectorForValidation.getMonitor();
                            final Long maxTimeoutMs = rpcConfigurationForValidation.getMaxTimeoutMs();
                            if (maxTimeoutMs != null) {
                                monitor.setMaxDurationMs(maxTimeoutMs);
                            }

                            final ChainHeight chainHeight = rpcConfigurationForValidation.getChainHeight();
                            final boolean isNodeBehind = bestChainHeight.isBetterThan(chainHeight);
                            if (isNodeBehind) {
                                Logger.debug("Skipping template validation template from " + rpcConfigurationForTemplate + " with " + rpcConfigurationForValidation + "; node is behind on ChainHeight: " + chainHeight);
                                return;
                            }

                            final Boolean isValid = bitcoinRpcConnectorForValidation.validateBlockTemplate(blockTemplate, monitor);

                            if (isValid != null) {
                                Logger.debug("Validated template from " + rpcConfigurationForTemplate + " with " + rpcConfigurationForValidation + " in " + monitor.getDurationMs() + "ms. (" + (isValid ? "VALID" : "INVALID") + ")");
                            }
                            else {
                                Logger.debug("Template validation not supported by " + rpcConfigurationForValidation + ".");
                            }

                            if (Util.coalesce(isValid, true)) {
                                final AtomicInteger currentIsValidCount = countOfValidBlockTemplates.get(rpcConfigurationForTemplate);
                                currentIsValidCount.incrementAndGet();
                            }
                        }
                    });
                }
            }

            for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
                final AtomicInteger countOfValidBlockTemplate = countOfValidBlockTemplates.get(rpcConfiguration);
                final int validCount = countOfValidBlockTemplate.get();
                if (validCount > bestValidCount) {
                    bestRpcConfiguration = rpcConfiguration;
                    bestValidCount = validCount;
                }
            }
            if (bestValidCount < 1) {
                wasSuccessful = false;
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            wasSuccessful = false;
        }

        if (! wasSuccessful) {
            Logger.warn("No valid templates found.");
            return null;
        }

        final BlockTemplate blockTemplate = blockTemplates.get(bestRpcConfiguration);
        Logger.info("Block template from " + bestRpcConfiguration + " considered valid by " + bestValidCount + " nodes.");
        return blockTemplate;
    }

    public BlockTemplateManager(final NodeSelector nodeSelector) {
        _nodeSelector = nodeSelector;
    }

    public BlockTemplate getBlockTemplate() {
        final RpcConfiguration bestRpcConfiguration = _nodeSelector.selectBestNode();
        final BitBalancerRpcConnector bestBitcoinRpcConnector = bestRpcConfiguration.getBitcoinRpcConnector();

        final List<RpcConfiguration> rpcConfigurations = _nodeSelector.getNodes();

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();
        final BlockTemplate blockTemplate = bestBitcoinRpcConnector.getBlockTemplate();
        nanoTimer.stop();

        Logger.info("Block template acquired in " + nanoTimer.getMillisecondsElapsed() + "ms.");

        final Container<String> errorStringContainer = new Container<>();
        if (blockTemplate == null) {
            Logger.warn("Unable to get block template from " + bestRpcConfiguration + ": " + errorStringContainer.value);
            return _onGetBlockTemplateFailure();
        }

        final ChainHeight bestChainHeight = _nodeSelector.getBestChainHeight();

        final AtomicInteger invalidCount = new AtomicInteger(0);
        final AtomicInteger skipCount = new AtomicInteger(0);
        final AtomicInteger validCount = new AtomicInteger(0);
        try {
            final BatchRunner<RpcConfiguration> batchRunner = new BatchRunner<>(1, true);
            batchRunner.run(rpcConfigurations, new BatchRunner.Batch<RpcConfiguration>() {
                @Override
                public void run(final List<RpcConfiguration> rpcConfigurations) throws Exception {
                    final NanoTimer nanoTimer = new NanoTimer();

                    final RpcConfiguration rpcConfiguration = rpcConfigurations.get(0); // Workaround for non-specialization of BatchRunner of size 1.
                    final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();

                    final ChainHeight chainHeight = rpcConfiguration.getChainHeight();
                    final boolean isNodeBehind = bestChainHeight.isBetterThan(chainHeight);
                    if (isNodeBehind) {
                        Logger.debug("Skipping template validation template for " + rpcConfiguration + " with " + bestRpcConfiguration + " template; node is behind on ChainHeight: " + chainHeight);
                        skipCount.incrementAndGet();
                        return;
                    }

                    final Monitor monitor = bitcoinRpcConnector.getMonitor();
                    final Long maxTimeoutMs = rpcConfiguration.getMaxTimeoutMs();
                    if (maxTimeoutMs != null) {
                        monitor.setMaxDurationMs(maxTimeoutMs);
                    }

                    nanoTimer.start();
                    final Boolean isValid = bitcoinRpcConnector.validateBlockTemplate(blockTemplate, monitor);
                    nanoTimer.stop();

                    if (isValid == null) {
                        Logger.debug("Template validation not supported by " + rpcConfiguration + ".");
                        skipCount.incrementAndGet();
                        return;
                    }

                    if (! isValid) {
                        Logger.debug("Template considered invalid by: " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                        invalidCount.incrementAndGet();
                        return;
                    }

                    Logger.debug("Validated template from " + bestRpcConfiguration + " with " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                    validCount.incrementAndGet();
                }
            });
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        final Long blockHeight = blockTemplate.getBlockHeight();
        if (invalidCount.get() > 0) {
            Logger.warn("Block template for height " + blockHeight + " considered invalid by " + invalidCount.get() + " nodes.");
            return _onGetBlockTemplateFailure();
        }

        Logger.info("Block template for height " + blockHeight + " considered valid by all nodes.");
        return blockTemplate;
    }
}
