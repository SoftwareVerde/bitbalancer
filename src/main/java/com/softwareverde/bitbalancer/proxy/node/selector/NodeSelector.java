package com.softwareverde.bitbalancer.proxy.node.selector;

import com.softwareverde.bitbalancer.proxy.rpc.ChainHeight;
import com.softwareverde.bitbalancer.proxy.rpc.RpcConfiguration;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.list.List;

public interface NodeSelector {
    RpcConfiguration selectBestNode();
    RpcConfiguration selectBestNode(List<RpcConfiguration> excludedConfiguration);
    RpcConfiguration selectBestNode(RpcNotificationType requiredNotificationType);
    List<RpcConfiguration> getNodes();

    ChainHeight getBestChainHeight();
}
