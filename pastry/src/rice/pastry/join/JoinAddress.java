
package rice.pastry.join;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;

/**
 * The address of the join receiver at a pastry node.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class JoinAddress implements Address {
    private static final int myCode = 0xe80c17e8;

    /**
     * Constructor.
     */
    
    public JoinAddress() {}

    public boolean equals(Object obj) {
	return (obj instanceof JoinAddress);
    }

    public int hashCode() {
	return myCode;
    }
    
    public String toString() { return "[JoinAddress]"; }
}
