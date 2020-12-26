package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.NotificationCallback;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;

public class FakeBitcoinRpcConnector implements BitcoinRpcConnector {
    protected final String _host;
    protected final Integer _port;
    protected ChainHeight _chainHeight = ChainHeight.UNKNOWN_CHAIN_HEIGHT;

    public FakeBitcoinRpcConnector(final String host) {
        _host = host;
        _port = 8334;
    }

    public void setChainHeight(final ChainHeight chainHeight) {
        _chainHeight = chainHeight;
    }

    @Override
    public String getHost() {
        return _host;
    }

    @Override
    public Integer getPort() {
        return _port;
    }

    @Override
    public Response handleRequest(final Request request) {
        return null;
    }

    @Override
    public ChainHeight getChainHeight() {
        return _chainHeight;
    }

    @Override
    public BlockTemplate getBlockTemplate() {
        return null;
    }

    @Override
    public Boolean validateBlockTemplate(final BlockTemplate blockTemplate) {
        return null;
    }

    @Override
    public Boolean supportsNotifications() {
        return false;
    }

    @Override
    public Boolean supportsNotification(final NotificationType notificationType) {
        return false;
    }

    @Override
    public void subscribeToNotifications(final NotificationCallback notificationCallback) { }

    @Override
    public void unsubscribeToNotifications() { }
}
