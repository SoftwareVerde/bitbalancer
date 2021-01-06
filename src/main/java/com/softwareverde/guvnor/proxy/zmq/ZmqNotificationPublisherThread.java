package com.softwareverde.guvnor.proxy.zmq;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.guvnor.proxy.Notification;
import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.logging.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.util.LinkedList;

public class ZmqNotificationPublisherThread extends Thread {
    public static ZmqNotificationPublisherThread newZmqNotificationPublisherThread(final NotificationType notificationType, final String host, final Integer zmqPort) {
        final LinkedList<ZMsg> messageQueue = new LinkedList<>();

        return new ZmqNotificationPublisherThread(notificationType, host, zmqPort, messageQueue);
    }

    protected final NotificationType _notificationType;
    protected final Integer _port;
    protected final LinkedList<ZMsg> _messageQueue;

    protected ZmqNotificationPublisherThread(final NotificationType notificationType, final String host, final Integer port, final LinkedList<ZMsg> messageQueue) {
        super(new Runnable() {
            @Override
            public void run() {
                try (final ZContext context = new ZContext()) {
                    final SocketType socketType = SocketType.type(zmq.ZMQ.ZMQ_PUB);
                    final ZMQ.Socket socket = context.createSocket(socketType);

                    socket.bind("tcp://" + host + ":" + port);

                    final Thread thread = Thread.currentThread();
                    while (! thread.isInterrupted()) {
                        synchronized (messageQueue) {
                            messageQueue.wait();

                            final ZMsg zMsg = messageQueue.poll();
                            if (zMsg != null) {
                                zMsg.send(socket);
                            }
                        }
                    }
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
                }
            }
        });

        this.setName("ZMQ Publisher Thread - " + "tcp://" + host + ":" + port);
        this.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
        this.setDaemon(true);

        _notificationType = notificationType;
        _port = port;
        _messageQueue = messageQueue;
    }

    public void sendMessage(final Notification notification) {
        final NotificationType notificationType = notification.notificationType;
        final ByteArray payload = notification.payload;

        final String messageTypeString = ZmqMessageTypeConverter.toSubscriptionString(notificationType);
        final ZMsg zMsg = new ZMsg();
        { // Frames are in reverse-order...
            zMsg.push(payload.getBytes());
            zMsg.push(messageTypeString);
        }

        synchronized (_messageQueue) {
            _messageQueue.add(zMsg);
            _messageQueue.notifyAll();
        }
    }

    public NotificationType getMessageType() {
        return _notificationType;
    }

    public Integer getPort() {
        return _port;
    }
}
