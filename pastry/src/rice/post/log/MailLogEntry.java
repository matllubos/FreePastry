package rice.post.log;

import java.security.*;

/**
 * Stores an email in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 */
public class MailLogEntry extends LogEntry {

    /**
     * Constructor for MailEntry.  For the given email, creates an
     * entry which can be used in a log chain.  The next field is the
     * next LogEntry in the chain.
     * @param email the email to store
     * @param next the next LogEntry in the chain
     */
    public MailLogEntry(Email email, LogEntry next) {
    }
}
