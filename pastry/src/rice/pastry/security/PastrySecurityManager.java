
package rice.pastry.security;

import rice.pastry.*;
import rice.pastry.messaging.*;
import java.util.*;

/**
 * The security manager interface.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public interface PastrySecurityManager {
    /**
     * This method takes a message and returns true
     * if the message is safe and false otherwise.
     *
     * @param msg a message.
     * @return if the message is safe, false otherwise.
     */
    
    public boolean verifyMessage(Message msg);

    /**
     * Checks to see if these credentials can be associated with the address.
     *
     * @param cred some credentials.
     * @param addr an address.
     *
     * @return true if the credentials match the address, false otherwise.
     */
    
    public boolean verifyAddressBinding(Credentials cred, Address addr);

    /**
     * Verify node handle safety.
     *
     * @param handle the handle to check.
     *
     * @return the verified node handle
     */

    public NodeHandle verifyNodeHandle(NodeHandle handle);

    /**
     * Gets the current time for a timestamp.
     *
     * @return the timestamp.
     */
    
    public Date getTimestamp();
}
