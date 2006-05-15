/*
 * Created on Feb 23, 2006
 */
package rice.pastry;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface NodeHandleFactory {
  public NodeHandle readNodeHandle(InputBuffer buf) throws IOException;
}
