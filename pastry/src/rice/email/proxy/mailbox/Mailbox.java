package rice.email.proxy.mailbox;

import rice.email.proxy.mail.MovingMessage;


public interface Mailbox
{
    public void put(MovingMessage msg)
             throws MailboxException;

    public MailFolder getFolder(String folder)
                         throws MailboxException;

    public void createFolder(String folder)
      throws MailboxException;

    public void deleteFolder(String folder)
      throws MailboxException;

    public MailFolder[] listFolders(String pattern)
                             throws MailboxException;

    public void subscribe(String name)
                   throws MailboxException;

    public void unsubscribe(String name)
                     throws MailboxException;

    public String[] listSubscriptions(String pattern)
                               throws MailboxException;
}
