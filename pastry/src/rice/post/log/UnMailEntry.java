package rice.post.log;

import java.security.*;

/**
 * An anti-email node, serves to cancel out the mail node that it
 * matches up to.
 */
public class UnMailEntry implements LogNode{
    /**
     * Constructor for UnMailEntry.  For the given email, creates a node which serves
     * as a marker that the previous occurence of the email in the chain
     * should be disregarded.  The next field is the next LogNode in the chain.
     * @param email the email to store
     * @param next the next LogNode in the chain
     */
    public UnMailEntry(Email email, LogNode next){
    }

    /**
     * Signs the UnMailEntry with the owner's private key.
     * @param signWith the owner's private key with which to sign the UnMailEntry
     */
    public void sign(PrivateKey signWith) throws CryptoException {
    }

    /**
     * Verifies that the UnMailEntry was signed by the owner of the mailbox.
     * @param verifyWith the owner's public key with which to verify the email's signature
     * @return if the UnMailEntry was validly signed
     */
    public boolean verify(PublicKey verifyWith) throws CryptoException {
    }

    /**
     * Returns whether this UnMailEntry has been signed or not.
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
