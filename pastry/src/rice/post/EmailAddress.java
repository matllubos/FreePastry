package rice.post;

import rice.pastry.*;

/**
 * Represents the idea of a POST email address. Constructor takes a string
 * (intended to be human-readable, perhaps similar in some way to an
 * Internet mail address). You can get the corresponding NodeId out of
 * this address.
 */

public class EmailAddress {
    
    /**
     * Constructs an EmailAddress with the given human-readable
     * string.
     */
    public EmailAddress(String humanReadable) { }

    /**
     * Gets the human-readable string of this EmailAddress.
     */
    public String getReadable() { }

    /**
     * Gets the NodeId corresponding to this EmailAddress.
     */
    public NodeId getNodeId() { }
}
