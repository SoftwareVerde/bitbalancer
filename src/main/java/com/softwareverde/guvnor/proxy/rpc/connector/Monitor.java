package com.softwareverde.guvnor.proxy.rpc.connector;

public interface Monitor {
    Boolean isComplete();
    Long getDurationMs();

    void setMaxDurationMs(Long maxDurationMs);
    void cancel();
}
