
package rice.pastry;

/** 
 * An interface to any object capable of generating nodeIds.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public interface NodeIdFactory {
    /**
     * Generates a nodeId.
     *
     * @return a new node id.
     */
    
    public Id generateNodeId();
}
