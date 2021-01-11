package com.softwareverde.bitbalancer.proxy.rpc.connector;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.RpcNotificationCallback;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;
import com.softwareverde.bitbalancer.proxy.rpc.ChainHeight;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.util.Container;

public class FakeBitcoinRpcConnector implements BitBalancerRpcConnector {
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
    public Monitor getMonitor() {
        return new Monitor() {
            @Override
            public Boolean isComplete() { return true; }

            @Override
            public Long getDurationMs() { return 0L; }

            @Override
            public void setMaxDurationMs(final Long maxDurationMs) { }

            @Override
            public void cancel() { }
        };
    }

    @Override
    public Response handleRequest(final Request request, final Monitor monitor) {
        return null;
    }

    @Override
    public ChainHeight getChainHeight(final Monitor monitor) {
        return _chainHeight;
    }

    @Override
    public Boolean isSuccessfulResponse(final Response response, final Container<String> errorStringContainer) {
        return null;
    }

    @Override
    public BlockTemplate getBlockTemplate(final Monitor monitor) {
        return null;
    }

    @Override
    public Boolean validateBlockTemplate(final BlockTemplate blockTemplate, final Monitor monitor) {
        return null;
    }

    @Override
    public Boolean submitBlock(final Block block, final Monitor monitor) {
        return null;
    }

    @Override
    public Boolean supportsNotifications() {
        return false;
    }

    @Override
    public Boolean supportsNotification(final RpcNotificationType notificationType) {
        return false;
    }

    @Override
    public void subscribeToNotifications(final RpcNotificationCallback notificationCallback) { }

    @Override
    public void unsubscribeToNotifications() { }
}
