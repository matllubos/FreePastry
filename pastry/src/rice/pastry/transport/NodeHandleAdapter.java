package rice.pastry.transport;

import java.util.Collection;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.proximity.ProximityListener;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.NodeHandle;
import rice.pastry.boot.Bootstrapper;
import rice.pastry.direct.DirectTransportLayer;

public class NodeHandleAdapter implements 
    TransportLayer<NodeHandle, RawMessage>, 
    LivenessProvider<NodeHandle>, 
    ProximityProvider<NodeHandle>,
    Bootstrapper {
  
  TransportLayer tl;
  LivenessProvider livenessProvider;
  ProximityProvider proxProvider;
  Bootstrapper boot;
  
  public NodeHandleAdapter(TransportLayer tl, LivenessProvider livenessProvider, ProximityProvider proxProvider, Bootstrapper boot) {
    this.tl = tl;
    this.livenessProvider = livenessProvider;
    this.proxProvider = proxProvider;
    this.boot = boot;
  }

  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }
  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }
  public NodeHandle getLocalIdentifier() {
    return (NodeHandle)tl.getLocalIdentifier();
  }
  public SocketRequestHandle<NodeHandle> openSocket(NodeHandle i, SocketCallback<NodeHandle> deliverSocketToMe, Map<String, Integer> options) {
    return tl.openSocket(i, deliverSocketToMe, options);
  }
  
  public MessageRequestHandle<NodeHandle, RawMessage> sendMessage(NodeHandle i, RawMessage m, MessageCallback<NodeHandle, RawMessage> deliverAckToMe, Map<String, Integer> options) {
    return tl.sendMessage(i, m, deliverAckToMe, options);
  }
  public void setCallback(TransportLayerCallback<NodeHandle, RawMessage> callback) {
    tl.setCallback(callback);
  }
  public void setErrorHandler(ErrorHandler<NodeHandle> handler) {
    tl.setErrorHandler(handler);
  }
  public void destroy() {
    tl.destroy();
  }
  
  public void addLivenessListener(LivenessListener<NodeHandle> name) {
    livenessProvider.addLivenessListener(name);
  }
  public boolean checkLiveness(NodeHandle i, Map<String, Integer> options) {
    return livenessProvider.checkLiveness(i, options);
  }
  public int getLiveness(NodeHandle i, Map<String, Integer> options) {
    return livenessProvider.getLiveness(i, options);
  }
  public boolean removeLivenessListener(LivenessListener<NodeHandle> name) {
    return livenessProvider.removeLivenessListener(name);
  }
  public void addProximityListener(ProximityListener<NodeHandle> listener) {
    proxProvider.addProximityListener(listener);
  }
  public int proximity(NodeHandle i) {
    return proxProvider.proximity(i);
  }
  public boolean removeProximityListener(ProximityListener<NodeHandle> listener) {
    return proxProvider.removeProximityListener(listener);
  }

  public void boot(Collection bootaddresses) {
    boot.boot(bootaddresses);    
  }

  public TransportLayer getTL() {
    return tl;
  }
}
