
package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;

/**
 * The address of the route protocol at a pastry node.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class RouteProtocolAddress implements Address {
    private static final int myCode = 0x89ce110e;

    /**
     * Constructor.
     */
    
    public RouteProtocolAddress() {}

    public boolean equals(Object obj) {
	return (obj instanceof RouteProtocolAddress);
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

    public String toString() { return "[RouteProtocolAddress]"; }
}
