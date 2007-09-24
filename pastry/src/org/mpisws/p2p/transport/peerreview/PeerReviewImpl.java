package org.mpisws.p2p.transport.peerreview;

import java.io.IOException;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;

public class PeerReviewImpl<Identifier, MessageType> implements 
  TransportLayer<Identifier, MessageType>,
  TransportLayerCallback<Identifier, MessageType> {

  TransportLayer<Identifier, MessageType> tl;
  TransportLayerCallback<Identifier, MessageType> callback;

  public SocketRequestHandle<Identifier> openSocket(Identifier i, SocketCallback<Identifier> deliverSocketToMe, Map<String, Integer> options) {
    return tl.openSocket(i, deliverSocketToMe, options);
  }

  public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
    callback.incomingSocket(s);
  }

  public MessageRequestHandle<Identifier, MessageType> sendMessage(Identifier i, MessageType m, MessageCallback<Identifier, MessageType> deliverAckToMe, Map<String, Integer> options) {
    return tl.sendMessage(i, m, deliverAckToMe, options);
  }

  public void messageReceived(Identifier i, MessageType m, Map<String, Integer> options) throws IOException {
    callback.messageReceived(i, m, options);
  }
  
  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }

  public Identifier getLocalIdentifier() {
    return tl.getLocalIdentifier();
  }

  public void setCallback(TransportLayerCallback<Identifier, MessageType> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    // TODO Auto-generated method stub
    
  }

  public void destroy() {
    tl.destroy();
  }
}
