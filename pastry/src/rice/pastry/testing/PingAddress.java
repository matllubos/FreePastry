
package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.util.*;

/**
 * PingAddress
 *
 * A performance test suite for pastry. 
 *
 * @version $Id$
 *
 * @author Rongmei Zhang
 */

public class PingAddress implements Address {
    private int myCode = 0x9219d8ff;
	
    public int hashCode() { return myCode; }

    public boolean equals(Object obj) { return (obj instanceof PingAddress); }

    public String toString() { return "[PingAddress]"; }
}

