package com.softwareverde.guvnor;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.guvnor.configuration.Configuration;
import com.softwareverde.guvnor.configuration.ConfigurationParser;
import com.softwareverde.guvnor.configuration.NodeProperties;
import com.softwareverde.guvnor.proxy.RpcProxyServer;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.RpcCredentials;
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

        if (arguments.length != 1) {
            System.err.println("Missing Argument: <configuration>");
            System.exit(1);
        }

        final String configurationFilename = arguments[0];
        final ConfigurationParser configurationParser = new ConfigurationParser();
        final Configuration configuration = configurationParser.parseConfigurationFile(configurationFilename);
        if (configuration == null) {
            System.err.println("Error parsing configuration: " + configurationFilename);
            System.exit(1);
        }

        final Integer rpcPort = configuration.getRpcPort();
        final MutableList<RpcConfiguration> rpcConfigurations = new MutableList<>();
        {
            int preferenceOrder = 0;
            for (final NodeProperties nodeProperties : configuration.getNodeProperties()) {
                final String host = nodeProperties.getHost();
                final Integer port = nodeProperties.getPort();
                final BitcoinNodeAddress bchdAddress = new BitcoinNodeAddress(host, port, nodeProperties.isSecure());
                final RpcCredentials bchdRpcCredentials = new RpcCredentials(nodeProperties.getRpcUsername(), nodeProperties.getRpcPassword());
                final BitcoinRpcConnector bchdConnector = new BitcoinCoreConnector(bchdAddress, bchdRpcCredentials);
                final RpcConfiguration rpcConfiguration = new RpcConfiguration(bchdConnector, preferenceOrder);

                rpcConfigurations.add(rpcConfiguration);
                Logger.info("Added endpoint: " + preferenceOrder + "=" + host + ":" + port);

                preferenceOrder += 1;
            }
        }

        _rpcProxyServer = new RpcProxyServer(rpcPort, rpcConfigurations);
    }

    public void run() {
        _rpcProxyServer.start();
        Logger.info("Server started, listening on port " + _rpcProxyServer.getPort() + ".");

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
