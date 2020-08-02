package com.softwareverde.guvnor;

import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.StringUtil;

public class Main {
    protected static Main INSTANCE = null;

    public static class Defaults {
        public static final Integer RPC_PORT = 8332;
    }

    protected final HttpServer _httpServer;

    public static void main(final String[] arguments) {
        if (Main.INSTANCE == null) {
            Main.INSTANCE = new Main(arguments);
            Main.INSTANCE.run();
        }
    }

    protected Main(final String[] arguments) {
        Logger.setLogLevel(LogLevel.ON);

        final Endpoint endpoint = new Endpoint(new Servlet() {
            @Override
            public Response onRequest(final Request request) {
                final String postData = StringUtil.bytesToString(request.getRawPostData());
                final Json json = Json.parse(postData);

                Logger.trace(json.toFormattedString(2));

                final Response response = new Response();
                response.setCode(Response.Codes.BAD_REQUEST);
                response.setContent(new byte[0]);
                return response;
            }
        });
        endpoint.setPath("/");
        endpoint.setStrictPathEnabled(true);

        _httpServer = new HttpServer();
        _httpServer.setPort(Defaults.RPC_PORT);
        _httpServer.enableEncryption(false);
        _httpServer.redirectToTls(false);
        _httpServer.addEndpoint(endpoint);
    }

    public void run() {
        _httpServer.start();
        Logger.debug("Server started.");

        while (! Thread.interrupted()) {
            try {
                Thread.sleep(10000L);
            }
            catch (final Exception exception) {
                break;
            }
        }

        _httpServer.stop();
        Logger.debug("Server stopped.");
    }
}
