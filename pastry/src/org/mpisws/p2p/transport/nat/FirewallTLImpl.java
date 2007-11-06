package org.mpisws.p2p.transport.nat;

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

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.time.TimeSource;

/**
 * Drops all incoming TCP connections.
 * Drops all incoming UDP connections that we didn't initiate, or that are since UDP_OPEN_MILLIS
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 * @param <MessageType>
 */
public class FirewallTLImpl<Identifier, MessageType> implements TransportLayer<Identifier, MessageType>, TransportLayerCallback<Identifier, MessageType> {

  protected final int UDP_OPEN_MILLIS;
  
  /**
   * Holds when we last refreshed the UDP connection
   */
  protected Map<Identifier, Long> udpTable;

  protected TransportLayer<Identifier, MessageType> tl;

  protected TransportLayerCallback<Identifier, MessageType> callback;

  protected TimeSource timeSource;

  protected Environment environment;

  protected Logger logger;
  
  /**
   * 
   * @param tl
   * @param udp_open_millis how long the udp hole remains open
   */
  public FirewallTLImpl(TransportLayer<Identifier, MessageType> tl, int udp_open_millis, Environment env) {
    this.UDP_OPEN_MILLIS = udp_open_millis;
    this.environment = env;
    this.timeSource = environment.getTimeSource();
    this.logger = env.getLogManager().getLogger(FirewallTLImpl.class, null);
    tl.setCallback(this);
    tl.acceptSockets(false);    
  }
  
  public MessageRequestHandle<Identifier, MessageType> sendMessage(Identifier i, MessageType m, MessageCallback<Identifier, MessageType> deliverAckToMe, Map<String, Object> options) {
    long now = timeSource.currentTimeMillis();
    udpTable.put(i,now);
    return tl.sendMessage(i, m, deliverAckToMe, options);
  }
  
  public void messageReceived(Identifier i, MessageType m, Map<String, Object> options) throws IOException {
    if (udpTable.containsKey(i)) {
      long now = timeSource.currentTimeMillis();
      if (udpTable.get(i)+UDP_OPEN_MILLIS >= now) {
        if (logger.level <= Logger.FINER) logger.log("accepting messageReceived("+i+","+m+","+options+")");
        udpTable.put(i,now);
        callback.messageReceived(i, m, options);
        return;
      }      
    }
    if (logger.level <= Logger.FINE) logger.log("dropping messageReceived("+i+","+m+","+options+")");
  }
  
  /**
   * Only allow outgoing sockets.
   */
  public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
    if (logger.level <= Logger.FINE) logger.log("closing incomingSocket("+s+")");
    s.close();
  }

  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    return;
  }

  public Identifier getLocalIdentifier() {
    return tl.getLocalIdentifier();    
  }

  public SocketRequestHandle<Identifier> openSocket(Identifier i, SocketCallback<Identifier> deliverSocketToMe, Map<String, Object> options) {
    return tl.openSocket(i, deliverSocketToMe, options);
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
