package com.softwareverde.bitbalancer.proxy.zmq;

import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.constable.list.List;

public interface ZmqConfiguration {
    Integer getPort(RpcNotificationType zmqNotificationType);
    List<RpcNotificationType> getSupportedMessageTypes();
}
