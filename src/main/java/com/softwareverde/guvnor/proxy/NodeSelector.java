package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;

public interface NodeSelector {
    RpcConfiguration selectBestNode();
    RpcConfiguration selectBestNode(List<RpcConfiguration> excludedConfiguration);
    List<RpcConfiguration> getNodes();
}
