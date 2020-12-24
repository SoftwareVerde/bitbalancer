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
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcProxyHandler implements Servlet {
    public enum Method {
        GET_BLOCK_TEMPLATE("getblocktemplate"),
        GET_BLOCK_TEMPLATE_LIGHT("getblocktemplatelight");

        protected final String _value;

        Method(final String value) {
            _value = value;
        }

        public String getValue() { return _value; }

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

        final String rawResponse = StringUtil.bytesToString(response.getContent());
        final Json responseJson = (Json.isJson(rawResponse) ? Json.parse(rawResponse) : new Json());

        final int nodeCount = rpcConfigurations.getCount();

        final Container<String> errorStringContainer = new Container<>();
        final Boolean isSuccessfulResponse = _isSuccessfulResponse(response, responseJson, errorStringContainer);
        if (! isSuccessfulResponse) {
            Logger.debug("Received error from " + bestRpcConfiguration + ": " + errorStringContainer.value);
            return response;
        }

        final Json resultJson = responseJson.get("result");
        final Block blockTemplate = _assembleBlockTemplate(resultJson);
        if (blockTemplate == null) {
            Logger.warn("Unable to assemble block template.");
            Logger.debug(responseJson);

            return _onGetBlockTemplateFailure(request);
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

        final Sha256Hash previousBlockHash = blockTemplate.getPreviousBlockHash();
        final int invalidCount = (nodeCount - validCount.get());
        if (invalidCount > 0) {
            Logger.warn("Block template considered invalid by " + invalidCount + " nodes.");
            return _onGetBlockTemplateFailure(request);
        }

        Logger.info("Block template for previous block " + previousBlockHash + " considered valid by all nodes.");
        return response;
    }

    protected Boolean _isSuccessfulResponse(final Response response, final Json preParsedResponse) {
        return _isSuccessfulResponse(response, preParsedResponse, new Container<String>());
    }

    protected Boolean _isSuccessfulResponse(final Response response, final Json preParsedResponse, final Container<String> errorStringContainer) {
        errorStringContainer.value = null;
        if (response == null) { return false; }

        if (! Util.areEqual(Response.Codes.OK, response.getCode())) {
            return false;
        }

        final Json responseJson;
        if (preParsedResponse != null) {
            responseJson = preParsedResponse;
        }
        else {
            final String rawResponse = StringUtil.bytesToString(response.getContent());
            responseJson = (Json.isJson(rawResponse) ? Json.parse(rawResponse) : new Json());
        }

        final String errorString = responseJson.getString("error");
        if (! Util.isBlank(errorString)) {
            errorStringContainer.value = errorString;
            return false;
        }

        return true;
    }

    public RpcProxyHandler(final NodeSelector nodeSelector) {
        _nodeSelector = nodeSelector;
    }

    @Override
    public Response onRequest(final Request request) {
        final String rawMethod;
        final Method method;
        { // Parse the method from the request object...
            final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());
            final Json requestJson = Json.parse(StringUtil.bytesToString(rawPostData.unwrap()));
            rawMethod = requestJson.getString("method");
            method = Method.fromString(rawMethod);
        }

        Logger.debug("Routing: " + rawMethod);

        if (method == Method.GET_BLOCK_TEMPLATE) {
            final RpcConfiguration rpcConfiguration = _nodeSelector.selectBestNode();
            return _onGetBlockTemplateRequest(rpcConfiguration, request);
        }

        final MutableList<RpcConfiguration> attemptedConfigurations = new MutableList<>();
        Response defaultResponse = null;
        while (true) {
            final NanoTimer nanoTimer = new NanoTimer();

            final RpcConfiguration rpcConfiguration = _nodeSelector.selectBestNode(attemptedConfigurations);
            if (rpcConfiguration == null) { break; } // Break if no viable nodes remain...

            // Prevent trying the same node multiple times with the same request.
            attemptedConfigurations.add(rpcConfiguration);

            final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
            Logger.debug("Routing: " + rawMethod + " to " + rpcConfiguration + ".");

            nanoTimer.start();
            final Response response = bitcoinRpcConnector.handleRequest(request);
            nanoTimer.stop();

            final Container<String> errorStringContainer = new Container<>();
            final Boolean responseWasSuccessful = _isSuccessfulResponse(response, null, errorStringContainer);
            Logger.debug("Response received for " + rawMethod + " from " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");

            if (responseWasSuccessful) {
                return response;
            }

            Logger.debug("Received non-ok response for " + rawMethod + ": " + errorStringContainer.value + ", trying next node.");
            if (defaultResponse == null) {
                defaultResponse = response;
            }
        }

        if (defaultResponse != null) {
            return defaultResponse;
        }

        final Response errorResponse = new Response();
        errorResponse.setCode(Response.Codes.SERVER_ERROR);
        errorResponse.setContent("No viable node connection found.");
        return errorResponse;
    }
}
