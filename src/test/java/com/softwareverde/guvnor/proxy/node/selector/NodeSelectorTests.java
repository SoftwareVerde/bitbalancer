package com.softwareverde.guvnor.proxy.node.selector;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.FakeBitcoinRpcConnector;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashMap;

public class NodeSelectorTests {
    public static ChainHeight makeFakeChainHeight(final Long blockHeight) {
        return new ChainHeight(blockHeight, ChainWork.fromBigInteger(BigInteger.valueOf(blockHeight)));
    }

    @Test
    public void should_select_preferred_node_when_ahead() {
        // Setup

        final RpcConfiguration rpcConfiguration02 = new RpcConfiguration(
            "host-02",
            new FakeBitcoinRpcConnector("host-02"),
            1
        );

        final RpcConfiguration expectedRpcConfiguration = new RpcConfiguration(
            "host-01",
            new FakeBitcoinRpcConnector("host-01"),
            0
        );

        final RpcConfiguration rpcConfiguration03 = new RpcConfiguration(
            "host-03",
            new FakeBitcoinRpcConnector("host-03"),
            2
        );

        final HashMap<RpcConfiguration, ChainHeight> chainHeights = new HashMap<>();
        chainHeights.put(rpcConfiguration02, makeFakeChainHeight(1L));
        chainHeights.put(expectedRpcConfiguration, makeFakeChainHeight(2L)); // Configuration is preferred and ahead.
        chainHeights.put(rpcConfiguration03, makeFakeChainHeight(1L));

        final NodeSelector nodeSelector = new HashMapNodeSelector(chainHeights);

        // Action
        final RpcConfiguration rpcConfiguration = nodeSelector.selectBestNode();

        // Assert
        Assert.assertEquals(expectedRpcConfiguration, rpcConfiguration);
    }


    @Test
    public void should_select_preferred_node_when_others_are_up_to_date() {
        // Setup

        final RpcConfiguration rpcConfiguration02 = new RpcConfiguration(
            "host-02",
            new FakeBitcoinRpcConnector("host-02"),
            1
        );

        final RpcConfiguration expectedRpcConfiguration = new RpcConfiguration(
            "host-01",
            new FakeBitcoinRpcConnector("host-01"),
            0
        );

        final RpcConfiguration rpcConfiguration03 = new RpcConfiguration(
            "host-03",
            new FakeBitcoinRpcConnector("host-03"),
            2
        );

        final HashMap<RpcConfiguration, ChainHeight> chainHeights = new HashMap<>();
        chainHeights.put(rpcConfiguration02, makeFakeChainHeight(2L));
        chainHeights.put(expectedRpcConfiguration, makeFakeChainHeight(2L)); // Configuration is preferred.
        chainHeights.put(rpcConfiguration03, makeFakeChainHeight(2L));

        final NodeSelector nodeSelector = new HashMapNodeSelector(chainHeights);

        // Action
        final RpcConfiguration rpcConfiguration = nodeSelector.selectBestNode();

        // Assert
        Assert.assertEquals(expectedRpcConfiguration, rpcConfiguration);
    }

    @Test
    public void should_select_other_node_when_preferred_node_is_behind() {
        // Setup

        final RpcConfiguration rpcConfiguration01 = new RpcConfiguration(
            "host-01",
            new FakeBitcoinRpcConnector("host-01"),
            0
        );

        final RpcConfiguration rpcConfiguration02 = new RpcConfiguration(
            "host-02",
            new FakeBitcoinRpcConnector("host-02"),
            1
        );

        final RpcConfiguration rpcConfiguration03 = new RpcConfiguration(
            "host-03",
            new FakeBitcoinRpcConnector("host-03"),
            2
        );

        final HashMap<RpcConfiguration, ChainHeight> chainHeights = new HashMap<>();
        chainHeights.put(rpcConfiguration01, makeFakeChainHeight(1L)); // Configuration is behind.
        chainHeights.put(rpcConfiguration02, makeFakeChainHeight(2L));
        chainHeights.put(rpcConfiguration03, makeFakeChainHeight(2L));

        final NodeSelector nodeSelector = new HashMapNodeSelector(chainHeights);

        // Action
        final RpcConfiguration rpcConfiguration = nodeSelector.selectBestNode();

        // Assert
        Assert.assertNotEquals(rpcConfiguration01, rpcConfiguration);
    }
}
