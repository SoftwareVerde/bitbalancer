package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.guvnor.AbcUtil;
import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.http.HttpRequest;
import com.softwareverde.http.HttpResponse;
import com.softwareverde.http.server.servlet.request.Headers;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.util.Util;

import java.util.Collection;

public class BitcoinCoreConnector implements BitcoinRpcConnector {
    protected final BitcoinNodeAddress _bitcoinNodeAddress;

    public BitcoinCoreConnector(final BitcoinNodeAddress bitcoinNodeAddress) {
        _bitcoinNodeAddress = bitcoinNodeAddress;
    }

    @Override
    public Response handleRequest(final Request request) {
        final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());

        final Integer proxiedResponseCode;
        final ByteArray proxiedResult;
        { // Proxy the request to the target node...
            final HttpRequest webRequest = new HttpRequest();
            webRequest.setUrl("http" + (_bitcoinNodeAddress.isSecure() ? "s" : "") + "://" + _bitcoinNodeAddress.getHost() + ":" + _bitcoinNodeAddress.getPort());
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

            proxiedResponseCode = (proxiedResponse != null ? proxiedResponse.getResponseCode() : null);
            proxiedResult = (proxiedResponse != null ? proxiedResponse.getRawResult() : null);
        }

        final Response response = new Response();
        response.setCode(Util.coalesce(proxiedResponseCode, Response.Codes.SERVER_ERROR));
        if (proxiedResult != null) {
            response.setContent(proxiedResult.getBytes());
        }
        else {
            response.setContent(AbcUtil.getErrorMessage(proxiedResponseCode));
        }
        return response;
    }
}
