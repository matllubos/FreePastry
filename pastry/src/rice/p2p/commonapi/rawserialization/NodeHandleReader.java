/*
 * Created on Mar 23, 2006
 */
package rice.p2p.commonapi.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.NodeHandle;

public interface NodeHandleReader {
  /**
   * To use Raw Serialization
   * @param buf
   * @return
   * @throws IOException 
   */
  public NodeHandle readNodeHandle(InputBuffer buf) throws IOException;
  public NodeHandle coalesce(NodeHandle handle);

}
