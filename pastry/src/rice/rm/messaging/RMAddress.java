package rice.rm.messaging;

import rice.pastry.messaging.*;

/**
 * @(#) RMAddress.java
 *
 * The receiver address of the RM system. It is the
 * address that Pastry uses to deliver messages to RM.
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */
public class RMAddress implements Address {


    /**
     * Code representing address.
     */
    private int _code = 0x7bcdef44;

    /**
     * Constructor for RMAddress
     *
     */
    public RMAddress() {;}

    
    /**
     * Returns the code representing the address.
     */
    public int hashCode() { return _code; }
    
    /**
     * Determines if another object is equal to this one.
     */
    public boolean equals(Object obj) {
      return (obj instanceof RMAddress);
    }
}







