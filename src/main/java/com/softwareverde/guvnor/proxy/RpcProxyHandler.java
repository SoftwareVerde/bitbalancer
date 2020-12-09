package com.softwareverde.guvnor.proxy;

import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;

public class RpcProxyHandler implements Servlet {
    protected final NodeSelector _nodeSelector;

    public RpcProxyHandler(final NodeSelector nodeSelector) {
        _nodeSelector = nodeSelector;
    }

    @Override
    public Response onRequest(final Request request) {
        final RpcConfiguration rpcConfiguration = _nodeSelector.selectBestNode();
        if (rpcConfiguration == null) {
            final Response errorResponse = new Response();
            errorResponse.setCode(Response.Codes.SERVER_ERROR);
            errorResponse.setContent("No viable node connection found.");
            return errorResponse;
        }

        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
        return bitcoinRpcConnector.handleRequest(request);
    }
}
