package com.softwareverde.guvnor;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.socket.SocketConnection;
import com.softwareverde.socket.server.SocketServer;

public class Main {
    protected static Main INSTANCE = null;

    public static class Defaults {
        public static final Integer RPC_PORT = 8332;
    }

    protected final SocketServer _socketServer;
    protected final MutableList<SocketConnection> _connections = new MutableList<SocketConnection>();

    public static void main(final String[] arguments) {
        if (Main.INSTANCE == null) {
            Main.INSTANCE = new Main(arguments);
            Main.INSTANCE.run();
        }
    }

    protected Main(final String[] arguments) {
        Logger.setLogLevel(LogLevel.ON);

        _socketServer = new SocketServer(Defaults.RPC_PORT);
        _socketServer.setSocketEventCallback(new SocketServer.SocketEventCallback() {
            @Override
            public void onConnect(final SocketConnection socketConnection) {
                synchronized (_connections) {
                    socketConnection.setMessageReceivedCallback(new Runnable() {
                        @Override
                        public void run() {
                            final String message = socketConnection.popMessage();
                            Logger.trace(message);
                        }
                    });

                    _connections.add(socketConnection);
                    Logger.trace("New connection: " + socketConnection);
                }
            }

            @Override
            public void onDisconnect(final SocketConnection socketConnection) {
                synchronized (_connections) {
                    final int index = _connections.indexOf(socketConnection);
                    _connections.remove(index);
                    Logger.trace("Connection lost: " + socketConnection);
                }
            }
        });
    }

    public void run() {
        _socketServer.start();
        Logger.debug("Server started.");

        while (! Thread.interrupted()) {
            try {
                Thread.sleep(10000L);
            }
            catch (final Exception exception) {
                break;
            }
        }

        Logger.debug("Server stopped.");
    }
}
