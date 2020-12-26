package com.softwareverde.guvnor.proxy.node.selector;

import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;

public interface NodeSelector {
    RpcConfiguration selectBestNode();
    RpcConfiguration selectBestNode(List<RpcConfiguration> excludedConfiguration);
    RpcConfiguration selectBestNode(NotificationType requiredNotificationType);
    List<RpcConfiguration> getNodes();
}
