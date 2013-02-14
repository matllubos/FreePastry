package rice.email;

import rice.pastry.*;
import rice.post.*;

/**
 * Represents the idea of a POST email address. Constructor takes a string
 * (intended to be human-readable, perhaps similar in some way to an
 * Internet mail address). You can get the corresponding NodeId out of
 * this address.
 */

public class EmailUserAddress extends PostUserAddress {
    
    /**
     * Constructs an EmailAddress with the given human-readable
     * string.
     *
     * @param name The name of the user
     */
    public EmailUserAddress(String name) { 
    }

    /**
     * Gets the human-readable string of this EmailAddress.
     *
     * @return The String representation of this email address
     */
    public String getName() { 
      return null;
    }

    /**
     * Gets the NodeId corresponding to this EmailAddress.
     *
     * @return The hash of this user's name, which is the email address.
     */
    public NodeId getAddress() { 
      return null;
    }
}
