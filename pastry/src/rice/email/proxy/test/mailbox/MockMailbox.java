package rice.email.proxy.test.mailbox;

import rice.email.proxy.imap.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import java.io.IOException;

import java.util.*;


public class MockMailbox
    implements Mailbox
{
    Map folders = new HashMap();

    public void createFolder(String folder)
                      throws MailboxException
    {
        folders.put(folder, new MockMailFolder(folder));
    }

    public void deleteFolder(String folder)
      throws MailboxException
    {
      folders.remove(folder);
    }
    

    public MailFolder getFolder(String folder)
                         throws MailboxException
    {
        MailFolder value = (MailFolder) folders.get(folder);
        if (value == null)
            throw new MailboxException("No such folder as " + 
                                       folder);

        return value;
    }

    public MailFolder[] listFolders(String pattern)
                             throws MailboxException
    {

        return (MailFolder[]) folders.values().toArray(new String[0]);
    }

    public void put(MovingMessage msg)
             throws MailboxException
    {
        put("INBOX", msg);
    }
    
    public String getHierarchyDelimiter() {
      return "/";
    }

    public void put(String folder, MovingMessage msg)
             throws MailboxException
    {
        try
        {
            MockMailFolder fold = (MockMailFolder) getFolder(folder);
            fold.appendMessage(StreamUtils.toString(msg.getContent()));
        }
        catch (IOException e)
        {
            throw new MailboxException(e);
        }
    }

    Set subscriptions = new HashSet();

    public void subscribe(String name)
                   throws MailboxException
    {
        subscriptions.add(name);
    }

    public void unsubscribe(String name)
                     throws MailboxException
    {
        subscriptions.remove(name);
    }

    public String[] listSubscriptions(String pattern)
                               throws MailboxException
    {

        return (String[]) subscriptions.toArray(new String[0]);
    }
    
    public void renameFolder(String old_name, String new_name) throws MailboxException {
      throw new MailboxException("FOLDERS NOT IMPLEMENTED!");
    }
}