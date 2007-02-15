/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.pastry.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.messaging.Message;
import rice.pastry.socket.messaging.*;
import rice.selector.SelectionKeyHandler;

/**
 * Private class which is tasked with reading the greeting message off of a
 * newly connected socket. This greeting message says who the socket is coming
 * from, and allows the connected to hand the socket off the appropriate node
 * handle.
 *
 * @version $Id: SocketCollectionManager.java 3061 2006-02-14 00:56:04Z jeffh $
 * @author jeffh
 */
class SocketManager extends SelectionKeyHandler {

  /**
   * 
   */
  private final SocketCollectionManager manager;

  // the key to read from
  protected SelectionKey key;
  
  // the channel we are associated with
  protected SocketChannel channel;

  // the reader reading data off of the stream
  protected SocketChannelReader reader;

  // the writer (in case it is necessary)
  protected SocketChannelWriter writer;
  
  // the timer we use to check for stalled nodes
  protected rice.selector.TimerTask timer;

  // the node handle we're talking to
  protected SourceRoute path;
  
  // whether or not this is a bootstrap socket - if so, we fake the address 
  // and die once the the message has been sent
  protected boolean bootstrap;

  /**
   * Constructor which accepts an incoming connection, represented by the
   * selection key. This constructor builds a new SocketManager, and waits
   * until the greeting message is read from the other end. Once the greeting
   * is received, the manager makes sure that a socket for this handle is not
   * already open, and then proceeds as normal.
   *
   * @param key The server accepting key for the channel
   * @param manager TODO
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  public SocketManager(SocketCollectionManager manager, SelectionKey key) throws IOException {
    this.manager = manager;
    this.reader = new SocketChannelReader(manager.pastryNode, null);
    this.writer = new SocketChannelWriter(manager.pastryNode, null);
    this.bootstrap = false;
    acceptConnection(key);
  }
  
  /**
   * Constructor which creates an outgoing connection to the given node
   * handle using the provided address as a source route intermediate node. 
   * This creates the connection by building the socket and sending
   * accross the greeting message. Once the response greeting message is
   * received, everything proceeds as normal.
   * @param manager TODO
   *
   * @param address The ultimate destination of this socket
   * @param proxy The intermediate destination of this socket (if a source route)
   * @exception IOException An error
   */
  public SocketManager(SocketCollectionManager manager, SourceRoute path, boolean bootstrap) throws IOException {
    this.manager = manager;
    this.reader = new SocketChannelReader(manager.pastryNode, path.reverse());
    this.writer = new SocketChannelWriter(manager.pastryNode, path);
    this.bootstrap = bootstrap;
    
    if (manager.logger.level <= Logger.FINE) manager.logger.log("Opening connection with path " + path);
    
    // build the entire connection
    createConnection(path);

    
    
//    ArrayList tempList = new ArrayList();    
//    for (int i=1; i<path.getNumHops(); i++) {
//      tempList.add(SocketCollectionManager.HEADER_SOURCE_ROUTE);
//      tempList.add(SocketChannelRepeater.encodeHeader(path.getHop(i)));
//    }     
//    tempList.add(SocketCollectionManager.HEADER_DIRECT);
//    tempList.add(new byte[4]);
//     
//    int sizeToAllocate = 0;     
//    Iterator i = tempList.iterator();
//    while(i.hasNext()) {
//      byte[] next = (byte[])i.next();
//      sizeToAllocate+=next.length;
//    }
//    
//    byte[] toWriteBytes = new byte[sizeToAllocate];
//    int ptr = 0;
//    i = tempList.iterator();
//    while(i.hasNext()) {
//      byte[] next = (byte[])i.next();
//      System.arraycopy(next, 0, toWriteBytes, ptr, next.length);
//      ptr+=next.length;
//    }     
    send(new SocketBuffer(path, 0));
    
//    for (int i=1; i<path.getNumHops(); i++) {
//      send(SocketCollectionManager.HEADER_SOURCE_ROUTE);
//      send(SocketChannelRepeater.encodeHeader(path.getHop(i)));
//    }
//    
//    send(SocketCollectionManager.HEADER_DIRECT);
//    send(new byte[4]);
    
    if (! bootstrap)
      send(new SocketBuffer(path.reverse(manager.localAddress)));
  }
  
