package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;

public interface BitcoinRpcConnector {
    String getHost();
    Integer getPort();
    Response handleRequest(Request request);
}
