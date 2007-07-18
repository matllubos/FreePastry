package org.mpisws.p2p.transport.identity;

import org.mpisws.p2p.transport.TransportLayer;

/**
 * Prefixes outgoing messages/sockets with the identity of the destination.
 * Verifies that sockets/messages that arrive are for the local identity.  When this is not true,
 * responds with an error.
 * 
 * When the error is received, it is reported to a corresponding UpperIdentity.
 * 
 * @author Jeff Hoye
 *
 */
public interface LowerIdentity<Identifier, MessageType> extends TransportLayer<Identifier, MessageType> {

}
