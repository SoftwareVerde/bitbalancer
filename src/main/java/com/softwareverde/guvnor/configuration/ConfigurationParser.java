package com.softwareverde.guvnor.configuration;

import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.guvnor.Main;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinCoreRpcConnector;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinVerdeRpcConnector;
import com.softwareverde.guvnor.proxy.rpc.connector.NoValidationBitcoinCoreRpcConnector;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationParser {
    protected void _extractZmqProperty(final String zmqKeyString, final Json sourceJson, final RpcNotificationType notificationType, final Map<RpcNotificationType, Integer> destinationMap) {
        if (! sourceJson.hasKey(zmqKeyString)) { return; }
        final Integer zmqPort = sourceJson.getInteger(zmqKeyString);

        if (zmqPort > 0) {
            destinationMap.put(notificationType, zmqPort);
        }
    }

    protected Configuration _parseConfigurationFileContents(final String fileContents) {
        final Json json = Json.parse(fileContents);

        final Integer rpcPort = json.get("rpcPort", Main.Defaults.RPC_PORT);

        final Json nodesJson = json.get("nodes");
        if ( (! nodesJson.isArray()) || (nodesJson.length() == 0) ) {
            Logger.debug("Missing \"nodes\" configuration.");
            return null;
        }

        final MutableList<NodeProperties> nodePropertiesList = new MutableList<>();
        for (int i = 0; i < nodesJson.length(); ++i) {
            final Json nodeJson = nodesJson.get(i);
            final String name = nodeJson.getString("name");
            final String host = nodeJson.getString("host");
            final Integer port = nodeJson.getInteger("port");
            final Boolean isSecure = nodeJson.get("isSecure", false);
            final String rpcUsername = nodeJson.getString("rpcUsername");
            final String rpcPassword = nodeJson.getString("rpcPassword");
            final Long maxTimeoutMs = nodeJson.getLong("maxTimeoutMs");

            final Map<RpcNotificationType, Integer> nodeZmqPorts;
            { // Parse Node ZMQ ports...
                final HashMap<RpcNotificationType, Integer> portMap = new HashMap<>();
                if (json.hasKey("zmqPorts")) {
                    final Json nodeZmqPortsJson = nodeJson.get("zmqPorts");
                    _extractZmqProperty("block", nodeZmqPortsJson, RpcNotificationType.BLOCK, portMap);
                    _extractZmqProperty("blockHash", nodeZmqPortsJson, RpcNotificationType.BLOCK_HASH, portMap);
                    _extractZmqProperty("transaction", nodeZmqPortsJson, RpcNotificationType.TRANSACTION, portMap);
                    _extractZmqProperty("transactionHash", nodeZmqPortsJson, RpcNotificationType.TRANSACTION_HASH, portMap);
                }
                nodeZmqPorts = (portMap.isEmpty() ? null : portMap);
            }

            final String connectorIdentifier;
            { // Parse the connector identifier...
                final String identifier = nodeJson.get("connector", "DEFAULT").toUpperCase();
                switch (identifier) {
                    case BitcoinCoreRpcConnector.IDENTIFIER:
                    case BitcoinVerdeRpcConnector.IDENTIFIER:
                    case NoValidationBitcoinCoreRpcConnector.IDENTIFIER: {
                        connectorIdentifier = identifier;
                    } break;

                    default: {
                        connectorIdentifier = "DEFAULT";
                    }
                }
            }

            final NodeProperties nodeProperties = new NodeProperties(name, host, port, isSecure, rpcUsername, rpcPassword, nodeZmqPorts, connectorIdentifier, (maxTimeoutMs < 1L ? null : maxTimeoutMs));
            nodePropertiesList.add(nodeProperties);
        }

        final Map<RpcNotificationType, Integer> serverZmqPorts;
        { // Parse Server external ZMQ ports...
            final HashMap<RpcNotificationType, Integer> portMap = new HashMap<>();
            if (json.hasKey("zmqPorts")) {
                final Json zmqPortsJson = json.get("zmqPorts");
                _extractZmqProperty("block", zmqPortsJson, RpcNotificationType.BLOCK, portMap);
                _extractZmqProperty("blockHash", zmqPortsJson, RpcNotificationType.BLOCK_HASH, portMap);
                _extractZmqProperty("transaction", zmqPortsJson, RpcNotificationType.TRANSACTION, portMap);
                _extractZmqProperty("transactionHash", zmqPortsJson, RpcNotificationType.TRANSACTION_HASH, portMap);
            }
            serverZmqPorts = (portMap.isEmpty() ? null : portMap);
        }

        final Long blockTemplateCacheDuration = json.getOrNull("cacheTemplateDuration", Json.Types.LONG);

        return new Configuration(rpcPort, nodePropertiesList, serverZmqPorts, blockTemplateCacheDuration);
    }

    public Configuration parseConfigurationFile(final String fileName) {
        final File file = new File(fileName);
        if (! (file.exists() && file.canRead())) {
            Logger.debug("Unable to load configuration file: " + fileName);
            return null;
        }

        final String configurationFileContents = StringUtil.bytesToString(IoUtil.getFileContents(file));
        if (configurationFileContents == null || configurationFileContents.isEmpty()) {
            Logger.debug("Empty configuration file: " + fileName);
            return null;
        }

        return _parseConfigurationFileContents(configurationFileContents);
    }
}