  public String toString() {
    return "SM "+channel; 
  }
  
  /**
   * Method which initiates a shutdown of this socket by calling 
   * shutdownOutput().  This has the effect of removing the manager from
   * the open list.
   */
  public void shutdown() {
    try {
      if (manager.logger.level <= Logger.FINE) manager.logger.log("Shutting down output on connection with path " + path);
      
      if (channel != null)
        channel.socket().shutdownOutput();
      else
        if (manager.logger.level <= Logger.SEVERE) manager.logger.log( "ERROR: Unable to shutdown output on channel; channel is null!");

      manager.socketClosed(path, this);
      manager.pastryNode.getEnvironment().getSelectorManager().modifyKey(key);
    } catch (IOException e) {
      if (manager.logger.level <= Logger.SEVERE) manager.logger.log( "ERROR: Received exception " + e + " while shutting down output.");
      close();
    }
  }

  /**
   * Method which closes down this socket manager, by closing the socket,
   * cancelling the key and setting the key to be interested in nothing
   */
  public void close() {
    try {
      if (manager.logger.level <= Logger.FINE) {
        if (path != null) {
          manager.logger.log("Closing connection with path " + path);
        } else {
          manager.logger.log("Closing connection to " + (InetSocketAddress) channel.socket().getRemoteSocketAddress());
        }
      }
      
      // todo, need to monitor all openings, sourceroute, accepted, etc.
      if (manager.pastryNode != null)
        manager.pastryNode.broadcastChannelClosed((InetSocketAddress) channel.socket().getRemoteSocketAddress());
      
      clearTimer();
      
      if (key != null) {
        if (manager.logger.level <= Logger.WARNING) {
          if (!manager.pastryNode.getEnvironment().getSelectorManager().isSelectorThread()) {
            manager.logger.logException("WARNING: cancelling key:"+key+" on the wrong thread.", new Exception("Stack Trace"));
          }
        }
        key.cancel();
        key.attach(null);
        key = null;
      }
      
      manager.unIdentifiedSM.remove(this);
      
      if (channel != null) 
        channel.close();

      if (path != null) {
        manager.socketClosed(path, this);

        Iterator i = writer.getQueue().iterator();
        writer.reset();

        /**
         * Here, if we have not been declared dead, then we attempt to resend
         * the messages. However, if we have been declared dead, we reroute
         * the route messages via the pastry node, but delete any messages
         * routed directly.
         */
        while (i.hasNext()) {
          Object o = i.next();

          if ((o instanceof SocketBuffer) && (manager.manager != null)) 
            manager.manager.reroute(path.getLastHop(), (SocketBuffer) o);
        } 

        path = null;
      }
    } catch (IOException e) {
      if (manager.logger.level <= Logger.SEVERE) manager.logger.log( "ERROR: Recevied exception " + e + " while closing socket!");
    }
  }

  public void send(Message msg) throws IOException {
    PRawMessage rm;
    if (msg instanceof PRawMessage) {
      rm = (PRawMessage)msg; 
    } else {
      rm = new PJavaSerializedMessage(msg); 
    }
    // todo, pool
    final SocketBuffer buffer = new SocketBuffer(manager.defaultDeserializer, manager.pastryNode);
    buffer.serialize(rm, true);
    send(buffer);
  }

  
  /**
   * The entry point for outgoing messages - messages from here are ensocketQueued
   * for transport to the remote node
   *
   * @param message DESCRIBE THE PARAMETER
   */
  public void send(final SocketBuffer message) {
    writer.enqueue(message);

    if (key != null) 
      manager.pastryNode.getEnvironment().getSelectorManager().modifyKey(key);
  }

  /**
   * Method which should change the interestOps of the handler's key. This
   * method should *ONLY* be called by the selection thread in the context of
   * a select().
   *
   * @param key The key in question
   */
  public synchronized void modifyKey(SelectionKey key) {
    if (channel.socket().isOutputShutdown()) {
      key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
      clearTimer();
    } else if ((! writer.isEmpty()) && ((key.interestOps() & SelectionKey.OP_WRITE) == 0)) {
      key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
      setTimer();
    }
  }

