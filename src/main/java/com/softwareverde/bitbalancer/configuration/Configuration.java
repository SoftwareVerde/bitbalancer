package com.softwareverde.bitbalancer.configuration;

import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.list.List;

import java.util.HashMap;
import java.util.Map;

public class Configuration {
    protected final Integer _rpcPort;
    protected final List<NodeProperties> _nodeProperties;
    protected final HashMap<RpcNotificationType, Integer> _zmqPorts = new HashMap<>();
    protected final Long _blockTemplateCacheDuration;

    protected Configuration(final Integer rpcPort, final List<NodeProperties> nodeProperties, final Map<RpcNotificationType, Integer> zmqPorts, final Long blockTemplateCacheDuration) {
        _rpcPort = rpcPort;
        _nodeProperties = nodeProperties;
        if (zmqPorts != null) {
            _zmqPorts.putAll(zmqPorts);
        }
        _blockTemplateCacheDuration = blockTemplateCacheDuration;
    }

    public Integer getRpcPort() {
        return _rpcPort;
    }

    public List<NodeProperties> getNodeProperties() {
        return _nodeProperties;
    }

    public Integer getZmqPort(final RpcNotificationType notificationType) {
        return _zmqPorts.get(notificationType);
    }

    public Long getBlockTemplateCacheDuration() {
        return _blockTemplateCacheDuration;
    }
}
