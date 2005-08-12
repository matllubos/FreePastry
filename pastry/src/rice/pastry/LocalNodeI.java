
package rice.pastry;

import java.io.*;
import java.util.*;

import rice.pastry.messaging.*;

/**
 * This interface is for any objects who need to automatically have the 
 * PastryNode assigned to it upon reception off of the wire.  As of FreePastry 1.4.1,
 * this is now done by the PastryObjectInputStream.  The main object that needs this
 * is the NodeHandle.  So that NodeHandle.receiveMessage() will work.  When using the 
 * commonAPI, this is not necessary.
 * 
 * @version $Id$
 *
 * @author Alan Mislove
 */
public interface LocalNodeI extends Serializable {

    /**
     * Accessor method.
     */
    public PastryNode getLocalNode();

    /**
     * Accessor method. Notifies the overridable afterSetLocalNode.
     */
    public void setLocalNode(PastryNode pn);

    /**
     * May be called from handle etc methods to ensure that local node has
     * been set, either on construction or on deserialization/receivemsg.
     */
    public void assertLocalNode();
}
