package rice.post.log;

import java.security.*;

/**
 * Abstract class for all entries in the log.
 */
public abstract class LogEntry {

    /**
     * Signs the LogEntry with the owner's private key.
     * @param signWith the owner's private key with which to sign the LogEntry
     */
    public abstract void sign(PrivateKey signWith) throws CryptoException;

    /**
     * Verifies that the LogEntry was signed by the owner of the mailbox.
     * @param verifyWith the owner's public key with which to verify the email's signature
     * @return if the LogEntry was validly signed
     */
    public abstract boolean verify(PublicKey verifyWith) throws CryptoException;

    /**
     * Returns whether this LogEntry has been signed or not.
     * @return whether the message is signed.
     */
    public abstract boolean isSigned();
    
    /**
     * Returns the next LogEntry.
     * @return the next LogEntry
     */
    public abstract LogEntry getNext();
}

