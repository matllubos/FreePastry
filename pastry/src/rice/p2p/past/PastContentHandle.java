
package rice.p2p.past;

import java.io.Serializable;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * @(#) PastContentHandle.java
 * 
 * This interface must be implemented by all content object handles.  This interface
 * represents an object that is stored on a specific replica.  Thus, the validity of
 * a handle is sometimes very short, and applications should keep references to an
 * object by Id rather than handle.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel 
 */
public interface PastContentHandle extends Serializable {

  /**
   * get the id of the PastContent object associated with this handle
   * @return the id
   */
  public Id getId();

  /**
   * get the NodeHandle of the Past node on which the object associated with this handle is stored
   * @return the id
   */
  public NodeHandle getNodeHandle();

}





