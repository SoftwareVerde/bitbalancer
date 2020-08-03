package com.softwareverde.guvnor.proxy;

import com.softwareverde.guvnor.BitcoinNodeAddress;

public interface NodeSelector {
    BitcoinNodeAddress selectBestNode();
}
