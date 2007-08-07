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
package org.mpisws.p2p.transport.wire;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.Continuation;
import rice.Destructable;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.selector.SelectionKeyHandler;

public class TCPLayer extends SelectionKeyHandler {
  public static final Map<String, Integer> OPTIONS;  
  static {
    Map<String, Integer> map = new HashMap<String, Integer>();
    map.put(WireTransportLayer.OPTION_TRANSPORT_TYPE, WireTransportLayer.TRANSPORT_TYPE_GUARANTEED);
    OPTIONS = Collections.unmodifiableMap(map);    
  }
  
  // the number of sockets where we start closing other sockets
  public final int MAX_OPEN_SOCKETS;
  
  // the size of the buffers for the socket
  public final int SOCKET_BUFFER_SIZE;
  
  WireTransportLayerImpl wire;
    
  // the key to accept from
  private SelectionKey key;
  
  // the buffer used to read the header
  private ByteBuffer buffer;
  
  Logger logger;
  
//  Set<SocketAcceptor> pending;
  /**
   * Which socket to collect.
   */
  LinkedHashMap<SocketManager, SocketManager> sockets;

  public TCPLayer(WireTransportLayerImpl wire) throws IOException {
    this.wire = wire;
    this.logger = wire.environment.getLogManager().getLogger(TCPLayer.class, null);
  
    Parameters p = wire.environment.getParameters();
    MAX_OPEN_SOCKETS = p.getInt("pastry_socket_scm_max_open_sockets");
    SOCKET_BUFFER_SIZE = p.getInt("pastry_socket_scm_socket_buffer_size"); // 32768

    ServerSocketChannel temp = null; // just to clean up after the exception
    sockets = new LinkedHashMap<SocketManager, SocketManager>(10,0.75f,true);    
//    pending = new HashSet<SocketAcceptor>();
    
    // bind to port
    final ServerSocketChannel channel = ServerSocketChannel.open();
    temp = channel;
    channel.configureBlocking(false);
    channel.socket().setReuseAddress(true);
    channel.socket().bind(wire.bindAddress);
    
    this.key = wire.environment.getSelectorManager().register(channel, this, SelectionKey.OP_ACCEPT);
  }

  public SocketRequestHandle<InetSocketAddress> openSocket(
      InetSocketAddress destination, 
      SocketCallback<InetSocketAddress> deliverSocketToMe,
      Map<String, Integer> options) {
    if (logger.level <= Logger.FINEST) {
      logger.logException("openSocket("+destination+")", new Exception("Stack Trace"));
    } else {
      if (logger.level <= Logger.FINE) logger.log("openSocket("+destination+")");
    }
    if (deliverSocketToMe == null) throw new IllegalArgumentException("deliverSocketToMe must be non-null!");
    try {
      synchronized (sockets) {
        SocketManager sm = new SocketManager(this, destination, deliverSocketToMe, options); 
        sockets.put(sm, sm);
        return sm;
      }
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("GOT ERROR " + e + " OPENING PATH - MARKING PATH " + destination + " AS DEAD!",e);
      SocketRequestHandle can = new SocketRequestHandleImpl<InetSocketAddress>(destination, options);
      deliverSocketToMe.receiveException(can, e);
      return can;
    }
  }

  /**
   * Method which cloeses a socket to a given remote node handle, and updates
   * the bookkeeping to keep track of this closing.  Note that this method does
   * not completely close the socket, rather,  it simply calls shutdown(), which
   * starts the shutdown process.
   *
   * @param address The address of the remote node
   */
//  protected void closeSocket(InetSocketAddress addr) {
//    synchronized (sockets) {
//      if (sockets.containsKey(addr)) {
//        ((SocketManager) sockets.get(addr)).shutdown();
//      } else {
//        if (logger.level <= Logger.SEVERE) logger.log( "(SCM) SERIOUS ERROR: Request to close socket to non-open handle to path " + addr);
//      }
//    }
//  }

  public void destroy() {
    if (logger.level <= Logger.INFO) logger.log("destroy()");

//    resigned = true;
//    
//    while (socketQueue.size() > 0) 
//      ((SocketManager) sockets.get(socketQueue.getFirst())).close();
//    
//    while (sourceRouteQueue.size() > 0) 
//      ((SourceRouteManager) sourceRouteQueue.getFirst()).close();
//        
//    // anything somehow left in sockets?
//    while (sockets.size() > 0) {
//      ((SocketManager) sockets.values().iterator().next()).close();   
//    }
    
    // any left in un
//    while (unIdentifiedSM.size() > 0) {
//      ((SocketManager) unIdentifiedSM.iterator().next()).close();
//    }
    
    try {
      key.channel().close();
      key.cancel();    
      key.attach(null);
    } catch (IOException ioe) {
      wire.errorHandler.receivedException(null, ioe); 
    }
  }

  public void acceptSockets(final boolean b) {
    Runnable r = new Runnable(){    
      public void run() {
        if (b) {
          key.interestOps(key.interestOps() | SelectionKey.OP_ACCEPT);
        } else {
          key.interestOps(key.interestOps() & ~SelectionKey.OP_ACCEPT);
        }
      }    
    };
    
    // thread safety
    if (wire.environment.getSelectorManager().isSelectorThread()) {
      r.run();
    } else {
      wire.environment.getSelectorManager().invoke(r);
    }
  }
  
  /**
   * Specified by the SelectionKeyHandler interface. Is called whenever a key
   * has become acceptable, representing an incoming connection. This method
   * will accept the connection, and attach a SocketConnector in order to read
   * the greeting off of the channel. Once the greeting has been read, the
   * connector will hand the channel off to the appropriate node handle.
   *
   * @param key The key which is acceptable.
   */
  public void accept(SelectionKey key) {
//    logger.log("accept("+key+")");
    try {
//      final SocketChannel channel = (SocketChannel) ((ServerSocketChannel) key.channel()).accept();
//      channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
//      channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
//      channel.configureBlocking(false);
//      InetSocketAddress addr = (InetSocketAddress)channel.socket().getRemoteSocketAddress();
      SocketManager sm = new SocketManager(this, key); 
      synchronized (sockets) {
        sockets.put(sm, sm);
      }
      wire.incomingSocket(sm);
      
//      pastryNode.broadcastChannelOpened((InetSocketAddress)channel.socket().getRemoteSocketAddress(), NetworkListener.REASON_ACC_NORMAL);

//      key = wire.environment.getSelectorManager().register(channel, this, SelectionKey.OP_READ);

//      pending.add(new SocketAccepter(key));
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.log( "ERROR (accepting connection): " + e);
    }
  }

  private void putSM(SocketManager sm) {
    sockets.put(sm, sm);
  }
  
  private void killSM() {
    Iterator<SocketManager> i = sockets.values().iterator();
    SocketManager sm = i.next();
    i.remove();    
    sm.close();
  }
}
