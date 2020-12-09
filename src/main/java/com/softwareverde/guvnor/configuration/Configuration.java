package com.softwareverde.guvnor.configuration;

import com.softwareverde.constable.list.List;

public class Configuration {
    protected final Integer _rpcPort;
    protected final List<NodeProperties> _nodeProperties;

    protected Configuration(final Integer rpcPort, final List<NodeProperties> nodeProperties) {
        _rpcPort = rpcPort;
        _nodeProperties = nodeProperties;
    }

    public Integer getRpcPort() {
        return _rpcPort;
    }

    public List<NodeProperties> getNodeProperties() {
        return _nodeProperties;
    }
}
