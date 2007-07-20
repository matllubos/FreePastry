package org.mpisws.p2p.transport.liveness;

import org.mpisws.p2p.transport.TransportLayer;

public interface LivenessTransportLayer<Identifier, MsgType> extends 
  TransportLayer<Identifier, MsgType>, 
  LivenessProvider<Identifier>, 
  Pinger<Identifier> {

}
