/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util;

import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.message.Message;
import org.h2.security.SecureSocketFactory;

import java.io.IOException;
import java.net.*;
import java.sql.SQLException;

/**
 * This utility class contains socket helper functions.
 */
public class NetUtils {

    private static final int CACHE_MILLIS = 1000;
    private static InetAddress cachedBindAddress;
    private static String cachedLocalAddress;
    private static long cachedLocalAddressTime;

    private NetUtils() {
        // utility class
    }

    /**
     * Create a loopback socket (a socket that is connected to localhost) on
     * this port.
     *
     * @param port the port
     * @param ssl  if SSL should be used
     * @return the socket
     */
    public static Socket createLoopbackSocket(int port, boolean ssl) throws IOException {
        InetAddress address = getBindAddress();
        if (address == null) {
            address = InetAddress.getLocalHost();
        }
        return createSocket(getHostAddress(address), port, ssl);
    }

    /**
     * Get the host address. This method adds '[' and ']' if required for
     * Inet6Address that contain a ':'.
     *
     * @param address the address
     * @return the host address
     */
    private static String getHostAddress(InetAddress address) {
        String host = address.getHostAddress();
        if (address instanceof Inet6Address) {
            if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
                host = "[" + host + "]";
            }
        }
        return host;
    }

    /**
     * Create a client socket that is connected to the given address and port.
     *
     * @param server      to connect to (including an optional port)
     * @param defaultPort the default port (if not specified in the server
     *                    address)
     * @param ssl         if SSL should be used
     * @return the socket
     */
    public static Socket createSocket(String server, int defaultPort, boolean ssl) throws IOException {
        int port = defaultPort;
        // IPv6: RFC 2732 format is '[a:b:c:d:e:f:g:h]' or
        // '[a:b:c:d:e:f:g:h]:port'
        // RFC 2396 format is 'a.b.c.d' or 'a.b.c.d:port' or 'hostname' or
        // 'hostname:port'
        int startIndex = server.startsWith("[") ? server.indexOf(']') : 0;
        int idx = server.indexOf(':', startIndex);
        if (idx >= 0) {
            port = MathUtils.decodeInt(server.substring(idx + 1));
            server = server.substring(0, idx);
        }
        InetAddress address = InetAddress.getByName(server);
        return createSocket(address, port, ssl);
    }

    /**
     * Create a client socket that is connected to the given address and port.
     *
     * @param address the address to connect to
     * @param port    the port
     * @param ssl     if SSL should be used
     * @return the socket
     */
    public static Socket createSocket(InetAddress address, int port, boolean ssl) throws IOException {
        if (ssl) {
            return SecureSocketFactory.createSocket(address, port);
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(address, port),
                SysProperties.SOCKET_CONNECT_TIMEOUT);
        return socket;
    }

    /**
     * Create a server socket. The system property h2.bindAddress is used if
     * set.
     *
     * @param port the port to listen on
     * @param ssl  if SSL should be used
     * @return the server socket
     */
    public static ServerSocket createServerSocket(int port, boolean ssl) throws SQLException {
        try {
            return createServerSocketTry(port, ssl);
        } catch (SQLException e) {
            // try again
            return createServerSocketTry(port, ssl);
        }
    }

    /**
     * Get the bind address if the system property h2.bindAddress is set, or
     * null if not.
     *
     * @return the bind address
     */
    private static InetAddress getBindAddress() throws UnknownHostException {
        String host = SysProperties.BIND_ADDRESS;
        if (host == null || host.length() == 0) {
            return null;
        }
        synchronized (NetUtils.class) {
            if (cachedBindAddress == null) {
                cachedBindAddress = InetAddress.getByName(host);
            }
        }
        return cachedBindAddress;
    }

    private static ServerSocket createServerSocketTry(int port, boolean ssl) throws SQLException {
        try {
            InetAddress bindAddress = getBindAddress();
            if (ssl) {
                return SecureSocketFactory.createServerSocket(port, bindAddress);
            }
            if (bindAddress == null) {
                return new ServerSocket(port);
            }
            return new ServerSocket(port, 0, bindAddress);
        } catch (BindException be) {
            throw Message.getSQLException(ErrorCode.EXCEPTION_OPENING_PORT_2,
                    be, "" + port, be.toString());
        } catch (IOException e) {
            throw Message.convertIOException(e, "port: " + port + " ssl: " + ssl);
        }
    }

    /**
     * Check if a socket is connected to a local address.
     *
     * @param socket the socket
     * @return true if it is
     */
    public static boolean isLocalAddress(Socket socket) throws UnknownHostException {
        InetAddress test = socket.getInetAddress();
        //## Java 1.4 begin ##
        if (test.isLoopbackAddress()) {
            return true;
        }
        //## Java 1.4 end ##
        InetAddress localhost = InetAddress.getLocalHost();
        // localhost.getCanonicalHostName() is very very slow
        String host = localhost.getHostAddress();
        for (InetAddress addr : InetAddress.getAllByName(host)) {
            if (test.equals(addr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Close a server socket and ignore any exceptions.
     *
     * @param socket the socket
     * @return null
     */
    public static ServerSocket closeSilently(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Get the local host address as a string.
     * For performance, the result is cached for one second.
     *
     * @return the local host address
     */
    public static synchronized String getLocalAddress() {
        long now = System.currentTimeMillis();
        if (cachedLocalAddress != null) {
            if (cachedLocalAddressTime + CACHE_MILLIS > now) {
                return cachedLocalAddress;
            }
        }
        InetAddress bind = null;
        try {
            bind = getBindAddress();
            if (bind == null) {
                bind = InetAddress.getLocalHost();
            }
        } catch (UnknownHostException e) {
            // ignore
        }
        String address = bind == null ? "localhost" : getHostAddress(bind);
        if (address.equals("127.0.0.1")) {
            address = "localhost";
        }
        cachedLocalAddress = address;
        cachedLocalAddressTime = now;
        return address;
    }

}
