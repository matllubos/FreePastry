package rice.email.proxy.mailbox;

import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.imap.*;
import java.util.*;

public interface Mailbox {
  
    public void put(MovingMessage msg) throws MailboxException;

    public void createFolder(String folder) throws MailboxException;
    public MailFolder getFolder(String folder) throws MailboxException;
    public MailFolder[] listFolders(String pattern) throws MailboxException;
    public void renameFolder(String old_name, String new_name) throws MailboxException;
    public void deleteFolder(String folder) throws MailboxException;

    public void subscribe(String name) throws MailboxException;
    public void unsubscribe(String name) throws MailboxException;
    public String[] listSubscriptions(String pattern) throws MailboxException;
    
    public String getHierarchyDelimiter();
}
