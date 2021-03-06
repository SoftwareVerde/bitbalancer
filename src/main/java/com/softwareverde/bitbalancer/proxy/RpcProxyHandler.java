package com.softwareverde.bitbalancer.proxy;

import com.softwareverde.bitbalancer.proxy.node.selector.NodeSelector;
import com.softwareverde.bitbalancer.proxy.rpc.RpcConfiguration;
import com.softwareverde.bitbalancer.proxy.rpc.connector.BitBalancerRpcConnector;
import com.softwareverde.bitbalancer.proxy.zmq.ZmqConfiguration;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.bitcoin.rpc.core.zmq.ZmqMessageTypeConverter;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.atomic.AtomicInteger;

public class RpcProxyHandler implements Servlet {
    public enum Method {
        GET_BLOCK_TEMPLATE("getblocktemplate"),
        GET_BLOCK_TEMPLATE_LIGHT("getblocktemplatelight"),
        SUBMIT_BLOCK("submitblock"),
        GET_ZMQ_NOTIFICATIONS("getzmqnotifications");

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

    protected final ZmqConfiguration _zmqConfiguration;
    protected final NodeSelector _nodeSelector;
    protected final BlockTemplateManager _blockTemplateManager;

    protected Boolean _submitBlockToAllNodes(final Block block) {
        final AtomicInteger acceptCount = new AtomicInteger(0);
        final AtomicInteger rejectCount = new AtomicInteger(0);

        final Sha256Hash blockHash = block.getHash();

        try {
            final List<RpcConfiguration> rpcConfigurations = _nodeSelector.getNodes();
            final BatchRunner<RpcConfiguration> batchRunner = new BatchRunner<>(1, true);
            batchRunner.run(rpcConfigurations, new BatchRunner.Batch<RpcConfiguration>() {
                @Override
                public void run(final List<RpcConfiguration> batchItems) throws Exception {
                    final RpcConfiguration rpcConfiguration = batchItems.get(0);
                    final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();

                    final Boolean wasSuccess = bitcoinRpcConnector.submitBlock(block);
                    if (wasSuccess) {
                        Logger.info("Block " + blockHash + " accepted by " + rpcConfiguration + ".");
                        acceptCount.incrementAndGet();
                    }
                    else {
                        Logger.info("Block " + blockHash + " rejected by " + rpcConfiguration + ".");
                        rejectCount.incrementAndGet();
                    }
                }
            });
        }
        catch (final Exception exception) {
            Logger.warn("Error submitting block.", exception);
        }

        Logger.info("Block " + blockHash + " accepted by " + acceptCount.get() + " nodes, rejected by " + rejectCount.get() + " nodes.");
        return (acceptCount.get() > 0);
    }

    public RpcProxyHandler(final NodeSelector nodeSelector, final BlockTemplateManager blockTemplateManager, final ZmqConfiguration zmqConfiguration) {
        _nodeSelector = nodeSelector;
        _blockTemplateManager = blockTemplateManager;
        _zmqConfiguration = zmqConfiguration;
    }

    @Override
    public Response onRequest(final Request request) {
        final Long requestId;
        final String rawMethod;
        final Method method;
        final Json requestJson;
        { // Parse the method from the request object...
            final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());
            requestJson = Json.parse(StringUtil.bytesToString(rawPostData.unwrap()));
            requestId = requestJson.getLong("id");
            rawMethod = requestJson.getString("method");
            method = Method.fromString(rawMethod);
        }

        Logger.debug("Routing: " + rawMethod);

        if (method == Method.GET_BLOCK_TEMPLATE) {
            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();
            final BlockTemplate blockTemplate = _blockTemplateManager.getBlockTemplate();
            nanoTimer.stop();
            Logger.info("Block template acquired in " + nanoTimer.getMillisecondsElapsed() + "ms.");

            final Response response = new Response();

            final Json responseJson = new Json();
            responseJson.put("id", requestId);

            if (blockTemplate == null) {
                response.setCode(Response.Codes.SERVER_ERROR);
                responseJson.put("code", Integer.MIN_VALUE);
                responseJson.put("error", "No valid templates found.");
                responseJson.put("result", null);
            }
            else {
                response.setCode(Response.Codes.OK);
                responseJson.put("error", null);
                responseJson.put("result", blockTemplate);
            }

            response.setContent(responseJson.toString());
            return response;
        }
        else if (method == Method.SUBMIT_BLOCK) {
            Logger.debug("Block submitted.");
            final String resultString;
            {
                final Json paramsJson = requestJson.get("params");
                final BlockInflater blockInflater = new BlockInflater();
                final String blockDataString = paramsJson.getString(0); // hexdata
                // final String dummyData = paramsJson.getString(1); // dummy
                final ByteArray blockData = ByteArray.fromHexString(blockDataString);
                final Block block = (blockData != null ? blockInflater.fromBytes(blockData) : null);

                if (block == null) {
                    Logger.debug("Block failed to decode: " + blockDataString);
                    resultString = "Block decode failed";
                }
                else {
                    final Boolean wasValid = _submitBlockToAllNodes(block);
                    resultString = (wasValid ? null : "rejected");
                }
            }

            final Response response = new Response();

            final Json responseJson = new Json();
            responseJson.put("id", requestId);

            response.setCode(Response.Codes.OK);
            responseJson.put("error", null);
            responseJson.put("result", resultString);

            Logger.debug("Submit Block Result: " + resultString);

            response.setContent(responseJson.toString());
            return response;
        }
        else if (method == Method.GET_ZMQ_NOTIFICATIONS) {
            final String resultString;
            {
                final Json zmqEndpointsJson = new Json(true);
                if (_zmqConfiguration != null) {
                    for (final RpcNotificationType notificationType : _zmqConfiguration.getSupportedMessageTypes()) {
                        final Integer listenPort = _zmqConfiguration.getPort(notificationType);
                        final Json endpointConfiguration = new Json(false);
                        endpointConfiguration.put("type", ZmqMessageTypeConverter.toPublishString(notificationType));
                        endpointConfiguration.put("address", "tcp://0.0.0.0:" + listenPort); // TODO: Determine if the address should be 0.0.0.0 or 127.0.0.1 or other...

                        zmqEndpointsJson.add(endpointConfiguration);
                    }
                }
                resultString = zmqEndpointsJson.toString();
            }

            final Response response = new Response();

            final Json responseJson = new Json();
            responseJson.put("id", requestId);

            response.setCode(Response.Codes.OK);
            responseJson.put("error", null);
            responseJson.put("result", resultString);

            response.setContent(responseJson.toString());
            return response;
        }

        final MutableList<RpcConfiguration> attemptedConfigurations = new MutableList<>();
        Response defaultResponse = null;
        while (true) {
            final NanoTimer nanoTimer = new NanoTimer();

            final RpcConfiguration rpcConfiguration = _nodeSelector.selectBestNode(attemptedConfigurations);
            if (rpcConfiguration == null) { break; } // Break if no viable nodes remain...

            // Prevent trying the same node multiple times with the same request.
            attemptedConfigurations.add(rpcConfiguration);

            final BitBalancerRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
            Logger.debug("Routing: " + rawMethod + " to " + rpcConfiguration + ".");

            nanoTimer.start();
            final Response response = bitcoinRpcConnector.handleRequest(request);
            nanoTimer.stop();

            final Container<String> errorStringContainer = new Container<>();
            final Boolean responseWasSuccessful = bitcoinRpcConnector.isSuccessfulResponse(response, errorStringContainer);
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
