package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.guvnor.BitcoinCoreUtil;
import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.NotificationCallback;
import com.softwareverde.guvnor.proxy.rpc.RpcCredentials;
import com.softwareverde.guvnor.proxy.zmq.ZmqMessageTypeConverter;
import com.softwareverde.guvnor.proxy.zmq.ZmqNotificationThread;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BitcoinCoreRpcConnector implements BitcoinRpcConnector {
    public static final String IDENTIFIER = "DEFAULT";

    protected final AtomicInteger _nextRequestId = new AtomicInteger(1);
    protected final BitcoinNodeAddress _bitcoinNodeAddress;
    protected final RpcCredentials _rpcCredentials;

    protected final Map<NotificationType, ZmqNotificationThread> _zmqNotificationThreads = new HashMap<>();
    protected Map<NotificationType, String> _zmqEndpoints = null;

    protected String _toString() {
        return (this.getHost() + ":" + this.getPort());
    }

    protected Map<NotificationType, String> _getZmqEndpoints() {
        final String host = _bitcoinNodeAddress.getHost();
        final String baseEndpointUri = ("tcp://" + host + ":");

        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", _nextRequestId.getAndIncrement());
            json.put("method", "getzmqnotifications");

            { // Method Parameters
                final Json paramsJson = new Json(true);
                json.put("params", paramsJson);
            }

            requestPayload = StringUtil.stringToBytes(json.toString());
        }

        Logger.trace("Attempting to collect ZMQ configuration for node: " + _toString());

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);

        final HashMap<NotificationType, String> zmqEndpoints = new HashMap<>();
        final Json resultJson;
        {
            final Response response = this.handleRequest(request);
            final String rawResponse = StringUtil.bytesToString(response.getContent()).trim();
            if (! Json.isJson(rawResponse)) {
                Logger.debug("Received error from " + _toString() +": " + rawResponse.replaceAll("[\\n\\r]+", "/"));
                return zmqEndpoints;
            }
            final Json responseJson = Json.parse(rawResponse);

            final String errorString = responseJson.getString("error");
            if (! Util.isBlank(errorString)) {
                Logger.debug("Received error from " + _toString() + ": " + errorString);
                return zmqEndpoints;
            }

            resultJson = responseJson.get("result");
        }

        for (int i = 0; i < resultJson.length(); ++i) {
            final Json configJson = resultJson.get(i);
            final String messageTypeString = configJson.getString("type");
            final NotificationType notificationType = ZmqMessageTypeConverter.fromPublishString(messageTypeString);
            final String address = configJson.getString("address");

            final Integer port;
            {
                final int colonIndex = address.lastIndexOf(':');
                if (colonIndex < 0) { continue; }

                final int portBeginIndex = (colonIndex + 1);
                if (portBeginIndex >= address.length()) { continue; }

                port = Util.parseInt(address.substring(portBeginIndex));
            }

            final String endpointUri = (baseEndpointUri + port);
            zmqEndpoints.put(notificationType, endpointUri);
        }

        return zmqEndpoints;
    }

    public BitcoinCoreRpcConnector(final BitcoinNodeAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
        _bitcoinNodeAddress = bitcoinNodeAddress;
        _rpcCredentials = rpcCredentials;
    }

    public void setZmqEndpoint(final NotificationType notificationType, final String endpointUri) {
        if (_zmqEndpoints == null) {
            _zmqEndpoints = new HashMap<>();
        }

        _zmqEndpoints.put(notificationType, endpointUri);
    }

    public void clearZmqEndpoints() {
        _zmqEndpoints = null;
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
            final String rawResponse = StringUtil.bytesToString(response.getContent()).trim();
            if (! Json.isJson(rawResponse)) {
                Logger.debug("Received error from " + _toString() +": " + rawResponse.replaceAll("[\\n\\r]+", "/"));
                return null;
            }
            final Json responseJson = Json.parse(rawResponse);

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

        final Boolean returnValueOnError = false;

        final Response response = this.handleRequest(request);
        final String rawResponse = StringUtil.bytesToString(response.getContent()).trim();
        if (! Json.isJson(rawResponse)) {
            Logger.debug("Received error from " + _toString() +": " + rawResponse.replaceAll("[\\n\\r]+", "/"));
            return returnValueOnError;
        }

        final Json responseJson = Json.parse(rawResponse);
        return responseJson.get("result", returnValueOnError);
    }

    @Override
    public Boolean supportsNotifications() {
        if (_zmqEndpoints == null) {
            _zmqEndpoints = _getZmqEndpoints();
        }

        return _zmqEndpoints.isEmpty();
    }

    @Override
    public Boolean supportsNotification(final NotificationType notificationType) {
        final Map<NotificationType, String> zmqEndpoints = _zmqEndpoints;
        if (zmqEndpoints == null) { return false; }

        return (zmqEndpoints.get(notificationType) != null);
    }

    @Override
    public void subscribeToNotifications(final NotificationCallback notificationCallback) {
        Map<NotificationType, String> zmqEndpoints = _zmqEndpoints;
        if (zmqEndpoints == null) {
            zmqEndpoints = _getZmqEndpoints();
            _zmqEndpoints = zmqEndpoints;
        }
        if (zmqEndpoints == null) { return; }

        for (final NotificationType notificationType : zmqEndpoints.keySet()) {
            final String endpointUri = zmqEndpoints.get(notificationType);
            final ZmqNotificationThread zmqNotificationThread = new ZmqNotificationThread(notificationType, endpointUri, notificationCallback);
            _zmqNotificationThreads.put(notificationType, zmqNotificationThread);
            zmqNotificationThread.start();
        }
    }

    @Override
    public void unsubscribeToNotifications() {
        for (final ZmqNotificationThread zmqNotificationThread : _zmqNotificationThreads.values()) {
            zmqNotificationThread.interrupt();
            try { zmqNotificationThread.join(); } catch (final Exception exception) { }
        }
        _zmqNotificationThreads.clear();
    }

    @Override
    public String toString() {
        return _toString();
    }
}
