package rice.post.log;

import java.security.*;

/**
 * Stores an email in the LogNode chain.  Holds the email and a pointer
 * to the next LogNode.
 */
public class MailEntry implements LogNode{
    /**
     * Constructor for MailEntry.  For the given email, creates an
     * entry which can be used in a log chain.  The next field is the
     * next LogNode in the chain.
     * @param email the email to store
     * @param next the next LogNode in the chain
     */
    public MailEntry(Email email, LogNode next) {
    }

    /**
     * Signs the MailEntry with the owner's private key.
     * @param signWith the owner's private key with which to sign the MailEntry
     */
    public void sign(PrivateKey signWith) throws CryptoException {
    }

    /**
     * Verifies that the MailEntry was signed by the owner of the mailbox.
     * @param verifyWith the owner's public key with which to verify the email's signature
     * @return if the MailEntry was validly signed
     */
    public boolean verify(PublicKey verifyWith) throws CryptoException {
    }

    /**
     * Returns whether this MailEntry has been signed or not.
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
}
