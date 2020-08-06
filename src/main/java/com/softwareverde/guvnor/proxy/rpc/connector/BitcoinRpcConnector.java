package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;

public interface BitcoinRpcConnector {
    Response handleRequest(Request request);
}
