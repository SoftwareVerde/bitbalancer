package com.softwareverde.guvnor.proxy;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class ZmqNotificationThread extends Thread {
    public interface Callback {
        void onNewNotification(Notification notification);
    }

    protected final String _messageType;
    protected Callback _callback;

    public ZmqNotificationThread(final String messageType, final String endpointUri, final Callback callback) {
        super(new Runnable() {
            @Override
            public void run() {
                try (final ZContext context = new ZContext()) {
                    final SocketType socketType = SocketType.type(zmq.ZMQ.ZMQ_SUB);
                    final ZMQ.Socket socket = context.createSocket(socketType);

                    socket.connect(endpointUri); // eg: "tcp://host:port"

                    socket.subscribe("rawblock".getBytes());
                    socket.subscribe("hashblock".getBytes());
                    socket.subscribe("rawtx".getBytes());
                    socket.subscribe("hashtx".getBytes());

                    String messageType = null;
                    ByteArray payload = null;

                    final Thread thread = Thread.currentThread();
                    while (! thread.isInterrupted()) {
                        final ZMsg zMsg = ZMsg.recvMsg(socket);
                        if (zMsg == null) { break; }

                        int frameIndex = 0;
                        for (final ZFrame zFrame : zMsg) {
                            final byte[] bytes = zFrame.getData();
                            if (frameIndex == 0) {
                                messageType = new String(bytes);
                            }
                            else if (frameIndex == 1) {
                                payload = ByteArray.wrap(bytes);
                            }
                            else {
                                break;
                            }
                            ++frameIndex;
                        }
                    }

                    if ( (messageType != null) && (payload != null) ) {
                        final Notification notification = new Notification(messageType, payload);
                        callback.onNewNotification(notification);
                    }
                }
            }
        });

        this.setName("ZMQ Notification Thread - " + endpointUri);
        this.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
        this.setDaemon(true);

        _messageType = messageType;
        _callback = callback;
    }

    public String getMessageType() {
        return _messageType;
    }
}
