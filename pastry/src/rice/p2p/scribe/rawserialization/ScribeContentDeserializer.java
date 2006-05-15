/*
 * Created on Mar 21, 2006
 */
package rice.p2p.scribe.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.scribe.ScribeContent;

public interface ScribeContentDeserializer {

  ScribeContent deserializeScribeContent(InputBuffer buf, Endpoint endpoint, short contentType) throws IOException;

}
