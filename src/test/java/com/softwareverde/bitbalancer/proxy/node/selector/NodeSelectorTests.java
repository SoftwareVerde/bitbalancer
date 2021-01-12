package com.softwareverde.bitbalancer.proxy.node.selector;

import com.softwareverde.bitbalancer.proxy.rpc.ChainHeight;
import com.softwareverde.bitbalancer.proxy.rpc.RpcConfiguration;
import com.softwareverde.bitbalancer.proxy.rpc.connector.FakeBitcoinRpcConnector;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

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

        final List<RpcConfiguration> rpcConfigurations = new ImmutableList<>(expectedRpcConfiguration, rpcConfiguration02, rpcConfiguration03);
        expectedRpcConfiguration.setChainHeight(makeFakeChainHeight(2L)); // Configuration is preferred and ahead.
        rpcConfiguration02.setChainHeight(makeFakeChainHeight(1L));
        rpcConfiguration03.setChainHeight(makeFakeChainHeight(1L));

        final NodeSelector nodeSelector = new HashMapNodeSelector(rpcConfigurations);

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

        final List<RpcConfiguration> rpcConfigurations = new ImmutableList<>(expectedRpcConfiguration, rpcConfiguration02, rpcConfiguration03);
        expectedRpcConfiguration.setChainHeight(makeFakeChainHeight(2L)); // Configuration is preferred.
        rpcConfiguration02.setChainHeight(makeFakeChainHeight(2L));
        rpcConfiguration03.setChainHeight(makeFakeChainHeight(2L));

        final NodeSelector nodeSelector = new HashMapNodeSelector(rpcConfigurations);

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

        final List<RpcConfiguration> rpcConfigurations = new ImmutableList<>(rpcConfiguration01, rpcConfiguration02, rpcConfiguration03);
        rpcConfiguration01.setChainHeight(makeFakeChainHeight(1L)); // Configuration is behind.
        rpcConfiguration02.setChainHeight(makeFakeChainHeight(2L));
        rpcConfiguration03.setChainHeight(makeFakeChainHeight(2L));

        final NodeSelector nodeSelector = new HashMapNodeSelector(rpcConfigurations);

        // Action
        final RpcConfiguration rpcConfiguration = nodeSelector.selectBestNode();

        // Assert
        Assert.assertNotEquals(rpcConfiguration01, rpcConfiguration);
    }

    @Test
    public void should_select_other_node_when_preferred_node_is_behind_with_production_values() {
        // Setup
        final RpcConfiguration verde01 = new RpcConfiguration(
            "verde-01",
            new FakeBitcoinRpcConnector("verde-01"),
            0
        );

        final RpcConfiguration verde02 = new RpcConfiguration(
            "verde-02",
            new FakeBitcoinRpcConnector("verde-02"),
            1
        );

        final RpcConfiguration bchn01 = new RpcConfiguration(
            "bchn-01",
            new FakeBitcoinRpcConnector("bchn-01"),
            2
        );

        final RpcConfiguration bitcoinUnlimited01 = new RpcConfiguration(
            "bitcoin-unlimited-01",
            new FakeBitcoinRpcConnector("bitcoin-unlimited-01"),
            3
        );

        final List<RpcConfiguration> rpcConfigurations = new ImmutableList<>(verde01, verde02, bchn01, bitcoinUnlimited01);
        verde01.setChainHeight(new ChainHeight(667855L, ChainWork.fromHexString("00000000000000000000000000000000000000000158CEB0A0DFAA0B3411D4DA")));
        verde02.setChainHeight(new ChainHeight(667855L, ChainWork.fromHexString("00000000000000000000000000000000000000000158CEB0A0DFAA0B3411D4DA")));
        bchn01.setChainHeight(new ChainHeight(667855L, ChainWork.fromHexString("00000000000000000000000000000000000000000158CEB0A0DFAA0B3411D4DA")));
        bitcoinUnlimited01.setChainHeight(new ChainHeight(667854L, ChainWork.fromHexString("00000000000000000000000000000000000000000158CE7A2EA55FF4C8ACA055")));

        final NodeSelector nodeSelector = new HashMapNodeSelector(rpcConfigurations);

        // Action
        final RpcConfiguration rpcConfiguration = nodeSelector.selectBestNode();

        // Assert
        Assert.assertNotEquals(bitcoinUnlimited01, rpcConfiguration);
    }
}
