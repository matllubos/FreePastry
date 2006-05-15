
package rice.p2p.past;

import java.io.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.rawserialization.RawPastContentHandle;

/**
 * @(#) ContentHashPastContentHandle.java
 *
 * A handle class for content-hash objects stored in Past.
 *
 * @version $Id$
 * @author Peter Druschel
 */
public class ContentHashPastContentHandle implements RawPastContentHandle {
  public static final short TYPE = -12;
  
  
  // the node on which the content object resides
  private NodeHandle storageNode;

  // the object's id
  private Id myId;

  /**
   * Constructor
   *
   * @param nh The handle of the node which holds the object
   * @param id key identifying the object to be inserted
   */
  public ContentHashPastContentHandle(NodeHandle nh, Id id) {
    storageNode = nh;
    myId = id;
  }

  
  // ----- PastCONTENTHANDLE METHODS -----

  /**
   * Returns the id of the PastContent object associated with this handle
   *
   * @return the id
   */
  public Id getId() {
    return myId;
  }

  /**
   * Returns the NodeHandle of the Past node on which the object associated
   * with this handle is stored
   *
   * @return the id
   */
  public NodeHandle getNodeHandle() {
    return storageNode;
  }

  public ContentHashPastContentHandle(InputBuffer buf, Endpoint endpoint) throws IOException {
    myId = endpoint.readId(buf, buf.readShort());
    storageNode = endpoint.readNodeHandle(buf);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(myId.getType());
    myId.serialize(buf);
    storageNode.serialize(buf);
  }
  
  public short getType() {
    return TYPE; 
  }
}










