
package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;

/**
 * The address of the router at a pastry node.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class RouterAddress implements Address {
    private static final int myCode = 0xACBDFE17;

    /**
     * Constructor.
     */
    
    public RouterAddress() {}

    public boolean equals(Object obj) {
	return (obj instanceof RouterAddress);
    }

    public int hashCode() {
	return myCode;
    }
    
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {}

    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {}

    public String toString() { return "[RouterAddress]"; }
}
