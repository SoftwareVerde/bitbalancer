package com.softwareverde.guvnor.proxy.node.selector;

import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;

public interface NodeSelector {
    RpcConfiguration selectBestNode();
    RpcConfiguration selectBestNode(List<RpcConfiguration> excludedConfiguration);
    RpcConfiguration selectBestNode(RpcNotificationType requiredNotificationType);
    List<RpcConfiguration> getNodes();

    ChainHeight getBestChainHeight();
}
