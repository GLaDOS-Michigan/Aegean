// $Id: TCPNetwork.java 67 2010-03-05 21:22:02Z yangwang $


package BFT.network;


import BFT.BaseNode;
import BFT.membership.Membership;
import BFT.membership.Principal;
import BFT.util.Role;
import BFT.util.UnsignedTypes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;

public class TCPNetwork implements Network {

    MessageHandler messageHandler;
    public Hashtable<String, SocketChannel> sockets;
    // The channels on which we'll accept connections
    public ServerSocketChannel[] serverChannels;
    // The selector we'll be monitoring
    public Selector selector;
    // The buffer into which we'll read data when it's available
    public ByteBuffer readBuffer = ByteBuffer.allocate(10);
    // A list of ChangeRequest instances
    public List<ChangeRequest> changeRequests = new LinkedList<ChangeRequest>();
    // Maps a SocketChannel to a list of ByteBuffer instances
    public Hashtable pendingData = new Hashtable();

    // stores the available bytes on each socket
    public Hashtable storedBytes = new Hashtable();

    public boolean listening;

    // the worker 
    protected Worker worker;

    // the membership list
    protected Membership membership;

    protected Thread workerThread;
    protected Thread listenerThread;

    public TCPNetwork(BaseNode bn) {
        this(bn, bn.getMembership());
    }

