package rice.post;

import rice.pastry.*;
import java.security.*;

/**
 * Represents the attachment to an email.
 */

public class EmailAttachment {

    /**
     * Constructor. Takes in a NodeId indicating where the attachment
     * is stored, and a Key with which that body can be decrypted.
     */
    public EmailAttachment(NodeId where, Key decryptWith) { }

    /**
     * Returns where the attachment is stored in PAST.
     */
    public NodeId getLocation();

    /**
     * Returns the Key that decrypts this attachment.
     */
    public Key getDecryptKey();

}
