package rice.post;

import rice.pastry.*;
import java.security.*;

/**
 * Represents the textual (or whatever) body of an email message.
 * Provides two things: a NodeID where this body may be found, and
 * a key which can decrypt the body once it's found there.
 */

public class EmailBody {

    /**
     * Constructor. Takes in a NodeId indicating where the email
     * body is stored, and a Key with which that body can be decrypted.
     */
    public EmailBody(NodeId where, Key decryptWith) { }

    /**
     * Returns where the email body is stored in PAST.
     */
    public NodeId getLocation();

    /**
     * Returns the Key that decrypts this email body.
     */
    public Key getDecryptKey();

}