    public TCPNetwork(MessageHandler bn, Membership mem) {
        this.messageHandler = bn;
        membership = mem;
        sockets = new Hashtable<String, SocketChannel>();
        storedBytes = new Hashtable<SocketChannel, byte[]>();

        try {
            selector = SelectorProvider.provider().openSelector();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(0);
        }
        listening = true;

        Principal[] myInterfaces = membership.getMyInterfaces();

        String[] IPports = new String[myInterfaces.length];
        for (int i = 0; i < myInterfaces.length; i++) {
            IPports[i] = new String(myInterfaces[i].getIP().toString().split("/", 0)[1] + ":" + myInterfaces[i].getPort());
        }

        // remove duplicates
        Set<String> set = new HashSet<String>(Arrays.asList(IPports));
        String[] IPportsNoDup = (set.toArray(new String[set.size()]));

        for (int i = 0; i < IPportsNoDup.length; i++) {
            //System.out.println(IPportsNoDup[i]);
        }
        //System.exit(0);
        this.serverChannels = new ServerSocketChannel[IPportsNoDup.length];
        //////System.out.println("Read interfaces, going to register them with selector");
        for (int i = 0; i < IPportsNoDup.length; i++) {
            try {
                String ipStr = IPportsNoDup[i].split(":", 0)[0];
                String portStr = IPportsNoDup[i].split(":", 0)[1];

                this.serverChannels[i] = ServerSocketChannel.open();
                this.serverChannels[i].configureBlocking(false);

                // Bind the server socket to the specified address and port
                InetSocketAddress isa = new InetSocketAddress(InetAddress.getByName(ipStr), Integer.parseInt(portStr));
                this.serverChannels[i].socket().bind(isa);
                this.serverChannels[i].register(selector, SelectionKey.OP_ACCEPT);

                //listenThreads[i] = new ListenerSocket(this, InetAddress.getByName(ipStr), Integer.parseInt(portStr));
                //listenThreads[i].start();
            } catch (UnknownHostException ex) {
                //Logger.getLogger(BaseNode.class.getName()).log(Level.SEVERE, null, ex);
                ex.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /**
     * Set the worker
     **/
    public void setWorker(Worker work) {
        worker = work;
    }

    public void send(byte m[], Role role, int id) {
        if (role.equals(Role.CLIENT)) {
            for (int i = 0; i < membership.getClientNodes()[id].length; i++) {
                send(m, role, id, i);
            }
        } else {
            send(m, role, id, -1);
        }
    }

    /**
     * General function to send bytes to a node
     */
    public void send(byte m[], Role role, int id, int subId) {
        //////System.out.println("Start of send");
        String key = role.toString() + id;
        SocketChannel socket = (SocketChannel) sockets.get(key);
        boolean isNew = false;
        if (socket == null) {    // this means there is no such socket, we need to open it
            // this means there is no such socket, we need to open it
            //////System.out.println("socket = null, creating new");
            Principal p = new Principal(); // just a silly initialization
            switch (role) {
                case CLIENT:
                    p = membership.getClientNodes()[id][subId];
                    break;
                case ORDER:
                    p = membership.getOrderNodes()[id];
                    break;
                case EXEC:
                    p = membership.getExecNodes()[id];
                    break;
                case FILTER:
                    p = membership.getFilterNodes()[id];
                    break;
                default:
                    throw new RuntimeException("Unknown Role");
            }

            try {
                //////System.out.println("Creating new socketChannel to "+p.getIP().toString()+":"+p.getPort());
                socket = initiateConnection(p.getIP(), p.getPort());
                isNew = true;
                sockets.put(key, socket);
            } catch (IOException ex) {
                //Logger.getLogger(BaseNode.class.getName()).log(Level.SEVERE, null, ex);
                ex.printStackTrace();
            }
        }

        synchronized (this.changeRequests) {
            // Indicate we want the interest ops set changed
            SelectionKey selKey = socket.keyFor(selector);
            if ((selKey != null)) {
                if (selKey.isValid()) {
                    if ((selKey.interestOps() & SelectionKey.OP_CONNECT) == 0) {
                        //////System.out.println("Adding changeRequest");
                        this.changeRequests.add(new ChangeRequest(socket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
                    }
                }
            }

            // And queue the data we want written
            synchronized (this.pendingData) {
                List queue = (List) this.pendingData.get(socket);
                if (queue == null) {
                    queue = new ArrayList();
                    this.pendingData.put(socket, queue);
                }
                byte[] lenBytes = UnsignedTypes.longToBytes((long) m.length);
                byte[] allBytes = new byte[m.length + 8]; // MARKER change
                // TLR 2009.1.23: Changed marker to be length of message
                byte[] marker = UnsignedTypes.longToBytes((long) m.length);
                System.arraycopy(lenBytes, 0, allBytes, 0, 4);
                System.arraycopy(m, 0, allBytes, 4, m.length);
                System.arraycopy(marker, 0, allBytes, m.length + 4, 4);
                queue.add(ByteBuffer.wrap(allBytes));
            }
        }

        // Finally, wake up our selecting thread so it can make the required changes
        //////System.out.println("Waking up selector thread");
        this.selector.wakeup();

    }

    /**
     * Handles raw bytes from the selector and calls handle(bytes)
     * if necessary
     *
     * @param bytes
     */
    public void handleRawBytes(SocketChannel socket, byte[] bytes) {
        /*BFT.//Debug.println("I got "+bytes.length+" bytes");
        for(int i=0;i<bytes.length;i++) {
            BFT.Debug.print(bytes[i]+" ");
        }
        BFT.//Debug.println();*/

        byte[] lengthBuf = UnsignedTypes.longToBytes((long) bytes.length);

        if (!storedBytes.containsKey(socket)) {
            storedBytes.put(socket, new byte[0]);
        }
        int target = 0;
        byte[] existingBytes = (byte[]) storedBytes.get(socket);
        /*if(existingBytes.length > 0) {
            BFT.//Debug.println("there are already "+existingBytes.length+" bytes for this socket");
        }*/
        byte[] newBytes = new byte[existingBytes.length + bytes.length];
        System.arraycopy(existingBytes, 0, newBytes, 0, existingBytes.length);
        System.arraycopy(bytes, 0, newBytes, existingBytes.length, bytes.length);

        // CAUTION: I am hardcoding the number of bytes that constitute the length header

        if (newBytes.length < 4) { // we still dont know the length
            //////System.out.println("newBytes.length < 2. Do nothing");
            storedBytes.put(socket, newBytes);
        } else {    // we know the length of the message
            byte[] lenBytes = new byte[4];
            System.arraycopy(newBytes, 0, lenBytes, 0, 4);
            int len = (int) UnsignedTypes.bytesToLong(lenBytes);
            if (newBytes.length < 4 + len + 4) {       // MARKER change
                ////System.out.println("More than 2 bytes, but still not enough");
                storedBytes.put(socket, newBytes);
            } else {
                ////System.out.println("Enough bytes 2+("+len+")"+" and "+(newBytes.length-(2+len))+" restBytes");
                byte[] dataBytes = new byte[len];
                int rest = newBytes.length - (4 + len + 4);
                byte[] restBytes = new byte[rest];
                byte[] marker = new byte[4];
                System.arraycopy(newBytes, 4, dataBytes, 0, len);       // 2        to len+2
                System.arraycopy(newBytes, 4 + len, marker, 0, 4);        // len+2    to len+4
                System.arraycopy(newBytes, 4 + len + 4, restBytes, 0, rest);  // len+4    to end

                int markerInt = (int) UnsignedTypes.bytesToLong(marker);
                // TLR 2009.1.23: Changed marker to be length of message
                if (markerInt != len) {
                    throw new RuntimeException("invalid marker");
                }
                //BFT.//Debug.println("#nodeReq start "+System.currentTimeMillis()); 
                messageHandler.handle(dataBytes);
                //BFT.//Debug.println("#nodeReq end "+System.currentTimeMillis());
                //reset the hashtable entry. This fixes the large message bug
                storedBytes.remove(socket);
                ////System.out.println("rest = "+rest+" restBytes.length = "+restBytes.length);
                if (restBytes.length > 0) { // if there are more bytes there
                    ////System.out.println("Calling handleRawBytes iteratively with "+restBytes.length+" bytes");
                    handleRawBytes(socket, restBytes);
                }
            }
        }
    }

    public void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();

        socketChannel.configureBlocking(false);

        // Register the new SocketChannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        ////System.out.println("Inside accept, direct register of OP_READ");
        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }

    public SocketChannel initiateConnection(InetAddress address, int port) throws IOException {
        // Create a non-blocking socket channel
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        // Kick off connection establishment
        socketChannel.connect(new InetSocketAddress(address, port));

        // Queue a channel registration since the caller is not the 
        // selecting thread. As part of the registration we'll register
        // an interest in connection events. These are raised when a channel
        // is ready to complete connection establishment.
        ////System.out.println("Inside initiateConnection, INdirect register of OP_CONNECT");
        synchronized (this.changeRequests) {
            this.changeRequests.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
        }

        return socketChannel;
    }

    public void finishConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Finish the connection. If the connection operation failed
        // this will raise an IOException.
        try {
            socketChannel.finishConnect();
        } catch (IOException ioe) {
            // Cancel the channel's registration with our selector
            ////System.out.println("Warning: key canceled");
            for (Enumeration<String> e = sockets.keys(); e.hasMoreElements(); ) {
                String stringKey = e.nextElement();
                SocketChannel sc = sockets.get(stringKey);
                if (sc.equals(socketChannel)) {
                    sockets.remove(stringKey);
                    break;
                }
            }
            key.cancel();
            return;
        }

        // Register an interest in writing on this channel
        ////System.out.println("Inside accept, interestOps of OP_WRITE");
        key.interestOps(SelectionKey.OP_WRITE);
    }

    public void read(SelectionKey key) throws IOException {
        ////System.out.println("Start of read");
        SocketChannel socketChannel = (SocketChannel) key.channel();

        ////System.out.println("before clear");
        // Clear out our read buffer so it's ready for new data
        this.readBuffer.clear();

        ////System.out.println("before try");
        // Attempt to read off the channel
        int numRead;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException ioe) {
            // The remote forcibly closed the connection, cancel
            // the selection key and close the channel.
            //System.out.println("Exception in socketChannel.read");
            for (Enumeration<String> e = sockets.keys(); e.hasMoreElements(); ) {
                String stringKey = e.nextElement();
                SocketChannel sc = sockets.get(stringKey);
                if (sc.equals(socketChannel)) {
                    sockets.remove(stringKey);
                    break;
                }
            }
            key.cancel();
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            //System.out.println("Clean shutdown from other side");
            for (Enumeration<String> e = sockets.keys(); e.hasMoreElements(); ) {
                String stringKey = e.nextElement();
                SocketChannel sc = sockets.get(stringKey);
                if (sc.equals(socketChannel)) {
                    sockets.remove(stringKey);
                    break;
                }
            }
            key.channel().close();
            key.cancel();
            return;
        }
        ////System.out.println("before processData");
        // Hand the data off to our worker thread
        byte[] arr = readBuffer.array();
        //BFT.//Debug.println("position="+readBuffer.position()+" numRead="+numRead);

        worker.processData(socketChannel, arr, numRead);
    }

    public void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (this.pendingData) {
            List queue = (List) this.pendingData.get(socketChannel);

            // Write until there's no more data ...
            while (!queue.isEmpty()) {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                int numWritten = socketChannel.write(buf);
                //                //System.out.println("written "+numWritten+" bytes to the socket");
                if (buf.remaining() > 0) {
                    // ... or the socket's buffer fills up
                    //System.out.println("We have to break. Probably buffer is full");
                    Socket s = socketChannel.socket();
                    //System.out.println("Remote: "+s.getInetAddress()+":"+s.getPort());
                    //System.out.println("Local: "+s.getLocalAddress()+":"+s.getLocalPort());
                    selector.wakeup();
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                // We wrote away all data, so we're no longer interested
                // in writing on this socket. Switch back to waiting for
                // data.
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }


    public void start() {
        worker = new Worker(this);
        workerThread = new Thread(worker);
        workerThread.start();
        listenerThread = new Thread(new Listener(this));
        listenerThread.start();
        listening = true;

    }

    public void stop() {
        try {
            workerThread.interrupt();
            listenerThread.interrupt();

            for (Enumeration<String> e = sockets.keys(); e.hasMoreElements(); ) {
                String stringKey = e.nextElement();
                SocketChannel sc = sockets.get(stringKey);

                sc.close();
            }

            for (int i = 0; i < serverChannels.length; i++) {
                serverChannels[i].close();
            }
        } catch (IOException ioe) {

        }
        //throw new RuntimeException("not yet implemented");
    }
}
