/*
 * Created on Apr 7, 2006
 */
package rice.post.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public interface Raw {
  public short getType();
  public void serialize(OutputBuffer buf) throws IOException;
}
