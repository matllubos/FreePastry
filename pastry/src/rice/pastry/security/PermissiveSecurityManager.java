
package rice.pastry.security;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.util.*;

/**
 * A trivial security manager.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class PermissiveSecurityManager implements PastrySecurityManager
{
    public boolean verifyMessage(Message msg)
    {
	return true;
    }
    
    public boolean verifyAddressBinding(Credentials cred, Address addr)
    {
	return true;
    }

    public NodeHandle verifyNodeHandle(NodeHandle handle) 
    { 
	return handle;
    }
    
    public Date getTimestamp() { return new Date(); }
}
