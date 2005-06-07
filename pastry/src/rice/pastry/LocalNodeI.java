
package rice.pastry;

import java.io.*;
import java.util.*;

import rice.pastry.messaging.*;

/**
 * Interface that some Serializable classes (such as NodeHandle and
 * Certificate) implement, if they want to be kept informed of what node
 * they're on. Think of this as a pattern. One implementation of this
 * is provided (LocalNodeImpl), but if a class cannot use this implementation
 * (for reasons such as multiple inheritance), it should implement
 * LocalNode and provide the methods below.
 *
 * NOTE: All implementations of local nodes should override their readObject()
 * methods in order to add the following lines:
 *
 *    in.defaultReadObject();
 *    LocalNode.pending.addPending(in, this)
 *
 * which will schedule the LocalNode to have it's local node to be set to
 * non-null.
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
