package com.softwareverde.guvnor.configuration;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.guvnor.Main;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;

import java.io.File;

public class ConfigurationParser {
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
            final String host = nodeJson.getString("host");
            final Integer port = nodeJson.getInteger("port");
            final Boolean isSecure = nodeJson.get("isSecure", false);
            final String rpcUsername = nodeJson.getString("rpcUsername");
            final String rpcPassword = nodeJson.getString("rpcPassword");

            final NodeProperties nodeProperties = new NodeProperties(host, port, isSecure, rpcUsername, rpcPassword);
            nodePropertiesList.add(nodeProperties);
        }

        return new Configuration(rpcPort, nodePropertiesList);
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
