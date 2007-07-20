package rice.pastry.direct;

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
import org.mpisws.p2p.transport.exception.NodeIsFaultyException;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.pastry.direct.DirectAppSocket.DirectAppSocketEndpoint;

public class DirectTransportLayer<Identifier, MessageType> implements TransportLayer<Identifier, MessageType> {
  protected boolean acceptMessages = true;
  protected boolean acceptSockets = true;

  protected Identifier localIdentifier;
  protected TransportLayerCallback<Identifier, MessageType> callback;
  protected GenericNetworkSimulator<Identifier, MessageType> simulator;
  protected ErrorHandler<Identifier> errorHandler;
  protected LivenessProvider<Identifier> livenessProvider;
  
  protected Environment environment;
  protected Logger logger;
  
  public DirectTransportLayer(Identifier local, 
      GenericNetworkSimulator<Identifier, MessageType> simulator, 
      LivenessProvider<Identifier> liveness, Environment env) {
    this.localIdentifier = local;
    this.simulator = simulator;
    this.livenessProvider = liveness;
    
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(DirectTransportLayer.class, null);
  }
  
  public void acceptMessages(boolean b) {
    acceptMessages = b;
  }

  public void acceptSockets(boolean b) {
    acceptSockets = b;
  }

  public Identifier getLocalIdentifier() {
    return localIdentifier;
  }
  
  static class CancelAndClose implements Cancellable {
    DirectAppSocket closeMe;
    Cancellable cancelMe;
    
    public boolean cancel() {
      closeMe.connectorEndpoint.close();
      return cancelMe.cancel();
    }
    
  }

  public SocketRequestHandle<Identifier> openSocket(Identifier i, SocketCallback<Identifier> deliverSocketToMe, Map<String, Integer> options) {
    SocketRequestHandleImpl<Identifier> handle = new SocketRequestHandleImpl<Identifier>(i,options);
    DirectAppSocket<Identifier, MessageType> socket = new DirectAppSocket<Identifier, MessageType>(i, localIdentifier, deliverSocketToMe, simulator, handle, options);
    CancelAndClose cancelAndClose = new CancelAndClose();
    handle.setSubCancellable(cancelAndClose);
    cancelAndClose.cancelMe = simulator.enqueueDelivery(socket.getAcceptorDelivery(),
        (int)Math.round(simulator.networkDelay(localIdentifier, i)));
    return handle;
  }

  public MessageRequestHandle<Identifier, MessageType> sendMessage(
      Identifier i, MessageType m, 
      MessageCallback<Identifier, MessageType> deliverAckToMe, 
      Map<String, Integer> options) {
    
    MessageRequestHandleImpl<Identifier, MessageType> handle = new MessageRequestHandleImpl<Identifier, MessageType>(i, m, options);
    
    if (livenessProvider.getLiveness(i, null) >= LivenessListener.LIVENESS_DEAD) {
      if (logger.level <= Logger.FINE)
        logger.log("Attempt to send message " + m
            + " to a dead node " + i + "!");      
      
      if (deliverAckToMe != null) deliverAckToMe.sendFailed(handle, new NodeIsFaultyException(i));
    } else {
      int delay = (int)Math.round(simulator.networkDelay(localIdentifier, i));
//      simulator.notifySimulatorListenersSent(m, localIdentifier, i, delay);
      handle.setSubCancellable(simulator.deliverMessage(m, i, localIdentifier, delay));
      if (deliverAckToMe != null) deliverAckToMe.ack(handle);
    }
    return handle;
  }

  public void setCallback(TransportLayerCallback<Identifier, MessageType> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    this.errorHandler = handler;
  }

  public void destroy() {
    simulator.remove(getLocalIdentifier());
  }

  public boolean canReceiveSocket() {
    return acceptSockets;
  }

  public void finishReceiveSocket(P2PSocket<Identifier> acceptorEndpoint) {
    try {
      callback.incomingSocket(acceptorEndpoint);
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.logException("Exception in "+callback,ioe);
    }
  }

  public Logger getLogger() {
    return logger;
  }

  int seq = Integer.MIN_VALUE;
  
  public synchronized int getNextSeq() {
    return seq++;
  }
    
  public void incomingMessage(Identifier i, MessageType m, Map<String, Integer> options) throws IOException {
    callback.messageReceived(i, m, options);
  }

  public void clearState(Identifier i) {
    // do nothing
  }
}
