package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.guvnor.BitcoinCoreUtil;
import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.RpcCredentials;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.HttpRequest;
import com.softwareverde.http.HttpResponse;
import com.softwareverde.http.server.servlet.request.Headers;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class BitcoinCoreRpcConnector implements BitcoinRpcConnector {
    protected final AtomicInteger _nextRequestId = new AtomicInteger(1);
    protected final BitcoinNodeAddress _bitcoinNodeAddress;
    protected final RpcCredentials _rpcCredentials;

    protected String _toString() {
        return (this.getHost() + ":" + this.getPort());
    }

    public BitcoinCoreRpcConnector(final BitcoinNodeAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
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

    @Override
    public ChainHeight getChainHeight() {
        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", _nextRequestId.getAndIncrement());
            json.put("method", "getblockchaininfo");

            { // Method Parameters
                final Json paramsJson = new Json(true);
                json.put("params", paramsJson);
            }

            requestPayload = StringUtil.stringToBytes(json.toString());
        }

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);
        // request.setHeader("host", "127.0.0.1");
        // request.setHeader("content-length", String.valueOf(requestPayload.length));
        // request.setHeader("connection", "close");

        final Json resultJson;
        {
            final Response response = this.handleRequest(request);
            final Json responseJson = Json.parse(StringUtil.bytesToString(response.getContent()));

            final String errorString = responseJson.getString("error");
            if (! Util.isBlank(errorString)) {
                Logger.debug("Received error from " + _toString() + ": " + errorString);
                return null;
            }

            resultJson = responseJson.get("result");
        }

        final Long blockHeight = resultJson.getLong("blocks");
        final ChainWork chainWork = ChainWork.fromHexString(resultJson.getString("chainwork"));

        return new ChainHeight(blockHeight, chainWork);
    }

    @Override
    public Boolean validateBlockTemplate(final Block blockTemplate) {
        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", _nextRequestId.getAndIncrement());
            json.put("method", "validateblocktemplate");

            { // Method Parameters
                final BlockDeflater blockDeflater = new BlockDeflater();
                final ByteArray blockTemplateBytes = blockDeflater.toBytes(blockTemplate);

                final Json paramsJson = new Json(true);
                paramsJson.add(blockTemplateBytes);

                json.put("params", paramsJson);
            }

            requestPayload = StringUtil.stringToBytes(json.toString());
        }

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);

        final Response response = this.handleRequest(request);
        final Json responseJson = Json.parse(StringUtil.bytesToString(response.getContent()));
        return responseJson.get("result", false);
    }

    @Override
    public String toString() {
        return _toString();
    }
}
