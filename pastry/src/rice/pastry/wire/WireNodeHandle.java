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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

import rice.pastry.NodeId;
import rice.pastry.PastryNode;
import rice.pastry.dist.DistCoalesedNodeHandle;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.wire.exception.DeserializationException;
import rice.pastry.wire.exception.ImproperlyFormattedMessageException;
import rice.pastry.wire.exception.NodeIsDeadException;
import rice.pastry.wire.messaging.datagram.PingMessage;
import rice.pastry.wire.messaging.socket.DisconnectMessage;
import rice.pastry.wire.messaging.socket.HelloMessage;
import rice.pastry.wire.messaging.socket.HelloResponseMessage;
import rice.pastry.wire.messaging.socket.SocketCommandMessage;
import rice.pastry.wire.messaging.socket.SocketTransportMessage;

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
  public final static int STATE_USING_UDP = -1;
  /**
   * DESCRIBE THE FIELD
   */
  public final static int STATE_USING_TCP = -2;
  /**
   * DESCRIBE THE FIELD
   */
  public final static int STATE_USING_UDP_WAITING_FOR_TCP_DISCONNECT = -3;
  /**
   * DESCRIBE THE FIELD
   */
  public final static int STATE_USING_UDP_WAITING_TO_DISCONNECT = -4;

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

    setState(STATE_USING_UDP,"ctor");
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

    setState(STATE_USING_UDP,"ctor");

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
   * Gets the WriteEnabled attribute of the WireNodeHandle object
   *
   * @return The WriteEnabled value
   */
  public boolean isWriteEnabled() {
    return ((key.interestOps() & SelectionKey.OP_WRITE) != 0);
  }

  /**
   * Method which sets the SelectionKey of this node handle. Is designed to be
   * called whenever a TCP connection has been established. All pending message
   * will now be sent over TCP using the socket attached to this SelectionKey.
   * If a socket has already been established, there is a protocol for
   * determining which socket to close. If the address:port of the local is less
   * than that of the remote, this node ignores the incoming key. Otherwise, it
   * will kill it's own socket and use the new key as the "real" socket. NOTE:
   * There are known problems with this implementation, especially under high
   * stress.
   *
   * @param key The new SelectionKey
   * @param scm The new Key value
   */
  public synchronized void setKey(SelectionKey key, SocketCommandMessage scm) {
    debug("Got new key  (state == " + state + ")");
    
    // if we're currently using UDP, accept the connection as usual
    if (state == STATE_USING_UDP) {
      this.key = key;
      key.attach(this);

      ((WirePastryNode) getLocalNode()).getSocketManager().openSocket(this);
      //((WirePastryNode) getLocalNode()).getDatagramManager().resetAckNumber(nodeId);

      reader = new SocketChannelReader((WirePastryNode) getLocalNode(),this);
      writer = new SocketChannelWriter((WirePastryNode) getLocalNode(), scm, key, this);

      setState(STATE_USING_TCP,"setKey1:"+scm);
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
          this.key.channel().close();
          this.key.cancel();
          this.key.attach(null);

          writer.reset(scm);
        } catch (IOException e) {
          System.out.println("ERROR closing unnecessary socket: " + e);
        }
        writer.setKey(key);
        // use new socket
        this.key = key;
        setState(STATE_USING_TCP,"setKey2a:"+scm);
        key.attach(this);

        debug("Killing our socket, using new one...");
      } else {
        wireDebug("setKey2b:"+scm);

        // use our socket and ignore the new one
//        key.cancel();
        //key.channel().close();
        key.attach(new StaleSKH());
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
        setState(STATE_USING_UDP_WAITING_TO_DISCONNECT,"recSockMsg");
        ((WirePastryNode) getLocalNode()).getSocketManager().closeSocket(this);
//        enableWrite(true);
// TODO: find out why we enabledWriting here
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

  public void notifyKilled() {
    SocketChannelWriter scw = writer;
    if (scw != null) {
      writer.notifyKilled(); 
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

      wireDebug("ENQ:"+msg);

      String s1 = null;
      if (writer!=null) {
        s1 = ""+writer.queueSize(); 
      }
      try {
        String s2 = null;
        if (key!=null) {
          s2 = ""+key.interestOps(); 
        }
        wireDebug("DBG:"+s1+","+s2);
      } catch (CancelledKeyException cke) {
        SelectorManager selMgr = ((WirePastryNode)getLocalNode()).getSelectorManager();
        if (!selMgr.isAlive()) {
          SocketChannelWriter tempWriter = writer;
          if (writer!=null) {
            Iterator messages = writer.getQueue();
            if (messages != null) {
              notifyPotentiallyLostMessage(messages);
            }
          }
          throw new NodeIsDeadException(cke);
        } else {
          closeDueToError(); 
        }       
      }

      switch (state) {
        case STATE_USING_TCP:
          writer.enqueue(new SocketTransportMessage(msg, nodeId));

          // enqueue now does this for us
          /*
           *  if (((WirePastryNode) getLocalNode()).inThread()) {
           *  enableWrite(true);
           *  } else {
           *  SelectorManager selMgr = ((WirePastryNode) getLocalNode()).getSelectorManager();
           *  Selector sel = selMgr.getSelector();
           *  try {
           *  sel.wakeup();
           *  } catch (NullPointerException npe) {
           *  if (!selMgr.isAlive()) {
           *  throw new NodeIsDeadException(npe);
           *  } else {
           *  throw npe;
           *  }
           *  }
           *  }
           */
          break;
        case STATE_USING_UDP:
          try {
            // if message is small enough, send via UDP
            if (messageSize(msg) <= MAX_UDP_MESSAGE_SIZE) {
              debug("Message is small enough to go over UDP - sending.");
              ((WirePastryNode) getLocalNode()).getDatagramManager().write(nodeId, address, msg);
            } else {
              debug("Message is too large - open up socket!");
              LinkedList list = new LinkedList();
              list.addFirst(new SocketTransportMessage(msg, nodeId));

              connectToRemoteNode(list.iterator());
            }
          } catch (IOException e) {
            System.out.println("IOException serializing message " + msg + " - cancelling message.");
            e.printStackTrace();
          }
          break;
        default:
          // if we're waiting to disconnect, send message over UDP anyway
          ((WirePastryNode) getLocalNode()).getDatagramManager().write(nodeId, address, msg);
          break;
      }

      /*
       *  / check to see if socket is open
       *  if (state != STATE_USING_TCP) {
       *  try {
       *  if (state == STATE_USING_UDP) {
       *  / if message is small enough, send via UDP
       *  if (messageSize(msg) <= MAX_UDP_MESSAGE_SIZE) {
       *  debug("Message is small enough to go over UDP - sending.");
       *  ((WirePastryNode) getLocalNode()).getDatagramManager().write(nodeId, address, msg);
       *  } else {
       *  debug("Message is too large - open up socket!");
       *  LinkedList list = new LinkedList();
       *  list.addFirst(new SocketTransportMessage(msg));
       *  connectToRemoteNode(list);
       *  }
       *  } else {
       *  / if we're waiting to disconnect, send message over UDP anyway
       *  ((WirePastryNode) getLocalNode()).getDatagramManager().write(nodeId, address, msg);
       *  }
       *  } catch (IOException e) {
       *  System.out.println("IOException serializing message " + msg + " - cancelling message.");
       *  }
       *  } else {
       *  writer.enqueue(new SocketTransportMessage(msg));
       *  if (((WirePastryNode) getLocalNode()).inThread()) {
       *  enableWrite(true);
       *  /          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
       *  } else {
       *  SelectorManager selMgr = ((WirePastryNode) getLocalNode()).getSelectorManager();
       *  Selector sel = selMgr.getSelector();
       *  try {
       *  sel.wakeup();
       *  } catch (NullPointerException npe) {
       *  if (!selMgr.isAlive()) {
       *  throw new NodeIsDeadException(npe);
       *  } else {
       *  throw npe;
       *  }
       *  }
       *  }
       *  debug("Enqueued message " + msg + " for writing in socket writer.");
       *  }
       */
    }
  }

  public void notifyPotentiallyLostMessage(Iterator i) {
    while (i.hasNext()) {
      Object o = i.next();
      System.err.println("WNH: Potentially lost the message:"+o);
    }
  }


  /**
   * Is called by the SelectorManager every time the manager is awakened. Checks
   * to make sure that if we are waiting to write data, we are registered as
   * being interested in writing.
   */
  public void wakeup() {
  }

  /**
   * Method which initiates a connection to a remote node. This is done by
   * connecting to the server socket on the remote node. This can be called by
   * the receiveMessageImpl, if there is a too-big message waiting to be sent,
   * or by the TransmissionManager if there are too many messages in the queue.
   *
   * @param messages DESCRIBE THE PARAMETER
   */
  public void connectToRemoteNode(Iterator messages) {
    if (state == STATE_USING_UDP) {
      try {
        SocketChannel channel = SocketChannel.open();
        channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
        channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
        channel.configureBlocking(false);

        boolean done = channel.connect(address);

        debug("Opening socket to " + address);

        SelectorManager selMgr = ((WirePastryNode) getLocalNode()).getSelectorManager();
        Selector selector = selMgr.getSelector();

        synchronized (selector) {
          if (done) {
            key = channel.register(selector, SelectionKey.OP_READ);
            // | SelectionKey.OP_WRITE);
          } else {
            try {
              key = channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
              // | SelectionKey.OP_WRITE);
            } catch (NullPointerException npe) {
              if (!selMgr.isAlive()) {
                if (messages != null) {
                  notifyPotentiallyLostMessage(messages);
                }
                throw new NodeIsDeadException(npe);
              } else {
                if (messages != null) {
                  notifyPotentiallyLostMessage(messages);
                }
                throw npe;
              }
            }
          }
        }

        setKey(key, new HelloMessage((WirePastryNode) getLocalNode(), nodeId));

        if (messages != null) {
          Iterator i = messages;
          while (i.hasNext()) {
            Object o = i.next();

            debug("Enqueueing message " + o + " into socket channel writer.");
            writer.enqueue(o);
          }
        }
        // enqueue does this for us
        /*
         *  if (((WirePastryNode) getLocalNode()).inThread()) {
         *  enableWrite(true);
         *  /          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
         *  } else {
         *  ((WirePastryNode) getLocalNode()).getSelectorManager().getSelector().wakeup();
         *  }
         */
      } catch (IOException e) {
        debug("IOException connecting to remote node " + address);
        e.printStackTrace();
        System.out.println("WireNodeHandle.connectToRemoteNode(): IOException connecting to remote node " + address);


        // mark state as TCP in order to show this was unexpeceted
        setState(STATE_USING_TCP,"connToRemoteNode, exception");
        close(messages);
      }
    } else {
      // enqueue all those messages
      if (messages != null) {
        Iterator i = messages;

        while (i.hasNext()) {
          Object o = i.next();
          debug("Enqueueing message " + o + " into socket channel writer.");
          writer.enqueue(o);
        }
      }
      
      // state is not udp
      // TODO implement, or throw exception
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
  public synchronized void disconnect() {
    debug("Received disconnect request... (state == " + state + ")");

    if (state == STATE_USING_TCP) {
      setState(STATE_USING_UDP_WAITING_FOR_TCP_DISCONNECT,"disconnect()");

      writer.enqueue(new DisconnectMessage());

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
        SelectorManager selMgr = ((WirePastryNode) getLocalNode()).getSelectorManager();
        Selector selector = selMgr.getSelector();
        synchronized (selector) {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
        }
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
        //key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
//        enableWrite(false);
        if (state == STATE_USING_UDP_WAITING_TO_DISCONNECT) {
          close(null);
        }
      }
    } catch (IOException e) {
      debug("ERROR writing - cancelling. " + e);
      close(writer.getQueue());
    }
  }
  
  transient PrintStream outputStream = null;
  void wireDebug(String s) {
    if (!Wire.outputDebug) return;
    synchronized(Wire.outputStreamLock) {
      try {
        if (outputStream == null) {
          String r = null;
          if (localnode != null) {
            r = localnode.getId().toString();
          } else {
            return;
          }
          String t = "WNH "+r+"->"+getNodeId().toString()+".txt";
          outputStream = new PrintStream(new FileOutputStream(t)); 
        }
        outputStream.println(s);
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
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


    // this is a hack because we may have been concurrently closed
    SocketChannelReader tempReader = reader;
    SocketChannelWriter tempWriter = writer;

    if ((tempReader != null) && (tempWriter != null)) {
      try {
        // inform reader that data is available
        Object o = null;
      
        while ((o = tempReader.read((SocketChannel) key.channel())) != null) {                    
          if (o != null) {
//            if (getNodeId().toString().startsWith("<0x5")) {
//              System.out.println("A.read");
//            }
            if (o instanceof SocketCommandMessage) {
              debug("Read socket message " + o + " - passing to node handle.");
              receiveSocketMessage((SocketCommandMessage) o);
            } else if (o instanceof SocketTransportMessage) {
              SocketTransportMessage stm = (SocketTransportMessage) o;
  
              if (stm.getDestination().equals(getLocalNode().getNodeId())) {
//                System.err.println("Read message " + o + " - passing to pastry node.");
                debug("Read message " + o + " - passing to pastry node.");
                wireDebug("DBG:"+System.identityHashCode(stm.getObject())+","+stm.getObject());
                wireDebug("REC:"+stm.getObject());
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
        tempReader.reset();
      } catch (DeserializationException e) {
        System.out.println("An error occured during message deserialization - ignoring message...");
        tempReader.reset();
      } catch (IOException e) {
        System.err.println("Error occurred during reading from " + address + " at " + getNodeId() + " - closing socket. " + e);
        debug("Error occurred during reading from " + address + " at " + getNodeId() + " - closing socket. " + e);
        close(tempWriter.getQueue());
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
   * Method which is called by the SocketPingManager when a ping response comes
   * back for this node.
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
   * Sets the State attribute of the WireNodeHandle object
   *
   * @param newState The new State value
   */
  private void setState(int newState, String reason) {
    if ((state == STATE_USING_UDP) && (newState == STATE_USING_TCP)) {
      Wire.acquireFileDescriptor();
    }

    if ((newState == STATE_USING_UDP) && (state == STATE_USING_TCP)) {
      Wire.releaseFileDescriptor();
    }

    if ((newState == STATE_USING_UDP_WAITING_FOR_TCP_DISCONNECT) && (state == STATE_USING_TCP)) {
      Wire.releaseingFileDescriptor();
    }

    if ((newState == STATE_USING_UDP) && (state == STATE_USING_UDP_WAITING_FOR_TCP_DISCONNECT)) {
      Wire.doneReleaseingFileDescriptor();
    }

    state = newState;
    
     String newStateString = "unknown";
     switch (newState) {
     case STATE_USING_TCP:
     newStateString = "USING_TCP";
     break;
     case STATE_USING_UDP:
     newStateString = "USING_UDP";
     break;
     case STATE_USING_UDP_WAITING_FOR_TCP_DISCONNECT:
     newStateString = "USING_UDP_WAITING_FOR_TCP_DISCONNECT";
     break;
     case STATE_USING_UDP_WAITING_TO_DISCONNECT:
     newStateString = "USING_UDP_WAITING_TO_DISCONNECT";
     }
     wireDebug("DBG:setState("+newStateString+"):"+reason);
     
  }

  /**
   * Method which returns the size of an object about to be sent over the wire.
   * This size includes all of the wrapper messages (such as the Socket
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

  void closeDueToError() {
    if (writer != null) {
      close(writer.getQueue());
    } else {
      close(null);
    }
  }

  /**
   * Private method used for closing the socket (if there is one present). It
   * also cancels the SelectionKey so that it is never called again.
   *
   * @param messages The messages that need to be rerouted (or null)
   */
  private void close(Iterator messages) {
    synchronized(localnode) {
      synchronized(this) {
      if (state == STATE_USING_UDP) {
        return;
      }
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
  
        setState(STATE_USING_UDP,"close()");
        Exception e = new Exception("Dump");
        e.printStackTrace(outputStream);
  
        if (messages != null) {
          debug("Messages contains " + writer.queueSize() + " messages.");
  
          Iterator i = messages;
  
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
                wireDebug("DRP:"+smsg);
                debug("Dropped message " + smsg + " on floor.");
              }
            } else {
              debug("Dropped message " + msg + " on floor.");
              wireDebug("DRP:"+msg);
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

    setState(STATE_USING_UDP,"readObject()");
  }
}
