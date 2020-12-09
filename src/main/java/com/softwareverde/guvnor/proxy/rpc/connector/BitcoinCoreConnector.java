package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.guvnor.BitcoinCoreUtil;
import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.guvnor.proxy.rpc.RpcCredentials;
import com.softwareverde.http.HttpRequest;
import com.softwareverde.http.HttpResponse;
import com.softwareverde.http.server.servlet.request.Headers;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.util.Util;

import java.util.Collection;

public class BitcoinCoreConnector implements BitcoinRpcConnector {
    protected final BitcoinNodeAddress _bitcoinNodeAddress;
    protected final RpcCredentials _rpcCredentials;

    public BitcoinCoreConnector(final BitcoinNodeAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
        _bitcoinNodeAddress = bitcoinNodeAddress;
        _rpcCredentials = rpcCredentials;
    }

    @Override
    public String getHost() {
        return _bitcoinNodeAddress.getHost();
    }

    @Override
    public Integer getPort() {
        return _bitcoinNodeAddress.getPort();
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

            { // Set request headers...
                final Headers headers = request.getHeaders();
                for (final String key : headers.getHeaderNames()) {
                    final Collection<String> values = headers.getHeader(key);
                    for (final String value : values) {
                        webRequest.setHeader(key, value);
                    }
                }

                if (_rpcCredentials != null) {
                    final String key = _rpcCredentials.getAuthorizationHeaderKey();
                    final String value = _rpcCredentials.getAuthorizationHeaderValue();
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
            response.setContent(BitcoinCoreUtil.getErrorMessage(proxiedResponseCode));
        }
        return response;
    }
}
