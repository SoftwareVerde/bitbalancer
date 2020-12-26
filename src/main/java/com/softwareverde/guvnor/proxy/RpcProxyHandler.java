package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.guvnor.proxy.node.selector.NodeSelector;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.guvnor.proxy.rpc.connector.BlockTemplate;
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

    protected Response _onGetBlockTemplateFailure(final Long requestId) {
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
                        Logger.debug("Obtained template from " + rpcConfiguration + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
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

            final Json responseJson = new Json();
            responseJson.put("id", requestId);
            responseJson.put("code", Integer.MIN_VALUE);
            responseJson.put("error", "No valid templates found.");
            responseJson.put("result", null);

            final Response response = new Response();
            response.setCode(Response.Codes.SERVER_ERROR);
            response.setContent(responseJson.toString());
            return response;
        }

        final BlockTemplate blockTemplate = blockTemplates.get(bestRpcConfiguration);
        Logger.info("Block template from " + bestRpcConfiguration + " considered valid by " + bestValidCount + " nodes.");

        final Json responseJson = new Json();
        responseJson.put("id", requestId);
        responseJson.put("error", null);
        responseJson.put("result", blockTemplate);

        final Response response = new Response();
        response.setCode(Response.Codes.OK);
        response.setContent(responseJson.toString());
        return response;
    }

    protected Response _onGetBlockTemplateRequest(final Long requestId, final RpcConfiguration bestRpcConfiguration) {
        final List<RpcConfiguration> rpcConfigurations = _nodeSelector.getNodes();
        final BitcoinRpcConnector bestBitcoinRpcConnector = bestRpcConfiguration.getBitcoinRpcConnector();

        final BlockTemplate blockTemplate = bestBitcoinRpcConnector.getBlockTemplate();

        final int nodeCount = rpcConfigurations.getCount();
        final Container<String> errorStringContainer = new Container<>();
        if (blockTemplate == null) {
            Logger.warn("Unable to get block template from " + bestRpcConfiguration + ": " + errorStringContainer.value);
            return _onGetBlockTemplateFailure(requestId);
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
            return _onGetBlockTemplateFailure(requestId);
        }

        Logger.info("Block template for height " + blockHeight + " considered valid by all nodes.");

        final Json responseJson = new Json();
        responseJson.put("id", requestId);
        responseJson.put("error", null);
        responseJson.put("result", blockTemplate);

        final Response response = new Response();
        response.setCode(Response.Codes.OK);
        response.setContent(responseJson.toString());
        return response;
    }

    public RpcProxyHandler(final NodeSelector nodeSelector) {
        _nodeSelector = nodeSelector;
    }

    @Override
    public Response onRequest(final Request request) {
        final Long requestId;
        final String rawMethod;
        final Method method;
        { // Parse the method from the request object...
            final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());
            final Json requestJson = Json.parse(StringUtil.bytesToString(rawPostData.unwrap()));
            requestId = requestJson.getLong("id");
            rawMethod = requestJson.getString("method");
            method = Method.fromString(rawMethod);
        }

        Logger.debug("Routing: " + rawMethod);

        if (method == Method.GET_BLOCK_TEMPLATE) {
            final RpcConfiguration rpcConfiguration = _nodeSelector.selectBestNode();
            return _onGetBlockTemplateRequest(requestId, rpcConfiguration);
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
            final Boolean responseWasSuccessful = bitcoinRpcConnector.isSuccessfulResponse(response, null, errorStringContainer);
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
