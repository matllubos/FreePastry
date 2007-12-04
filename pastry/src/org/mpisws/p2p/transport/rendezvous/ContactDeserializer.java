package org.mpisws.p2p.transport.rendezvous;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface ContactDeserializer<Identifier, HighIdentifier> {

  HighIdentifier deserialize(InputBuffer sib);

  byte[] readCredentials(InputBuffer sib);

  Identifier convert(HighIdentifier high);
  
}
