
package rice.pastry.leafset;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;
import java.util.*;

/**
 * Initiate leaf set maintenance on the local node.
 *
 * @version $Id$
 *
 * @author Peter Druschel
 */

public class InitiateLeafSetMaintenance extends Message implements Serializable
{

    /**
     * Constructor.
     *
     */
    
    public InitiateLeafSetMaintenance() { 
	super(new LeafSetProtocolAddress()); 
    }
    
}
