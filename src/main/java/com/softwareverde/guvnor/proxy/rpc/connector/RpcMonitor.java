package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class RpcMonitor<T> implements Monitor {
    protected final NanoTimer _timer = new NanoTimer();
    protected final AtomicBoolean _isComplete = new AtomicBoolean(false);
    protected final Thread _cancelThread;
    protected volatile Long _maxDurationMs = null;
    protected T _connection;

    private Long _getDurationMs() {
        if (! _isComplete.get()) {
            _timer.stop();
        }

        final Double msElapsed = _timer.getMillisecondsElapsed();
        return msElapsed.longValue();
    }

    protected abstract void _cancelRequest();

    public RpcMonitor() {
        _cancelThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean maxDurationReached = false;
                try {
                    while ( (!_cancelThread.isInterrupted()) && (! maxDurationReached) && (! _isComplete.get()) ) {
                        Thread.sleep(100L);

                        final long duration = _getDurationMs();
                        maxDurationReached = (duration > _maxDurationMs);

                        if (maxDurationReached) {
                            Logger.debug("Request timed out after " + duration + "ms.");
                        }
                    }
                }
                catch (final Exception exception) { }

                if (maxDurationReached) {
                    RpcMonitor.this.cancel();
                }
            }
        });
    }

    void beforeRequestStart(final T connection) {
        _connection = connection;
        _timer.start();
    }

    void afterRequestEnd() {
        _isComplete.set(true);
        _timer.stop();
    }

    @Override
    public Boolean isComplete() {
        return _isComplete.get();
    }

    @Override
    public Long getDurationMs() {
        return _getDurationMs();
    }

    @Override
    public void setMaxDurationMs(final Long maxDurationMs) {
        if (_isComplete.get()) { return; }
        final boolean threadHasBeenStarted = (_maxDurationMs != null);
        _maxDurationMs = maxDurationMs;

        if (! threadHasBeenStarted) {
            _cancelThread.start();
        }
    }

    @Override
    public void cancel() {
        final T connection = _connection;
        if (connection == null) { return; }

        _cancelRequest();
    }
}
