package rice.post.log;

import java.security.*;

/**
 * Represents the start of a log chain/Inbox.
 */
public class LogHead {
    /**
     * Constructor for LogHead. Creates the first node in the log chain
     * that is the Inbox.  The next field is the next LogNode in the
     * chain to examine.
     */
    LogHead(LogNode next){
    }

    /**
     * Signs the LogNode with the owner's private key.
     * @param signWith the owner's private key with which to sign the LogNode
     */
    public void sign(PrivateKey signWith) throws CryptoException {
    }

    /**
     * Verifies that the LogNode was signed by the owner of the mailbox.
     * @param verifyWith the owner's public key with which to verify the email's signature
     * @return if the LogNode was validly signed
     */
    public boolean verify(PublicKey verifyWith) throws CryptoException {
    }

    /**
     * Returns whether this LogHead has been signed or not.
     * @return whether the message is signed.
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

