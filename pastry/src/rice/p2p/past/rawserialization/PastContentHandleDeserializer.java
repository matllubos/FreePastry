/*
 * Created on Mar 23, 2006
 */
package rice.p2p.past.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.past.PastContentHandle;

public interface PastContentHandleDeserializer {
  public PastContentHandle deserializePastContentHandle(InputBuffer buf, Endpoint endpoint, short contentType) throws IOException;
}
