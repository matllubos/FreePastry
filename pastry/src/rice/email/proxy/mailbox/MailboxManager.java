package rice.email.proxy.mailbox;

public interface MailboxManager
{
    public String getMailboxType();

    public Mailbox getMailbox(String username)
                       throws NoSuchMailboxException;

    public void createMailbox(String username)
                       throws MailboxException;

    public void destroyMailbox(String username)
                        throws MailboxException;
}