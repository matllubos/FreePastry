package rice.post.log;

import java.security.*;

/**
 * Serves as a summary of the log chain up to the current point.  Lets
 * the email reader display the current emails without having to read
 * through the entire chain.
 */
public class SnapShot implements LogNode{
    /**
     * Constructor for SnapShot.  For the given email, creates an
     * entry which can be used in a log chain.  The next field is the
     * next LogNode in the chain.
     * @param email the email to store
     * @param next the next LogNode in the chain
     */
    public SnapShot(Email email[], LogNode next) {
    }

    /**
     * Signs the SnapShot with the owner's private key.
     * @param signWith the owner's private key with which to sign the SnapShot
     */
    public void sign(PrivateKey signWith) throws CryptoException {
    }

    /**
     * Verifies that the SnapShot was signed by the owner of the mailbox.
     * @param verifyWith the owner's public key with which to verify the email's signature
     * @return if the SnapShot was validly signed
     */
    public boolean verify(PublicKey verifyWith) throws CryptoException {
    }

    /**
     * Returns whether this SnapShot has been signed or not.
     * @return whether the entry is signed
     */
    public boolean isSigned() {
	return null;
    }
    
    /**
     * Returns the next LogNode.
     * @return the next LogNode
     */
    public LogNode getNext() {
	return null;
    }

    /**
     * Returns all of the emails that the SnapShot contains.
     * @return the valid emails at the point of the SnapShot
     */
    public Email[] getEmails() {
	return null;
    }
    
    /**
     * Returns whether this is the last node in the current snapshot.
     * Is provided in case the snapshot covers multiple nodes.
     * @return whether this is the last snapshot node.
     */
    public boolean isEnd() {
	return null;
    }
}
