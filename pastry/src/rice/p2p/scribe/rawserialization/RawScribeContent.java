/*
 * Created on Mar 21, 2006
 */
package rice.p2p.scribe.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.scribe.ScribeContent;

public interface RawScribeContent extends ScribeContent {

  public short getType();

  public void serialize(OutputBuffer buf) throws IOException;

}
