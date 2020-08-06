package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;

public class RpcProxyServer {
    protected final Integer _port;
    protected final HttpServer _httpServer;
    protected final MutableList<RpcConfiguration> _rpcConfigurations = new MutableList<RpcConfiguration>();

    public RpcProxyServer(final Integer port, final List<RpcConfiguration> rpcConfigurations) {
        _port = port;
        _rpcConfigurations.addAll(rpcConfigurations);

        final Endpoint endpoint;
        {
            final NodeSelector nodeSelector = new NodeSelector() {
                @Override
                public RpcConfiguration selectBestNode() {
                    return _rpcConfigurations.get(0); // TODO: Select node based on configuration and ChainWork...
                }
            };

            endpoint = new Endpoint(
                new RpcProxyHandler(nodeSelector)
            );
            endpoint.setPath("/");
            endpoint.setStrictPathEnabled(true);
        }

        _httpServer = new HttpServer();
        _httpServer.setPort(_port);
        _httpServer.enableEncryption(false);
        _httpServer.redirectToTls(false);
        _httpServer.addEndpoint(endpoint);
    }

    public void start() {
        _httpServer.start();
    }

    public void stop() {
        _httpServer.stop();
    }
}
