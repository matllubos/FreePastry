package org.mpisws.p2p.transport.identity;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;

public interface IdentitySerializer<UpperIdentifier, MiddleIdentifier, LowerIdentifier> {
  public void serialize(OutputBuffer buf, UpperIdentifier i) throws IOException;

  public UpperIdentifier deserialize(InputBuffer buf, LowerIdentifier l) throws IOException;
  
  public MiddleIdentifier translateDown(UpperIdentifier i);
  public UpperIdentifier translateUp(MiddleIdentifier i);
  
}
