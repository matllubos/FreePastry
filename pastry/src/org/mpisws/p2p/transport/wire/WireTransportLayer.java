package org.mpisws.p2p.transport.wire;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;

import rice.Continuation;
import rice.pastry.messaging.Message;

/**
 * Sends/receives ByteBuffer from InetSocketAddress
 * 
 * This layer does a lot of the difficult part:
 * 
 * - Non-blocking I/O (using selector etc)
 * - Enforcement of number of Sockets to prevent FileDescriptor Starvation
 * 
 * 
 * @author Jeff Hoye
 *
 */
public interface WireTransportLayer extends TransportLayer<InetSocketAddress, ByteBuffer> {

  public static final String OPTION_TRANSPORT_TYPE = "transport_type";
  public static final int TRANSPORT_TYPE_DATAGRAM = 0;
  /**
   * Note this does not provide end-to-end guarantee.  Only per-hop guarantee.
   */
  public static final int TRANSPORT_TYPE_GUARANTEED = 1;
    
}
