package rice.email.proxy.test.mailbox;

import java.util.*;
import rice.email.proxy.mailbox.*;

public class MockMailboxManager
    implements MailboxManager
{
    Map mailboxes = new HashMap();

    public String getMailboxType()
    {

        return MockMailboxManager.class.getName();
    }

    public Mailbox getMailbox(String username)
                       throws NoSuchMailboxException
    {
        Mailbox box = (Mailbox) mailboxes.get(username);
        if (box == null)
            throw new NoSuchMailboxException("MockMailbox: '" + 
                                             username + 
                                             "' mailbox doesn't exist");

        return box;
    }

    public void createMailbox(String username)
    {
        mailboxes.put(username, new MockMailbox());
    }

    public void destroyMailbox(String username)
    {
        mailboxes.remove(username);
    }
}