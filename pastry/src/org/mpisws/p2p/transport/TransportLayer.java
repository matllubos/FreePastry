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
