
package rice.pastry;

/**
 * A class which stores changes to a node set.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class NodeSetUpdate 
{
    private NodeHandle changed;
    private boolean added;

    /**
     * Constructor.
     *
     * @param which the handle that changed.
     * @param how true if it was added to the set, false otherwise.
     */

    public NodeSetUpdate(NodeHandle which, boolean how) {
	changed = which;
	added = how;
    }

    /**
     * The handle that changed.
     *
     * @return the node handle that changed.
     */

    public NodeHandle handle() { return changed; }

    /**
     * Returns true if the handle was added to the set.
     *
     * @return true if added, false otherwise.
     */

    public boolean wasAdded() { return added; }
}
