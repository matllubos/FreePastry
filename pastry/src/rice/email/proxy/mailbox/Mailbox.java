package rice.email.proxy.mailbox;

import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.imap.*;
import java.util.*;

public interface Mailbox
{
    public void put(MovingMessage msg)
             throws MailboxException;

    public MailFolder getFolder(String folder)
                         throws MailboxException;

    public void createFolder(String folder)
                      throws MailboxException;

    public Vector listFolders(String pattern)
      throws MailboxException;

    public void deleteFolder(String folder)
      throws MailboxException;

    public void subscribe(ImapConnection conn, String name)
                   throws MailboxException;

    public void unsubscribe(ImapConnection conn, String name)
                     throws MailboxException;

    public String[] listSubscriptions(ImapConnection conn, String pattern)
                               throws MailboxException;
}
