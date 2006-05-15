/*
 * Created on Mar 17, 2006
 */
package rice.pastry.messaging;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.pastry.*;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;

/**
 * The purpose of this class is just for programming convienience to disambiguate
 * between rice.p2p.commonapi and rice.pastry with the interfaces/classes
 *   Message
 *   NodeHandle
 *   
 * @author Jeff Hoye
 */
public abstract class PJavaSerializedDeserializer extends JavaSerializedDeserializer {

  public PJavaSerializedDeserializer(PastryNode pn) {
    super(pn);
  }

  public abstract Message deserialize(InputBuffer buf, short type, byte priority, NodeHandle sender) throws IOException;
  
  public rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type, byte priority, rice.p2p.commonapi.NodeHandle sender) throws IOException {
    rice.p2p.commonapi.Message ret = deserialize(buf, type, priority, (NodeHandle)sender);
    if (ret == null) return super.deserialize(buf, type, priority, sender);
    return ret;
  }

}
