package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.http.server.HttpServer;
import com.softwareverde.http.server.endpoint.Endpoint;

public class RpcProxyServer {
    protected final Integer _port;
    protected final HttpServer _httpServer;
    protected final MutableList<BitcoinNodeAddress> _bitcoinNodeAddresses = new MutableList<BitcoinNodeAddress>();

    public RpcProxyServer(final Integer port, final List<BitcoinNodeAddress> bitcoinNodeAddresses) {
        _port = port;
        _bitcoinNodeAddresses.addAll(bitcoinNodeAddresses);

        final Endpoint endpoint;
        {
            final NodeSelector nodeSelector = new NodeSelector() {
                @Override
                public BitcoinNodeAddress selectBestNode() {
                    return _bitcoinNodeAddresses.get(0); // TODO: Select node based on configuration and ChainWork...
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
