package com.softwareverde.guvnor.proxy.rpc;

import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Map;

public class RpcConfiguration {
//    public static Map<NotificationType, String> getZmqEndpoints(final RpcConfiguration rpcConfiguration) {
//        if (rpcConfiguration.hasZmqPorts()) {
//            final String baseEndpointUri = ("tcp://" + rpcConfiguration.getHost() + ":");
//            final HashMap<NotificationType, String> zmqEndpoints = new HashMap<>();
//            for (final NotificationType notificationType : NotificationType.values()) {
//                final Integer zmqPort = rpcConfiguration.getZmqPort(notificationType);
//                if (zmqPort == null) { continue; }
//
//                final String endpointUri = (baseEndpointUri + zmqPort);
//                zmqEndpoints.put(notificationType, endpointUri);
//            }
//            return zmqEndpoints;
//        }
//
//        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
//        return bitcoinRpcConnector.getZmqEndpoints();
//    }

    protected final String _name;
    protected final BitcoinRpcConnector _bitcoinRpcConnector;
    protected final Integer _hierarchy;
    protected final HashMap<NotificationType, Integer> _zmqPorts;

    public RpcConfiguration(final String name, final BitcoinRpcConnector bitcoinRpcConnector) {
        this(name, bitcoinRpcConnector, null);
    }

    public RpcConfiguration(final String name, final BitcoinRpcConnector bitcoinRpcConnector, final Integer preferenceOrder) {
        this(name, bitcoinRpcConnector, preferenceOrder, null);
    }

    public RpcConfiguration(final String name, final BitcoinRpcConnector bitcoinRpcConnector, final Integer preferenceOrder, final Map<NotificationType, Integer> zmqPorts) {
        _name = name;
        _bitcoinRpcConnector = bitcoinRpcConnector;
        _hierarchy = preferenceOrder;

        if ( (zmqPorts != null) && (! zmqPorts.isEmpty()) ) {
            _zmqPorts = new HashMap<>(0);
            _zmqPorts.putAll(zmqPorts);
        }
        else {
            _zmqPorts = null;
        }
    }

    public String getName() {
        return _name;
    }

    public BitcoinRpcConnector getBitcoinRpcConnector() {
        return _bitcoinRpcConnector;
    }

    public Integer getHierarchy() {
        return _hierarchy;
    }

    public String getHost() {
        return _bitcoinRpcConnector.getHost();
    }

    public Integer getPort() {
        return _bitcoinRpcConnector.getPort();
    }

    public Boolean hasZmqPorts() {
        return (_zmqPorts != null);
    }

    public Integer getZmqPort(final NotificationType notificationType) {
        if (_zmqPorts == null) { return null; }
        return _zmqPorts.get(notificationType);
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof RpcConfiguration)) { return false; }

        final String host = _bitcoinRpcConnector.getHost();
        final Integer port = _bitcoinRpcConnector.getPort();

        final RpcConfiguration rpcConfiguration = (RpcConfiguration) object;

        if (! Util.areEqual(_hierarchy, rpcConfiguration.getHierarchy())) { return false; }
        if (! Util.areEqual(host, rpcConfiguration.getHost())) { return false; }
        if (! Util.areEqual(port, rpcConfiguration.getPort())) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        final String host = _bitcoinRpcConnector.getHost();
        final Integer port = _bitcoinRpcConnector.getPort();

        return (Util.coalesce(_hierarchy).hashCode() + host.hashCode() + port.hashCode());
    }

    @Override
    public String toString() {
        if (! Util.isBlank(_name)) { return _name; }
        return (_bitcoinRpcConnector.getHost() + ":" + _bitcoinRpcConnector.getPort());
    }
}
