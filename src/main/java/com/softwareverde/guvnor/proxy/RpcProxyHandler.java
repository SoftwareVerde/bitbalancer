package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcProxyHandler implements Servlet {
    public enum Method {
        GET_BLOCK_TEMPLATE("getblocktemplate");

        protected final String _value;
        public String getValue() { return _value; }

        Method(final String value) {
            _value = value;
        }

        public static Method fromString(final String value) {
            for (final Method method : Method.values()) {
                final String methodValue = method.getValue();
                if (methodValue.equalsIgnoreCase(value)) {
                    return method;
                }
            }

            return null;
        }
    }

    protected final NodeSelector _nodeSelector;

    protected Block _assembleBlockTemplate(final Json blockTemplateJson) {
        final AddressInflater addressInflater = new AddressInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final MutableBlock block = new MutableBlock();
        block.setVersion(blockTemplateJson.getLong("version"));
        block.setDifficulty(Difficulty.decode(ByteArray.fromHexString(blockTemplateJson.getString("bits"))));
        block.setPreviousBlockHash(Sha256Hash.fromHexString(blockTemplateJson.getString("previousblockhash")));
        block.setTimestamp(blockTemplateJson.getLong("mintime"));
        block.setNonce(0L);

        final Transaction coinbaseTransaction;
        { // Generate the Coinbase
            final Long coinbaseAmount = blockTemplateJson.getLong("coinbasevalue");
            final Long blockHeight = blockTemplateJson.getLong("height");

            final String coinbaseMessage = "0000000000000000000000000000000000000000000000000000000000000000"; // NOTE: CoinbaseMessage must be at least 11 bytes or the transaction will not satisfy the minimum transaction size (100 bytes).
            final PrivateKey privateKey = PrivateKey.fromHexString("0000000000000000000000000000000000000000000000000000000000000001");
            final Address address = addressInflater.fromPrivateKey(privateKey);
            coinbaseTransaction = transactionInflater.createCoinbaseTransaction(blockHeight, coinbaseMessage, address, coinbaseAmount);
        }
        block.addTransaction(coinbaseTransaction);

        final Json transactionsJson = blockTemplateJson.get("transactions");
        for (int i = 0; i < transactionsJson.length(); ++i) {
            final Json transactionJson = transactionsJson.get(i);
            final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionJson.getString("data")));
            block.addTransaction(transaction);
        }

        return block;
    }

    protected Response _onGetBlockTemplateFailure(final Request request) {
        boolean wasSuccessful = true;
        int bestValidCount = 0;
        RpcConfiguration bestRpcConfiguration = null;

        final ConcurrentHashMap<RpcConfiguration, Response> blockTemplateResponses = new ConcurrentHashMap<>();
        final ConcurrentHashMap<RpcConfiguration, Block> blockTemplates = new ConcurrentHashMap<>();
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
                    final Response response = bitcoinRpcConnector.handleRequest(request);
                    nanoTimer.stop();

                    final Block blockTemplate;
                    final String rawResponse = StringUtil.bytesToString(response.getContent());
                    final Json responseJson = (Json.isJson(rawResponse) ? Json.parse(rawResponse) : new Json());
                    final String errorString = responseJson.getString("error");
                    if (! Util.isBlank(errorString)) {
                        Logger.debug("Received error from " + rpcConfiguration + ": " + errorString);
                        blockTemplate = null;
                    }
                    else {
                        final Json resultJson = responseJson.get("result");
                        Block assembledBlock = null;
                        try {
                            assembledBlock = _assembleBlockTemplate(resultJson);
                        }
                        catch (final Exception exception) {
                            Logger.debug("Unable to obtain template from " + rpcConfiguration + ".", exception);
                        }
                        blockTemplate = assembledBlock;
                    }

                    blockTemplateResponses.put(rpcConfiguration, response);
                    if (blockTemplate != null) {
                        blockTemplates.put(rpcConfiguration, blockTemplate);
                    }
                    countOfValidBlockTemplates.put(rpcConfiguration, new AtomicInteger(0));

                    Logger.debug("Obtained template from " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                }
            });

            for (final RpcConfiguration rpcConfigurationForTemplate : rpcConfigurations) {
                final Block blockTemplate = blockTemplates.get(rpcConfigurationForTemplate);
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

                        Logger.debug("Validated template from " + rpcConfigurationForTemplate + " with " + rpcConfigurationForValidation + " in " + nanoTimer.getMillisecondsElapsed() + "ms. (" + (isValid ? "VALID" : "INVALID") + ")");

                        if (isValid) {
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

            final Json errorJson = new Json();
            errorJson.put("error", "No valid templates found.");
            errorJson.put("code", Integer.MIN_VALUE);

            final Response response = new Response();
            response.setCode(Response.Codes.SERVER_ERROR);
            response.setContent(errorJson.toString());
            return response;
        }

        Logger.info("Block template from " + bestRpcConfiguration + " considered valid by " + bestValidCount + " nodes.");
        return blockTemplateResponses.get(bestRpcConfiguration);
    }

    protected Response _onGetBlockTemplateRequest(final RpcConfiguration bestRpcConfiguration, final Request request) {
        final List<RpcConfiguration> rpcConfigurations = _nodeSelector.getNodes();
        final BitcoinRpcConnector bestBitcoinRpcConnector = bestRpcConfiguration.getBitcoinRpcConnector();
        final Response response = bestBitcoinRpcConnector.handleRequest(request);
        final Json responseJson = Json.parse(StringUtil.bytesToString(response.getContent()));

        final int nodeCount = rpcConfigurations.getCount();

        final String errorString = responseJson.getString("error");
        if (! Util.isBlank(errorString)) {
            Logger.debug("Received error from " + bestRpcConfiguration + ": " + errorString);
            return response;
        }

        final Json resultJson = responseJson.get("result");
        final Block blockTemplate = _assembleBlockTemplate(resultJson);
        if (blockTemplate == null) {
            Logger.warn("Unable to assemble block template.");
            Logger.warn(responseJson);

            return _onGetBlockTemplateFailure(request);
        }

        int validCount = 0;
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
            final Boolean isValid = bitcoinRpcConnector.validateBlockTemplate(blockTemplate);
            if (isValid) {
                validCount += 1;
            }
            else {
                Logger.debug("Template considered invalid by: " + rpcConfiguration);
            }
        }

        final Sha256Hash previousBlockHash = blockTemplate.getPreviousBlockHash();
        final int invalidCount = (nodeCount - validCount);
        if (invalidCount > 0) {
            Logger.warn("Block template considered invalid by " + invalidCount + " nodes.");
            return _onGetBlockTemplateFailure(request);
        }

        Logger.info("Block template for height " + previousBlockHash + " considered valid by all nodes.");
        return response;
    }

    public RpcProxyHandler(final NodeSelector nodeSelector) {
        _nodeSelector = nodeSelector;
    }

    @Override
    public Response onRequest(final Request request) {
        final Method method;
        {
            final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());
            final Json requestJson = Json.parse(StringUtil.bytesToString(rawPostData.unwrap()));
            final String methodString = requestJson.getString("method");
            method = Method.fromString(methodString);
        }

        final RpcConfiguration rpcConfiguration = _nodeSelector.selectBestNode();
        if (rpcConfiguration == null) {
            final Response errorResponse = new Response();
            errorResponse.setCode(Response.Codes.SERVER_ERROR);
            errorResponse.setContent("No viable node connection found.");
            return errorResponse;
        }

        if (method == Method.GET_BLOCK_TEMPLATE) {
            return _onGetBlockTemplateRequest(rpcConfiguration, request);
        }

        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
        return bitcoinRpcConnector.handleRequest(request);
    }
}
