/**
 * "FreePastry" Peer-to-Peer Application Development Substrate Copyright 2002,
 * Rice University. All rights reserved. Redistribution and use in source and
 * binary forms, with or without modification, are permitted provided that the
 * following conditions are met: - Redistributions of source code must retain
 * the above copyright notice, this list of conditions and the following
 * disclaimer. - Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. -
 * Neither the name of Rice University (RICE) nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. This software is provided by RICE and the
 * contributors on an "as is" basis, without any representations or warranties
 * of any kind, express or implied including, but not limited to,
 * representations or warranties of non-infringement, merchantability or fitness
 * for a particular purpose. In no event shall RICE or contributors be liable
 * for any direct, indirect, incidental, special, exemplary, or consequential
 * damages (including, but not limited to, procurement of substitute goods or
 * services; loss of use, data, or profits; or business interruption) however
 * caused and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of the use
 * of this software, even if advised of the possibility of such damage.
 */

package rice.pastry.wire;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.wire.exception.*;
import rice.pastry.wire.messaging.datagram.*;
import rice.pastry.wire.messaging.socket.*;

/**
 * Class which represents a node handle in the socket-based pastry protocol.
 * Initially, all of the messages are sent over UDP. If a message is too large
 * to be sent over the UDP protocol (as determined by the MAX_UDP_MESSAGE_SIZE),
 * then a socket connection is opened to the remote node.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class WireNodeHandle extends DistCoalesedNodeHandle implements SelectionKeyHandler {

    // the time the last ping was performed
    private transient long lastpingtime;

    // used only when there is a socket open
    private transient SocketChannelReader reader;
    private transient SocketChannelWriter writer;
    private transient SelectionKey key;
    private transient int state;

    // possible states of the WireNodeHandle
    /**
     * DESCRIBE THE FIELD
     */
    public static int STATE_USING_UDP = -1;
    /**
     * DESCRIBE THE FIELD
     */
    public static int STATE_USING_TCP = -2;
    /**
     * DESCRIBE THE FIELD
     */
    public static int STATE_USING_UDP_WAITING_FOR_TCP_DISCONNECT = -3;
    /**
     * DESCRIBE THE FIELD
     */
    public static int STATE_USING_UDP_WAITING_TO_DISCONNECT = -4;

    // the largest message size to send over UDP
    /**
     * DESCRIBE THE FIELD
     */
    public static int MAX_UDP_MESSAGE_SIZE = DatagramManager.DATAGRAM_SEND_BUFFER_SIZE;

    // the size of the "receive" buffer for the socket
    /**
     * DESCRIBE THE FIELD
     */
    public static int SOCKET_BUFFER_SIZE = 32768;

    // the throttle (pings only sent this many seconds)
    /**
     * DESCRIBE THE FIELD
     */
    public static int PING_THROTTLE = 5;

    /**
     * Constructor.
     *
     * @param address The address of the host on which this node resides
     * @param nid The NodeId of this host
     */
    public WireNodeHandle(InetSocketAddress address, NodeId nid) {
        super(nid, address);

        debug("creating Socket handle for node: " + nid + " address: " + address);

        lastpingtime = 0;

        state = STATE_USING_UDP;
    }

    /**
     * Alternate constructor with local Pastry node.
     *
     * @param address The address of the host on which this node resides
     * @param nid The NodeId of this host
     * @param pn The local Pastry node
     */
    public WireNodeHandle(InetSocketAddress address, NodeId nid, PastryNode pn) {
        super(nid, address);

        debug("creating Socket handle for node: " + nid + ", local: " + pn + " address: " + address);

        lastpingtime = 0;

        state = STATE_USING_UDP;

        setLocalNode(pn);
    }

    /**
     * Returns the state of this WireNodeHandle
     *
     * @return The state of this handle
     */
    public int getState() {
        return state;
    }

    /**
     * Method which sets the SelectionKey of this node handle. Is designed to be
     * called whenever a TCP connection has been established. All pending
     * message will now be sent over TCP using the socket attached to this
     * SelectionKey. If a socket has already been established, there is a
     * protocol for determining which socket to close. If the address:port of
     * the local is less than that of the remote, this node ignores the incoming
     * key. Otherwise, it will kill it's own socket and use the new key as the
     * "real" socket. NOTE: There are known problems with this implementation,
     * especially under high stress.
     *
     * @param key The new SelectionKey
     * @param scm The new Key value
     */
    public void setKey(SelectionKey key, SocketCommandMessage scm) {
        debug("Got new key  (state == " + state + ")");

        // if we're currently using UDP, accept the connection as usual
        if (state == STATE_USING_UDP) {
            this.key = key;
            key.attach(this);

            ((WirePastryNode) getLocalNode()).getSocketManager().openSocket(this);
            ((WirePastryNode) getLocalNode()).getDatagramManager().resetAckNumber(nodeId);

            reader = new SocketChannelReader((WirePastryNode) getLocalNode());
            writer = new SocketChannelWriter((WirePastryNode) getLocalNode(), scm);

            state = STATE_USING_TCP;
        } else {
            markAlive();

            // otherwise, we have problems!
            InetSocketAddress local = ((WireNodeHandle) getLocalNode().getLocalHandle()).getAddress();
            InetSocketAddress remote = getAddress();

            debug("Found double socket... (state == " + state + ")");

            // if not currently connected (connection killing pending), we must request a new socket
            if (state != STATE_USING_TCP) {
                ((WirePastryNode) getLocalNode()).getSocketManager().openSocket(this);
            }

            // determine who should kill the socket
            if ((getAddress(local.getAddress()) > getAddress(remote.getAddress())) ||
                ((getAddress(local.getAddress()) == getAddress(remote.getAddress())) &&
                (local.getPort() > remote.getPort()))) {

                // kill our socket and use the new one
                try {
                    this.key.attach(null);
                    this.key.channel().close();
                    this.key.cancel();

                    writer.reset(scm);
                } catch (IOException e) {
                    System.out.println("ERROR closing unnecessary socket: " + e);
                }

                // use new socket
                this.key = key;
                state = STATE_USING_TCP;
                key.attach(this);

                debug("Killing our socket, using new one...");
            } else {

                // use our socket and ignore the new one
                key.attach(null);
                debug("Using our socket, letting other socket die...");
            }
        }
    }

    /**
     * Method which is called when a SocketCommandMessage comes across an open
     * socket for this node handle.
     *
     * @param message The message coming across the wire.
     */
    public void receiveSocketMessage(SocketCommandMessage message) {
        if (message instanceof DisconnectMessage) {
            debug("Received DisconnectMessage (state == " + state + ")");

            if (state == STATE_USING_TCP) {
                state = STATE_USING_UDP_WAITING_TO_DISCONNECT;
                ((WirePastryNode) getLocalNode()).getSocketManager().closeSocket(this);
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            } else if (state == STATE_USING_UDP_WAITING_FOR_TCP_DISCONNECT) {
                close(null);
            } else {
                System.out.println("Recieved DisconnectMessage at non-connected socket - not fatal... (state == " + state + ")");
            }
        } else if (message instanceof HelloResponseMessage) {
            HelloResponseMessage hrmsg = (HelloResponseMessage) message;

            if (hrmsg.getNodeId().equals(getNodeId()) && hrmsg.getDestination().equals(getLocalNode().getNodeId())) {
                markAlive();
                writer.greetingReceived();
            } else {
                debug("Receved incorrect HelloMessageResponse for nodeId " + hrmsg.getNodeId() + " - resetting.");
                close(null);
            }
        } else {
            System.out.println("Received unreconginzed SocketCommandMessage " + message + " - dropping on floor");
        }
    }

    /**
     * Called to send a message to the node corresponding to this handle.
     *
     * @param msg Message to be delivered, may or may not be routeMessage.
     */
    public void receiveMessageImpl(Message msg) {
        assertLocalNode();

        WirePastryNode spn = (WirePastryNode) getLocalNode();

        if (isLocal) {
            debug("Sending message " + msg + " locally");
            spn.receiveMessage(msg);
        } else {
            debug("Passing message " + msg + " to the socket controller for writing (state == " + state + ")");

            // check to see if socket is open
            if (state != STATE_USING_TCP) {
                try {
                    if (state == STATE_USING_UDP) {
                        // if message is small enough, send via UDP
                        if (messageSize(msg) <= MAX_UDP_MESSAGE_SIZE) {
                            debug("Message is small enough to go over UDP - sending.");
                            ((WirePastryNode) getLocalNode()).getDatagramManager().write(nodeId, address, msg);
                        } else {
                            debug("Message is too large - open up socket!");
                            LinkedList list = new LinkedList();
                            list.addFirst(new SocketTransportMessage(msg, nodeId));

                            connectToRemoteNode(list);
                        }
                    } else {
                        // if we're waiting to disconnect, send message over UDP anyway
                        ((WirePastryNode) getLocalNode()).getDatagramManager().write(nodeId, address, msg);
                    }
                } catch (IOException e) {
                    System.out.println("IOException " + e + " serializing message " + msg + " - cancelling message.");
                }
            } else {
                writer.enqueue(new SocketTransportMessage(msg, nodeId));

                if (((WirePastryNode) getLocalNode()).inThread()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                } else {
                    ((WirePastryNode) getLocalNode()).getSelectorManager().getSelector().wakeup();
                }

                debug("Enqueued message " + msg + " for writing in socket writer.");
            }
        }
    }

    /**
     * Method which initiates a connection to a remote node. This is done by
     * connecting to the server socket on the remote node. This can be called by
     * the receiveMessageImpl, if there is a too-big message waiting to be sent,
     * or by the TransmissionManager if there are too many messages in the
     * queue.
     *
     * @param messages DESCRIBE THE PARAMETER
     */
    public void connectToRemoteNode(LinkedList messages) {
        if (state == STATE_USING_UDP) {
            try {
                SocketChannel channel = SocketChannel.open();
                channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
                channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
                channel.configureBlocking(false);
                boolean done = channel.connect(address);

                debug("Opening socket to " + address);

                Selector selector = ((WirePastryNode) getLocalNode()).getSelectorManager().getSelector();

                synchronized (selector) {
                    if (done) {
                        key = channel.register(selector, SelectionKey.OP_READ);
                    } else {
                        key = channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
                    }
                }

                setKey(key, new HelloMessage((WirePastryNode) getLocalNode(), nodeId));

                if (messages != null) {
                    Iterator i = messages.iterator();

                    while (i.hasNext()) {
                        Object o = i.next();
                        debug("Enqueueing message " + o + " into socket channel writer.");
                        writer.enqueue(o);
                    }
                }

                if (((WirePastryNode) getLocalNode()).inThread()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                } else {
                    ((WirePastryNode) getLocalNode()).getSelectorManager().getSelector().wakeup();
                }
            } catch (IOException e) {
                debug("IOException connecting to remote node " + address);

                // mark state as TCP in order to show this was unexpeceted
                state = STATE_USING_TCP;
                close(messages);
            }
        }
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param hm DESCRIBE THE PARAMETER
     */
    public void sendGreetingResponse(HelloMessage hm) {
    }

    /**
     * Method that is designed to be called by the SocketManager when it wishes
     * for this node handle to disconnect. Once this is called, the node handle
     * will finish writing out any pending objects in the queue, and then send a
     * DisconnectMessage to the remote node. Upon receiving this
     * DisconnectMessage, the remote node will finish writing out any pending
     * objects, and then will actually disconnect the socket.
     */
    public void disconnect() {
        debug("Received disconnect request... (state == " + state + ")");

        if (state == STATE_USING_TCP) {
            state = STATE_USING_UDP_WAITING_FOR_TCP_DISCONNECT;

            writer.enqueue(new DisconnectMessage());

            if (((WirePastryNode) getLocalNode()).inThread()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            } else {
                ((WirePastryNode) getLocalNode()).getSelectorManager().getSelector().wakeup();
            }
        } else {
            System.out.println("Recieved disconnect request at non-connected socket - very bad... (state == " + state + ")");
        }
    }

    /**
     * Requeired by the SelectionKeyHandler interface. Should never be called
     * (because we never accept connections).
     *
     * @param key DESCRIBE THE PARAMETER
     */
    public void accept(SelectionKey key) {
        System.out.println("PANIC: accept() called on WireNodeHandle!");
    }

    /**
     * Called by the socket manager whnever this node handle needs to complete
     * it's connection to it's remote node. Is specified by the
     * SelectionKeyHandler interface.
     *
     * @param key DESCRIBE THE PARAMETER
     */
    public void connect(SelectionKey key) {
        try {
            if (((SocketChannel) key.channel()).finishConnect()) {
                // deregister interest in connecting to this socket
                key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
            }

            debug("Found connectable channel - completed connection to " + address);
        } catch (ConnectException e) {
            debug("ERROR connecting - cancelling. " + e);
            close(writer.getQueue());
        } catch (SocketException e) {
            debug("ERROR connecting - cancelling. " + e);
            close(writer.getQueue());
        } catch (IOException e) {
            debug("ERROR connecting - cancelling. " + e);
            close(writer.getQueue());
        }
    }

    /**
     * Called by the socket manager whenever this node handle has registered
     * interest in writing to it's remote node, and the socket is ready for
     * writing. Is specified by the SelectionKeyHandler interface.
     *
     * @param key DESCRIBE THE PARAMETER
     */
    public void write(SelectionKey key) {
        if (state == STATE_USING_TCP) {
            ((WirePastryNode) getLocalNode()).getSocketManager().update(this);
        }

        try {
            // if writer is done, remove interest from writing
            if (writer.write((SocketChannel) key.channel())) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

                if (state == STATE_USING_UDP_WAITING_TO_DISCONNECT) {
                    close(null);
                }
            }
        } catch (IOException e) {
            debug("ERROR writing - cancelling. " + e);
            close(writer.getQueue());
        }
    }

    /**
     * Called by the socket manager whenever there is data to be read from this
     * node handle's remote node. Is specified from the SelectionKeyHandler
     * interface.
     *
     * @param key DESCRIBE THE PARAMETER
     */
    public void read(SelectionKey key) {
        if (state == STATE_USING_TCP) {
            ((WirePastryNode) getLocalNode()).getSocketManager().update(this);
        }

        try {
            // inform reader that data is available
            Object o = null;

            while ((o = reader.read((SocketChannel) key.channel())) != null) {
                if (o != null) {
                    if (o instanceof SocketCommandMessage) {
                        debug("Read socket message " + o + " - passing to node handle.");
                        receiveSocketMessage((SocketCommandMessage) o);
                    } else if (o instanceof SocketTransportMessage) {
                        SocketTransportMessage stm = (SocketTransportMessage) o;

                        if (stm.getDestination().equals(getLocalNode().getNodeId())) {
                            debug("Read message " + o + " - passing to pastry node.");
                            getLocalNode().receiveMessage((Message) stm.getObject());
                        } else {
                            debug("Read message " + o + " at " + nodeId + " for wrong nodeId " + stm.getDestination() + " - killing connection.");
                            throw new IOException("Incoming message was for incorrect node id.");
                        }
                    } else {
                        throw new IllegalArgumentException("Message " + o + " was not correctly wrapped.");
                    }
                }
            }
        } catch (ImproperlyFormattedMessageException e) {
            System.out.println("Improperly formatted message found during parsing - ignoring message... " + e);
            reader.reset();
        } catch (DeserializationException e) {
            System.out.println("An error occured during message deserialization - ignoring message...");
            reader.reset();
        } catch (IOException e) {
            debug("Error occurred during reading from " + address + " at " + getNodeId() + " - closing socket. " + e);
            close(writer.getQueue());
        }
    }

    /**
     * Is called by the SelectorManager every time the manager is awakened.
     * Checks to make sure that if we are waiting to write data, we are
     * registered as being interested in writing.
     */
    public void wakeup() {
        if (writer != null) {
            if (!key.isValid()) {
                System.out.println("ERROR: Recieved wakeup with non-valid key! (state == " + state + ")");
            } else {
                if (writer.isEmpty()) {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                } else {
                    if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    }
                }
            }
        }
    }

    /**
     * Ping the remote node now, and update the proximity metric. This method
     * ALWAYS uses UDP, even if there already is a TCP socket open.
     *
     * @return liveness of remote node.
     */
    public boolean pingImpl() {
        if (isLocal) {
            setProximity(0);
            return alive;
        }

        if (getLocalNode() != null) {
            if (((WireNodeHandle) getLocalNode().getLocalHandle()).getAddress().getAddress().equals(address.getAddress())) {
                setProximity(1);
                return alive;
            }
        }

        if (getLocalNode() != null) {

            long now = System.currentTimeMillis();

            if (now - lastpingtime < PING_THROTTLE * 1000) {
                return alive;
            }

            lastpingtime = now;

            // always send ping over UDP
            ((WirePastryNode) getLocalNode()).getDatagramManager().write(nodeId, address, new PingMessage(getLocalNode().getNodeId(), nodeId, 0, this));
        }

        return alive;
    }

    /**
     * Method which is called by the PingMessage right before it is going to be
     * sent across the wire. Marks the beginning of a ping as now.
     */
    public void pingStarted() {
        lastpingtime = System.currentTimeMillis();
    }

    /**
     * Method which is called by the SocketPingManager when a ping response
     * comes back for this node.
     */
    public void pingResponse() {
        if (isLocal) {
            debug("ERROR (pingResponse): Ping should never be sent to local node...");
            return;
        }

        long stoptime = System.currentTimeMillis();
        if (proximity() > (int) (stoptime - lastpingtime)) {
            setProximity((int) (stoptime - lastpingtime));
        }

        markAlive();
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     */
    public String toStringImpl() {
        return "[" + nodeId + " (" + address.getAddress().getHostAddress() + ":" + address.getPort() + ") on " + localnode + "]";
    }

    /**
     * Utility method for converting an InetAddress to an int (for comparison
     * purposes).
     *
     * @param address The address to convert
     * @return An int representation of the address
     */
    private int getAddress(InetAddress address) {
        byte[] tmp = address.getAddress();

        int i = (((int) tmp[0]) << 24) | (((int) tmp[1]) << 16) |
            (((int) tmp[2]) << 8) | (((int) tmp[3]));

        return i;
    }

    /**
     * Method which returns the size of an object about to be sent over the
     * wire. This size includes all of the wrapper messages (such as the Socket
     * Transport Message).
     *
     * @param obj The object
     * @return The total size the object and wrappers will occupy.
     * @exception IOException DESCRIBE THE EXCEPTION
     */
    private int messageSize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        oos.writeObject(new SocketTransportMessage(obj, nodeId));
        oos.flush();

        byte[] array = baos.toByteArray();

        return array.length;
    }

    /**
     * Private method used for closing the socket (if there is one present). It
     * also cancels the SelectionKey so that it is never called again.
     *
     * @param messages The messages that need to be rerouted (or null)
     */
    private void close(LinkedList messages) {
        try {
            debug("Closing and cleaning up socket.");

            if (key != null) {
                key.channel().close();
                key.cancel();
                key.attach(null);
            }

            // unexpected disconnect
            if (state == STATE_USING_TCP) {
                debug("Disconnect was unexpected - marking node as dead.");
                ((WirePastryNode) getLocalNode()).getSocketManager().closeSocket(this);
                markDead();
            }

            state = STATE_USING_UDP;

            if (messages != null) {
                debug("Messages contains " + messages.size() + " messages.");

                Iterator i = messages.iterator();

                while (i.hasNext()) {
                    Object msg = i.next();

                    if (msg instanceof SocketTransportMessage) {
                        SocketTransportMessage smsg = (SocketTransportMessage) msg;

                        // if it's a routeMessage, reroute it
                        if (smsg.getObject() instanceof RouteMessage) {
                            RouteMessage rmsg = (RouteMessage) smsg.getObject();
                            rmsg.nextHop = null;
                            getLocalNode().receiveMessage(rmsg);

                            debug("Rerouted message " + rmsg);
                        } else {
                            debug("Dropped message " + smsg + " on floor.");
                        }
                    } else {
                        debug("Dropped message " + msg + " on floor.");
                    }
                }
            }

            debug("Done rerouting messages...");
            writer = null;
            reader = null;
        } catch (IOException e) {
            System.out.println("IOException " + e + " disconnecting from remote node " + address);
            markDead();
        }
    }


    /**
     * Overridden in order to specify the default state (using UDP)
     *
     * @param ois DESCRIBE THE PARAMETER
     * @exception IOException DESCRIBE THE EXCEPTION
     * @exception ClassNotFoundException DESCRIBE THE EXCEPTION
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();

        state = STATE_USING_UDP;
    }
}
