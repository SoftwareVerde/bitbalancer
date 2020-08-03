package com.softwareverde.guvnor;

import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.guvnor.proxy.RpcProxyServer;
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

        final ImmutableList<BitcoinNodeAddress> bitcoinNodeAddresses;
        { // TODO: Read from arguments...
            bitcoinNodeAddresses = new ImmutableList<BitcoinNodeAddress>(
                new BitcoinNodeAddress("bchd.greyh.at", 8335),
                new BitcoinNodeAddress("btc.sv.net", 8332)
            );
        }

        final Integer rpcPort;
        { // TODO: Read from arguments...
            rpcPort = Defaults.RPC_PORT;
        }

        _rpcProxyServer = new RpcProxyServer(rpcPort, bitcoinNodeAddresses);
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
