/*
 * Created on Feb 16, 2006
 */
package rice.p2p.commonapi.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.Message;

public interface RawMessage extends Message {
  public short getType();
  public void serialize(OutputBuffer buf) throws IOException;
}
