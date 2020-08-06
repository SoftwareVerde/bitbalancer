package com.softwareverde.guvnor.proxy;

import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;

public interface NodeSelector {
    RpcConfiguration selectBestNode();
}
