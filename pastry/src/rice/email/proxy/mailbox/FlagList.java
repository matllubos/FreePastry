package rice.email.proxy.mailbox;

public interface FlagList
{
    void addFlag(String flag);

    void removeFlag(String flag);

    void setFlag(String flag, boolean value);
 
    boolean isDeleted();

    boolean isSeen();

    boolean isRecent();

    boolean isSet(String flag);

    String toFlagString();

    /**
     * Causes any changes in this FlagList's state to be written to
     * the associated Mailbox. This allows colapsing several changes
     * into one disk write, one SQL command, etc.
     */
    void commit()
         throws MailboxException;
}

