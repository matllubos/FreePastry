package rice.email.proxy.mailbox;

public interface FlagList
{
    void addFlag(String flag);

    void removeFlag(String flag);

    //    void setDeleted(boolean value);

    boolean isDeleted();

    //    void setSeen(boolean value);

    boolean isSeen();

    boolean isRecent();

    String toFlagString();

    /**
     * Causes any changes in this FlagList's state to be written to
     * the associated Mailbox. This allows colapsing several changes
     * into one disk write, one SQL command, etc.
     */
    void commit()
         throws MailboxException;
}
