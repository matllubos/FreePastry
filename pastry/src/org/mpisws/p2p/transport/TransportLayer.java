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
package org.mpisws.p2p.transport;

import java.io.IOException;
import java.util.Map;

import rice.Continuation;
import rice.Destructable;
import rice.p2p.commonapi.Cancellable;

/**
 * The transport layer does provides the minimum functionality to provide communication
 * with flow control.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier> The type of node this layer operates on.
 * @param <MessageType> The type of message this layer sends.
 * @param <E> The type of exceptions this layer produces.
 */
public interface TransportLayer<Identifier, MessageType> extends Destructable {
  /**
   * Open a socket to the Identifier
   * 
   * @param i who to open the socket to
   * @param deliverSocketToMe the callback when the socket is opened
   * @param options options on how to open the socket (don't source route, encrypt etc) (may not be respected if layer cannot provide service)
   * @return an object to cancel opening the socket if it takes to long, or is no longer relevent
   */
  public SocketRequestHandle<Identifier> openSocket(Identifier i, SocketCallback<Identifier> deliverSocketToMe, Map<String, Integer> options);
  
  /**
   * Send the message to the identifier
   * 
   * @param i the destination
   * @param m the message
   * @param options delivery options (don't source route, encrypt etc) (may not be respected if layer cannot provide service)
   * @param deliverAckToMe layer dependent notification when the message is sent (can indicate placed on the wire, point-to-point acknowledgement, or end-to-end acknowledgement)
   * @return ability to cancel the message if no longer relevent
   */
  public MessageRequestHandle<Identifier, MessageType> sendMessage(Identifier i, MessageType m, MessageCallback<Identifier, MessageType> deliverAckToMe, Map<String, Integer> options);
  
  /**
   * The local node.
   * 
   * @return The local node.
   */
  public Identifier getLocalIdentifier();
  
  /**
   * Toggle accepting new sockets.  Useful in flow control if overwhelmed by incoming sockets.
   * Default: true
   * 
   * @param b 
   */
  public void acceptSockets(boolean b);
  
  /**
   * Toggle accepting incoming messages.  Useful in flow control if overwhelmed by incoming sockets.
   * Default: true
   * 
   * @param b 
   */
  public void acceptMessages(boolean b);
  
  /**
   * Set the callback for incoming sockets/messages
   * @param callback the callback for incoming sockets/messages
   */
  public void setCallback(TransportLayerCallback<Identifier, MessageType> callback);
  
  /**
   * To be notified of problems not related to an outgoing messaage/socket.  Or to be notified
   * if a callback isn't provided.
   * 
   * @param handler to be notified of problems not related to a specific messaage/socket.
   */
  public void setErrorHandler(ErrorHandler<Identifier> handler);  
}
