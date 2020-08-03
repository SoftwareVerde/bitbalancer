package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.guvnor.AbcUtil;
import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.http.HttpRequest;
import com.softwareverde.http.HttpResponse;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Headers;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;

import java.util.Collection;

public class RpcProxyHandler implements Servlet {
    protected final NodeSelector _nodeSelector;

    public RpcProxyHandler(final NodeSelector nodeSelector) {
        _nodeSelector = nodeSelector;
    }

    @Override
    public Response onRequest(final Request request) {
        final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());

        final BitcoinNodeAddress bitcoinNodeAddress = _nodeSelector.selectBestNode();

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
            response.setContent(AbcUtil.getErrorMessage(proxiedResponseCode));
        }
        return response;
    }
}
