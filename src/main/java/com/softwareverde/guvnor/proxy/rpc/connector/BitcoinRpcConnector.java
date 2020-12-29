package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.NotificationCallback;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.util.Container;
import com.softwareverde.util.StringUtil;

public interface BitcoinRpcConnector {
    String getHost();
    Integer getPort();

    Monitor getMonitor();

    Response handleRequest(Request request, Monitor monitor);

    ChainHeight getChainHeight(Monitor monitor);
    BlockTemplate getBlockTemplate(Monitor monitor);
    Boolean validateBlockTemplate(BlockTemplate blockTemplate, Monitor monitor);

    default Response handleRequest(Request request) {
        return this.handleRequest(request, null);
    }

    default ChainHeight getChainHeight() {
        return this.getChainHeight(null);
    }
    default BlockTemplate getBlockTemplate() {
        return this.getBlockTemplate(null);
    }
    default Boolean validateBlockTemplate(BlockTemplate blockTemplate) {
        return this.validateBlockTemplate(blockTemplate, null);
    }

    Boolean supportsNotifications();
    Boolean supportsNotification(NotificationType notificationType);
    void subscribeToNotifications(NotificationCallback notificationCallback);
    void unsubscribeToNotifications();

    default Boolean isSuccessfulResponse(final Response response, final Json preParsedResponse, final Container<String> errorStringContainer) {
        errorStringContainer.value = null;
        if (response == null) { return false; }

        if (! Util.areEqual(Response.Codes.OK, response.getCode())) {
            return false;
        }

        final Json responseJson;
        if (preParsedResponse != null) {
            responseJson = preParsedResponse;
        }
        else {
            final String rawResponse = StringUtil.bytesToString(response.getContent());
            responseJson = Json.parse(rawResponse);
        }

        final String errorString = responseJson.getString("error");
        if (! Util.isBlank(errorString)) {
            errorStringContainer.value = errorString;
            return false;
        }

        return true;
    }
}
