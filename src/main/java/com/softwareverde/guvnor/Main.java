package com.softwareverde.guvnor;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.guvnor.configuration.Configuration;
import com.softwareverde.guvnor.configuration.ConfigurationParser;
import com.softwareverde.guvnor.configuration.NodeProperties;
import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.guvnor.proxy.RpcProxyServer;
import com.softwareverde.guvnor.proxy.ZmqConfigurationCore;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.RpcCredentials;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinCoreRpcConnector;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

import java.util.Map;

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
        Logger.setLog(LineNumberAnnotatedLog.getInstance());
        Logger.setLogLevel(LogLevel.DEBUG);

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
                final String name = nodeProperties.getName();
                final String host = nodeProperties.getHost();
                final Integer port = nodeProperties.getPort();
                final BitcoinNodeAddress bitcoinNodeAddress = new BitcoinNodeAddress(host, port, nodeProperties.isSecure());
                final RpcCredentials rpcCredentials = new RpcCredentials(nodeProperties.getRpcUsername(), nodeProperties.getRpcPassword());
                final BitcoinRpcConnector bitcoinRpcConnector = new BitcoinCoreRpcConnector(bitcoinNodeAddress, rpcCredentials);
                final Map<NotificationType, Integer> zmqPorts = nodeProperties.getZmqPorts();
                final RpcConfiguration rpcConfiguration = new RpcConfiguration(name, bitcoinRpcConnector, preferenceOrder, zmqPorts);

                rpcConfigurations.add(rpcConfiguration);
                Logger.info("Added endpoint: " + preferenceOrder + "=" + host + ":" + port + " (" + name + ")");

                preferenceOrder += 1;
            }
        }

        final ZmqConfigurationCore zmqConfiguration = new ZmqConfigurationCore();
        for (final NotificationType notificationType : NotificationType.values()) {
            final Integer zmqPort = configuration.getZmqPort(notificationType);
            zmqConfiguration.setPort(notificationType, zmqPort);
        }

        _rpcProxyServer = new RpcProxyServer(rpcPort, rpcConfigurations, zmqConfiguration);
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
