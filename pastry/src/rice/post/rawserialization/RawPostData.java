/*
 * Created on Apr 12, 2006
 */
package rice.post.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.post.storage.PostData;

public interface RawPostData extends PostData {
  public short getType();
  public void serialize(OutputBuffer buf) throws IOException;
}
