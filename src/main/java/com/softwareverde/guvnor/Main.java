package com.softwareverde.guvnor;

import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.guvnor.proxy.RpcProxyServer;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinCoreConnector;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

public class Main {
    protected static Main INSTANCE = null;

    public static class Defaults {
        public static final Integer RPC_PORT = 8332;
    }

    protected final RpcProxyServer _rpcProxyServer;

    public static void main(final String[] arguments) {
        if (Main.INSTANCE == null) {
            Main.INSTANCE = new Main(arguments);
            Main.INSTANCE.run();
        }
    }

    protected Main(final String[] arguments) {
        Logger.setLogLevel(LogLevel.ON);

        final ImmutableList<RpcConfiguration> rpcConfigurations;
        { // TODO: Read from arguments...
            final RpcConfiguration bchdConfiguration;
            {
                final BitcoinNodeAddress bchdAddress = new BitcoinNodeAddress("bchd.greyh.at", 8334, true);
                final BitcoinRpcConnector bchdConnector = new BitcoinCoreConnector(bchdAddress);
                bchdConfiguration = new RpcConfiguration(bchdAddress, bchdConnector, 0);
            }

            final RpcConfiguration bchnConfiguration;
            {
                final BitcoinNodeAddress bchnAddress = new BitcoinNodeAddress("btc.sv.net", 8332);
                final BitcoinRpcConnector bchdConnector = new BitcoinCoreConnector(bchnAddress);
                bchnConfiguration = new RpcConfiguration(bchnAddress, bchdConnector, 1);
            }

            rpcConfigurations = new ImmutableList<RpcConfiguration>(
                bchdConfiguration,
                bchnConfiguration
            );
        }

        final Integer rpcPort;
        { // TODO: Read from arguments...
            rpcPort = Defaults.RPC_PORT;
        }

        _rpcProxyServer = new RpcProxyServer(rpcPort, rpcConfigurations);
    }

    public void run() {
        _rpcProxyServer.start();
        Logger.debug("Server started.");

        while (! Thread.interrupted()) {
            try {
                Thread.sleep(10000L);
            }
            catch (final Exception exception) {
                break;
            }
        }

        _rpcProxyServer.stop();
        Logger.debug("Server stopped.");
    }
}
