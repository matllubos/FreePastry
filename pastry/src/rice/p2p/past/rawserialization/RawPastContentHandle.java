/*
 * Created on Mar 23, 2006
 */
package rice.p2p.past.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.past.PastContentHandle;

public interface RawPastContentHandle extends PastContentHandle {

  public short getType();

  public void serialize(OutputBuffer buf) throws IOException;

}
