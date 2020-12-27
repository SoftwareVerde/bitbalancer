package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.node.selector.NodeSelector;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.guvnor.proxy.rpc.connector.BlockTemplate;
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

        try {
            final BatchRunner<RpcConfiguration> batchRunner = new BatchRunner<>(1, true);
            batchRunner.run(rpcConfigurations, new BatchRunner.Batch<RpcConfiguration>() {
                @Override
                public void run(final List<RpcConfiguration> rpcConfigurations) {
                    final RpcConfiguration rpcConfiguration = rpcConfigurations.get(0); // Workaround for non-specialization of BatchRunner of size 1.

                    final NanoTimer nanoTimer = new NanoTimer();
                    final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();

                    nanoTimer.start();
                    final BlockTemplate blockTemplate = bitcoinRpcConnector.getBlockTemplate();
                    nanoTimer.stop();

                    if (blockTemplate != null) {
                        blockTemplates.put(rpcConfiguration, blockTemplate);
                    }

                    countOfValidBlockTemplates.put(rpcConfiguration, new AtomicInteger(0));

                    if (blockTemplate == null) {
                        Logger.debug("Failed to obtain template from " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                    }
                    else {
                        Logger.info("Obtained template from " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                    }
                }
            });

            for (final RpcConfiguration rpcConfigurationForTemplate : rpcConfigurations) {
                final BlockTemplate blockTemplate = blockTemplates.get(rpcConfigurationForTemplate);
                if (blockTemplate == null) { continue; }

                batchRunner.run(rpcConfigurations, new BatchRunner.Batch<RpcConfiguration>() {
                    @Override
                    public void run(final List<RpcConfiguration> rpcConfigurationsForValidation) {
                        final NanoTimer nanoTimer = new NanoTimer();

                        final RpcConfiguration rpcConfigurationForValidation = rpcConfigurationsForValidation.get(0); // Workaround for non-specialization of BatchRunner of size 1.
                        final BitcoinRpcConnector bitcoinRpcConnectorForValidation = rpcConfigurationForValidation.getBitcoinRpcConnector();

                        nanoTimer.start();
                        final Boolean isValid = bitcoinRpcConnectorForValidation.validateBlockTemplate(blockTemplate);
                        nanoTimer.stop();

                        if (isValid != null) {
                            Logger.debug("Validated template from " + rpcConfigurationForTemplate + " with " + rpcConfigurationForValidation + " in " + nanoTimer.getMillisecondsElapsed() + "ms. (" + (isValid ? "VALID" : "INVALID") + ")");
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
        final BitcoinRpcConnector bestBitcoinRpcConnector = bestRpcConfiguration.getBitcoinRpcConnector();

        final List<RpcConfiguration> rpcConfigurations = _nodeSelector.getNodes();

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();
        final BlockTemplate blockTemplate = bestBitcoinRpcConnector.getBlockTemplate();
        nanoTimer.stop();

        Logger.info("Block template acquired in " + nanoTimer.getMillisecondsElapsed() + "ms.");

        final int nodeCount = rpcConfigurations.getCount();
        final Container<String> errorStringContainer = new Container<>();
        if (blockTemplate == null) {
            Logger.warn("Unable to get block template from " + bestRpcConfiguration + ": " + errorStringContainer.value);
            return _onGetBlockTemplateFailure();
        }

        final AtomicInteger validCount = new AtomicInteger(0);
        try {
            final BatchRunner<RpcConfiguration> batchRunner = new BatchRunner<>(1, true);
            batchRunner.run(rpcConfigurations, new BatchRunner.Batch<RpcConfiguration>() {
                @Override
                public void run(final List<RpcConfiguration> rpcConfigurations) throws Exception {
                    final NanoTimer nanoTimer = new NanoTimer();

                    final RpcConfiguration rpcConfiguration = rpcConfigurations.get(0); // Workaround for non-specialization of BatchRunner of size 1.
                    final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();

                    nanoTimer.start();
                    final Boolean isValid = bitcoinRpcConnector.validateBlockTemplate(blockTemplate);
                    nanoTimer.stop();

                    if (isValid != null) {
                        Logger.debug("Validated template from " + bestRpcConfiguration + " with " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms. (" + (isValid ? "VALID" : "INVALID") + ")");
                    }
                    else {
                        Logger.debug("Template validation not supported by " + rpcConfiguration + ".");
                    }

                    if (Util.coalesce(isValid, true)) {
                        validCount.incrementAndGet();
                    }
                    else {
                        Logger.debug("Template considered invalid by: " + rpcConfiguration);
                    }
                }
            });
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        final Long blockHeight = blockTemplate.getBlockHeight();
        final int invalidCount = (nodeCount - validCount.get());
        if (invalidCount > 0) {
            Logger.warn("Block template for height " + blockHeight + " considered invalid by " + invalidCount + " nodes.");
            return _onGetBlockTemplateFailure();
        }

        Logger.info("Block template for height " + blockHeight + " considered valid by all nodes.");
        return blockTemplate;
    }
}
