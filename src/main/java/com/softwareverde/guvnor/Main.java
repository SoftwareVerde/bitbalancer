package com.softwareverde.guvnor;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.http.HttpRequest;
import com.softwareverde.http.HttpResponse;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Headers;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

import java.util.Collection;

public class Main {
    protected static Main INSTANCE = null;

    public static class Defaults {
        public static final Integer RPC_PORT = 8332;
    }

    public static String getErrorMessage(final Integer responseCode) {
        if (responseCode == null) {
            return "send http request failed";
        }

        final int httpCode = responseCode;
        if (httpCode == 0) {
            return "couldn't connect to server";
        }
        else if (httpCode == Response.Codes.NOT_AUTHORIZED) {
            return "Authorization failed: Incorrect rpcuser or rpcpassword";
        }
        else if ( (httpCode >= 400) && (httpCode != Response.Codes.BAD_REQUEST) && (httpCode != Response.Codes.NOT_FOUND) && (httpCode != Response.Codes.SERVER_ERROR) ) {
            return ("server returned HTTP error " + responseCode);
        }
        else {
            return "no response from server";
        }
    }

    protected final HttpServer _httpServer;
    protected final MutableList<BitcoinNodeAddress> _bitcoinNodeAddresses = new MutableList<BitcoinNodeAddress>();

    public static void main(final String[] arguments) {
        if (Main.INSTANCE == null) {
            Main.INSTANCE = new Main(arguments);
            Main.INSTANCE.run();
        }
    }

    protected Main(final String[] arguments) {
        Logger.setLogLevel(LogLevel.ON);

        { // TODO: Read from arguments...
            _bitcoinNodeAddresses.add(new BitcoinNodeAddress("btc.sv.net", 8332));
        }

        final Endpoint endpoint = new Endpoint(new Servlet() {
            @Override
            public Response onRequest(final Request request) {
                final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());

                final BitcoinNodeAddress bitcoinNodeAddress = _bitcoinNodeAddresses.get(0);

                final Integer proxiedResponseCode;
                final ByteArray proxiedResult;
                { // Proxy the request to the target node...
                    final HttpRequest webRequest = new HttpRequest();
                    webRequest.setUrl("http://" + bitcoinNodeAddress.host + ":" + bitcoinNodeAddress.port);
                    webRequest.setAllowWebSocketUpgrade(false);
                    webRequest.setFollowsRedirects(false);
                    webRequest.setValidateSslCertificates(false);
                    webRequest.setRequestData(rawPostData);
                    webRequest.setMethod(request.getMethod());
                    final Headers headers = request.getHeaders();
                    for (final String key : headers.getHeaderNames()) {
                        final Collection<String> values = headers.getHeader(key);
                        for (final String value : values) {
                            webRequest.setHeader(key, value);
                        }
                    }
                    final HttpResponse proxiedResponse = webRequest.execute();

                    proxiedResponseCode = proxiedResponse.getResponseCode();
                    proxiedResult = proxiedResponse.getRawResult();
                }

                final Response response = new Response();
                response.setCode(proxiedResponseCode);
                if (proxiedResult != null) {
                    response.setContent(proxiedResult.getBytes());
                }
                else {
                    response.setContent(Main.getErrorMessage(proxiedResponseCode));
                }
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
