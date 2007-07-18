package org.mpisws.p2p.transport.priority;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;

/**
 * Does 3 things:
 *  a) Sends messages on a Socket (depending on the options).  
 *  b) Prioritizes messages into queues.
 *  c) calls sendFailed if there is a liveness change
 *  
 * @author Jeff Hoye
 */
public interface PriorityTransportLayer<Identifier> extends TransportLayer<Identifier, ByteBuffer> {
  public static final String OPTION_PRIORITY = "OPTION_PRIORITY";
  
  // different priority levels
  public static final byte MAX_PRIORITY = -15;
  public static final byte HIGH_PRIORITY = -10;
  public static final byte MEDIUM_HIGH_PRIORITY = -5;
  public static final byte MEDIUM_PRIORITY = 0;
  public static final byte MEDIUM_LOW_PRIORITY = 5;
  public static final byte LOW_PRIORITY = 10;
  public static final byte LOWEST_PRIORITY = 15;
  public static final byte DEFAULT_PRIORITY = MEDIUM_PRIORITY;

}
