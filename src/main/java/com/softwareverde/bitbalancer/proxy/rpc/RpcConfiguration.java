package com.softwareverde.bitbalancer.proxy.rpc;

import com.softwareverde.bitbalancer.proxy.rpc.connector.BitBalancerRpcConnector;
import com.softwareverde.util.Util;

public class RpcConfiguration {
    protected final String _name;
    protected final BitBalancerRpcConnector _bitcoinRpcConnector;
    protected final Integer _hierarchy;
    protected final Long _maxTimeoutMs;

    protected ChainHeight _chainHeight = ChainHeight.UNKNOWN_CHAIN_HEIGHT;

    public RpcConfiguration(final String name, final BitBalancerRpcConnector bitcoinRpcConnector) {
        this(name, bitcoinRpcConnector, null);
    }

    public RpcConfiguration(final String name, final BitBalancerRpcConnector bitcoinRpcConnector, final Integer preferenceOrder) {
        this(name, bitcoinRpcConnector, preferenceOrder, null);
    }

    public RpcConfiguration(final String name, final BitBalancerRpcConnector bitcoinRpcConnector, final Integer preferenceOrder, final Long maxTimeoutMs) {
        _name = name;
        _bitcoinRpcConnector = bitcoinRpcConnector;
        _hierarchy = preferenceOrder;
        _maxTimeoutMs = maxTimeoutMs;
    }

    public String getName() {
        return _name;
    }

    public BitBalancerRpcConnector getBitcoinRpcConnector() {
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

    public ChainHeight getChainHeight() {
        return _chainHeight;
    }

    public void setChainHeight(final ChainHeight chainHeight) {
        _chainHeight = chainHeight;
    }

    public Long getMaxTimeoutMs() {
        return _maxTimeoutMs;
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
