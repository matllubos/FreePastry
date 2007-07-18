package org.mpisws.p2p.transport.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.TransportLayerCallback;

import rice.environment.Environment;
import rice.environment.logging.Logger;

public class DefaultCallback<Identifier, MessageType> implements
    TransportLayerCallback<Identifier, MessageType> {
  Logger logger;
  
  public DefaultCallback(Environment environment) {
    logger = environment.getLogManager().getLogger(DefaultCallback.class, null);
  }

  public DefaultCallback(Logger logger) {
    this.logger = logger;
  }

  public void incomingSocket(P2PSocket s)
      throws IOException {
    logger.log("incomingSocket("+s+")");
  }

  public void livenessChanged(Identifier i, int state) {
    logger.log("livenessChanged("+i+","+state+")");
  }

  public void messageReceived(Identifier i, MessageType m, Map<String, Integer> options)
      throws IOException {
    logger.log("messageReceived("+i+","+m+")");
  }

}
