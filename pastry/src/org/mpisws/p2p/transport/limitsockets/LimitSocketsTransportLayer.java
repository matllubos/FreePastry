package org.mpisws.p2p.transport.limitsockets;

import org.mpisws.p2p.transport.TransportLayer;

/**
 * Automatically closes sockets based on a policy.  Default policy is LRU.
 * 
 * @author Jeff Hoye
 *
 */
public interface LimitSocketsTransportLayer<Identifier, MessageType> extends TransportLayer<Identifier, MessageType> {

}
