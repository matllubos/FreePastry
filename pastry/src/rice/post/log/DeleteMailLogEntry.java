package rice.post.log;

import java.security.*;

/**
 * An anti-email node, serves to cancel out the mail node that it
 * matches up to.
 */
public class DeleteMailLogEntry extends LogEntry {

    /**
     * Constructor for DeleteMailLogEntry.  For the given email, creates a node which serves
     * as a marker that the previous occurence of the email in the chain
     * should be disregarded.  The next field is the next LogNode in the chain.
     * @param email the email to store
     * @param next the next LogEntry in the chain
     */
    public DeleteMailLogEntry(Email email, LogEntry next){
    }
}
