/*
 * Created on Mar 21, 2006
 */
package rice.p2p.past.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.past.PastContent;

public interface RawPastContent extends PastContent {

  public short getType();

  public void serialize(OutputBuffer buf) throws IOException;

}
