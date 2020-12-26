package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.bytearray.MutableByteArray;
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
    protected final BlockTemplateManager _blockTemplateManager;

    public RpcProxyHandler(final NodeSelector nodeSelector) {
        _nodeSelector = nodeSelector;
        _blockTemplateManager = new BlockTemplateManager(nodeSelector);
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
            final BlockTemplate blockTemplate = _blockTemplateManager.getBlockTemplate(rpcConfiguration);

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
