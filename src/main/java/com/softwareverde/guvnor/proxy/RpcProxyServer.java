package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;

import java.util.HashMap;

public class RpcProxyServer {
    protected static final ChainWork UNKNOWN_CHAIN_WORK = ChainWork.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
    protected final Integer _port;
    protected final HttpServer _httpServer;
    protected final HashMap<ChainWork, RpcConfiguration> _rpcConfigurations = new HashMap<ChainWork, RpcConfiguration>();

    public RpcProxyServer(final Integer port, final List<RpcConfiguration> rpcConfigurations) {
        _port = port;
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            _rpcConfigurations.put(UNKNOWN_CHAIN_WORK, rpcConfiguration);
        }

        final Endpoint endpoint;
        {
            final NodeSelector nodeSelector = new NodeSelector() {
                @Override
                public RpcConfiguration selectBestNode() {
                    // TODO: Select node based on configuration and ChainWork...
                    for (final RpcConfiguration rpcConfiguration : _rpcConfigurations.values()) {
                        return rpcConfiguration;
                    }
                    return null;
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

    public Integer getPort() {
        return _port;
    }
}
