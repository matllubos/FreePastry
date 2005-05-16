package rice.email.proxy.mailbox;

import rice.email.proxy.mail.*;

import java.util.List;


public interface MailFolder {

    /**
     * Obtains a list of <code>StoredMessage</code>s  in this folder
     * that fall within the designated range.
     */
    List getMessages(MsgFilter range) throws MailboxException;

    String getFullName();

    int getNextUID() throws MailboxException;
    String getUIDValidity() throws MailboxException;
    int getExists() throws MailboxException;
    int getRecent() throws MailboxException;

    void put(MovingMessage msg, List flags, long internaldate) throws MailboxException;
    void put(MovingMessage msg) throws MailboxException;
    void copy(MovingMessage[] messages, List[] flags, long[] dates) throws MailboxException;
    void update(StoredMessage[] messages) throws MailboxException;
    void purge(StoredMessage[] messages) throws MailboxException;
    
    public MailFolder getChild(String name) throws MailboxException;
    public MailFolder createChild(String name) throws MailboxException;
    public MailFolder[] getChildren() throws MailboxException;
    public void delete() throws MailboxException;
}