  /**
   * Specified by the SelectionKeyHandler interface - calling this tells this
   * socket manager that the connection has completed and we can now
   * read/write.
   *
   * @param key The key which is connectable.
   */
  public void connect(SelectionKey key) {
    try {
      // deregister interest in connecting to this socket
      if (channel.finishConnect()) 
        key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);

      manager.manager.markAlive(path);

      if (manager.logger.level <= Logger.FINE) manager.logger.log("(SM) Found connectable channel - completed connection");
    } catch (Exception e) {
      if (manager.logger.level <= Logger.FINE) manager.logger.logException(
          "(SM) Unable to connect to path " + path + " (" + e + ") marking as dead.",e);
      manager.manager.markDead(path);

      close();
    }
  }

  /**
   * Reads from the socket attached to this connector.
   *
   * @param key The selection key for this manager
   */
  public void read(SelectionKey key) {
    try {
      SocketBuffer o = reader.read(channel);

      if (o != null) {
        if (manager.logger.level <= Logger.FINE) manager.logger.log("(SM) Read message " + o + " from socket.");
        
        receive(o);
      }
    } catch (IOException e) {
      if (manager.logger.level <= Logger.FINE) manager.logger.log("(SM) WARNING " + e + " reading - cancelling.");        
      
      // if it's not a bootstrap path, and we didn't close this socket's output,
      // then check to see if the remote address is dead or just closing a socket
      if ((path != null) && 
          (! ((SocketChannel) key.channel()).socket().isOutputShutdown()))
        manager.checkLiveness(path);
      
      close();
    } catch (OutOfMemoryError oome) {
      close(); 
    }
  }

  /**
   * Writes to the socket attached to this socket manager.
   *
   * @param key The selection key for this manager
   */
  public synchronized void write(SelectionKey key) {
    try {        
      clearTimer();
      
      if (writer.write(channel)) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        
        if (bootstrap) 
          close();
      } else {
        setTimer();
      }
    } catch (IOException e) {
      if (manager.logger.level <= Logger.WARNING) manager.logger.log( "(SM) ERROR " + e + " writing - cancelling.");
      close();
    }
  }

  /**
   * Accepts a new connection on the given key
   *
   * @param serverKey The server socket key
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected void acceptConnection(SelectionKey key) throws IOException {      
    this.channel = (SocketChannel) key.channel();
    this.key = manager.pastryNode.getEnvironment().getSelectorManager().register(key.channel(), this, 0);
    this.key.interestOps(SelectionKey.OP_READ);
    
    if (manager.logger.level <= Logger.FINE) manager.logger.log(
        "(SM) Accepted connection from " + 
        channel.socket().getRemoteSocketAddress());
  }

  /**
   * Creates the outgoing socket to the remote handle
   *
   * @param address The accress to connect to
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected void createConnection(final SourceRoute path) throws IOException {
    this.path = path;
    this.channel = SocketChannel.open();
    this.channel.socket().setSendBufferSize(manager.SOCKET_BUFFER_SIZE);
    this.channel.socket().setReceiveBufferSize(manager.SOCKET_BUFFER_SIZE);
    this.channel.configureBlocking(false);
    this.key = manager.pastryNode.getEnvironment().getSelectorManager().register(channel, this, 0);
    
    if (manager.logger.level <= Logger.FINE) manager.logger.log("(SM) Initiating socket connection to path " + path);
    
    manager.pastryNode.broadcastChannelOpened(path.getFirstHop().getAddress(manager.localAddress), NetworkListener.REASON_NORMAL);

    if (this.channel.connect(path.getFirstHop().getAddress(manager.localAddress))) 
      this.key.interestOps(SelectionKey.OP_READ);
    else 
      this.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
  }

  // short circuit the deserialization step
  class SMDeserializer implements MessageDeserializer {

    public rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type, int priority, rice.p2p.commonapi.NodeHandle sender) throws IOException {
      byte version;
      switch (type) {        
        case SourceRoute.TYPE:
          SourceRoute tempPath = SourceRoute.build(buf);
          if (path == null) {
            path = tempPath;
            manager.socketOpened(path, SocketManager.this);
            manager.manager.markAlive(path);
            writer.setPath(path);
            reader.setPath(path.reverse());
  
            if (manager.logger.level <= Logger.FINE) manager.logger.log("Read open connection with path " + path);              
          } else {
            if (manager.logger.level <= Logger.SEVERE) manager.logger.log( "SERIOUS ERROR: Received duplicate path assignments: " + path + " and " + tempPath);
          }
          return null;
        case NodeIdRequestMessage.TYPE:
          version = buf.readByte();
          switch(version) {
            case 0:
              send(new NodeIdResponseMessage(manager.pastryNode.getNodeId(), manager.localAddress.getEpoch()));
              break;
            default:
              throw new IOException("Unknown Version: "+version);
          }     
          return null;
        case LeafSetRequestMessage.TYPE:
          version = buf.readByte();
          switch(version) {
            case 0:
              send(new LeafSetResponseMessage(manager.pastryNode.getLeafSet()));
              break;
            default:
              throw new IOException("Unknown Version: "+version);
          }     
          return null;
        case RoutesRequestMessage.TYPE:
          version = buf.readByte();
          switch(version) {
            case 0:
              send(new RoutesResponseMessage((SourceRoute[]) manager.manager.getBest().values().toArray(new SourceRoute[0])));
              break;
            default:
              throw new IOException("Unknown Version: "+version);
          }     
          return null;
        case RouteRowRequestMessage.TYPE:          
          version = buf.readByte();
          switch(version) {
            case 0:
//              RouteRowRequestMessage rrMessage = new RouteRowRequestMessage(buf.readInt());
//              send(new RouteRowResponseMessage(manager.pastryNode.getRoutingTable().getRow(rrMessage.getRow())));
              send(new RouteRowResponseMessage(manager.pastryNode.getRoutingTable().getRow(buf.readInt())));
              break;
            default:
              throw new IOException("Unknown Version: "+version);
          }     
          return null;
        default:
          if (manager.logger.level <= Logger.SEVERE) manager.logger.log( "SERIOUS ERROR: Received unknown message address: "+0+"type:"+type);
      }
      return null;
    }    
  }
  
MessageDeserializer deserializer = new SMDeserializer();
  
  /**
   * Method which is called once a message is received off of the wire If it's
   * for us, it's handled here, otherwise, it's passed to the pastry node.
   *
   * @param message The receved message
   */
  /**
   * Method which is called once a message is received off of the wire If it's
   * for us, it's handled here, otherwise, it's passed to the pastry node.
   *
   * @param message The receved message
   */
  protected void receive(SocketBuffer delivery) {
    if (delivery.getAddress() == 0) {
      // short circuit, these are the internal messages that Socket handles
      try {
        delivery.deserialize(deserializer);
      } catch (IOException ioe) {
        if (manager.logger.level <= Logger.SEVERE) manager.logger.logException("Internal error while deserializing.",ioe); 
      }
    } else {
      long start = manager.pastryNode.getEnvironment().getTimeSource().currentTimeMillis();
      manager.pastryNode.receiveMessage(delivery);
      if (manager.logger.level <= Logger.FINER) manager.logger.log( "ST: " + (manager.pastryNode.getEnvironment().getTimeSource().currentTimeMillis() - start) + " deliver of " + delivery);
      return;          
    }
  }
  
  /**
   * Internal method which sets the internal timer
   */
  protected void setTimer() {
    if (this.timer == null) {
      this.timer = new rice.selector.TimerTask() {
        public void run() {
          if (manager.logger.level <= Logger.FINE) SocketManager.this.manager.logger.log("WRITE_TIMER::Timer expired, checking liveness...");
          SocketManager.this.manager.manager.checkLiveness(path.getLastHop());
        }
      };
      
      manager.pastryNode.getEnvironment().getSelectorManager().schedule(this.timer, manager.WRITE_WAIT_TIME);
    }
  }
  
  /**
   * Internal method which clears the internal timer
   */
  protected void clearTimer() {
    if (this.timer != null)
      this.timer.cancel();
    
    this.timer = null;
  }
}