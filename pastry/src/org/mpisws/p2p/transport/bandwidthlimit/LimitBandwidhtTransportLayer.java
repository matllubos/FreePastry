package org.mpisws.p2p.transport.bandwidthlimit;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.TransportLayer;

/**
 * Can Limit Bandwidth of a node.  Will queue messages, then drop them.
 * 
 * @author Jeff Hoye
 *
 */
public interface LimitBandwidhtTransportLayer<Identifier> extends TransportLayer<Identifier, ByteBuffer> {

}
