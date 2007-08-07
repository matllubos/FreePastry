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
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.util.DefaultCallback;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.pastry.messaging.Message;

public class WireTransportLayerImpl implements WireTransportLayer {
  // state
  protected InetSocketAddress bindAddress;
  
  // helpers
  protected UDPLayer udp;
  protected TCPLayer tcp;
  
  
  // abstract helpers
  protected Logger logger;
  protected Environment environment;
  
  private TransportLayerCallback<InetSocketAddress, ByteBuffer> callback;
  protected ErrorHandler<InetSocketAddress> errorHandler;
    
  /**
   * 
   * @param bindAddress the address to bind to, if it fails, it will throw an exception
   * @param env will acquire the SelectorManager from the env
   * @throws IOException
   */
  public WireTransportLayerImpl(
      InetSocketAddress bindAddress, 
      Environment env, 
      ErrorHandler<InetSocketAddress> errorHandler) throws IOException {
    this.logger = env.getLogManager().getLogger(WireTransportLayer.class, null);
    this.bindAddress = bindAddress;
    this.environment = env;
    
    this.callback = new DefaultCallback<InetSocketAddress, ByteBuffer>(logger);    
    this.errorHandler = errorHandler;
    
    if (this.errorHandler == null) {
      this.errorHandler = new DefaultErrorHandler<InetSocketAddress>(logger); 
    }
    
    udp = new UDPLayer(this);
    try {
      tcp = new TCPLayer(this);
    } catch (IOException ioe) {
      udp.destroy();
      throw ioe;
    }
    
  }
  
  public void setCallback(TransportLayerCallback<InetSocketAddress, ByteBuffer> callback) {
    this.callback = callback;
  }

  public SocketRequestHandle<InetSocketAddress> openSocket(InetSocketAddress destination, SocketCallback<InetSocketAddress> deliverSocketToMe, Map<String, Integer> options) {
    return tcp.openSocket(destination, deliverSocketToMe, options);
  }

  public MessageRequestHandle<InetSocketAddress, ByteBuffer> sendMessage(
      InetSocketAddress destination, 
      ByteBuffer m, 
      MessageCallback<InetSocketAddress, ByteBuffer> deliverAckToMe, 
      Map<String, Integer> options) {
    if (logger.level <= Logger.FINE) logger.log("sendMessage("+destination+","+m+")");
    return udp.sendMessage(destination, m, deliverAckToMe, options); 
//    if (options != null) {
//      Integer val = options.get(WireTransportLayer.OPTION_TRANSPORT_TYPE);
//      if (val != null) {
//        if (val.intValue() == WireTransportLayer.TRANSPORT_TYPE_DATAGRAM) {
//          udp.sendMessage(destination, m, deliverAckToMe); 
//          return;
//        }
//      }
//    }
//    tcp.sendMessage(destination, m, deliverAckToMe);
  }

  /**
   * Notification methods
   * 
   * @param destination
   * @param msg
   * @param i
   * @param data
   * @param transport_type WireTransportLayer.TRANSPORT_TYPE_DATAGRAM or WireTransportLayer.TRANSPORT_TYPE_GUARANTEED
   */
//  public void msgSent(InetSocketAddress destination, byte[] msg, int transport_type_datagram) {
//    // TODO notify listeners
//  }

  public InetSocketAddress getLocalIdentifier() {
    return bindAddress;
  }

  public void destroy() {
    if (logger.level <= Logger.INFO) logger.log("destroy()");
    udp.destroy();
    tcp.destroy();
  }

  public void setErrorHandler(ErrorHandler<InetSocketAddress> handler) {
    if (handler == null) {
      this.errorHandler = new DefaultErrorHandler<InetSocketAddress>(logger);
      return;
    }
    this.errorHandler = handler;
  }

  public void acceptMessages(boolean b) {
    udp.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    tcp.acceptSockets(b);
  }

  protected void messageReceived(InetSocketAddress address, ByteBuffer buffer) throws IOException {
    callback.messageReceived(address, buffer, udp.OPTIONS);
  }

  protected void incomingSocket(P2PSocket<InetSocketAddress> sm) throws IOException {
    callback.incomingSocket(sm);
  }
}
