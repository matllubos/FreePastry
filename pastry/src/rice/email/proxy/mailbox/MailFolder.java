package rice.email.proxy.mailbox;

import rice.email.proxy.mail.MovingMessage;

import java.util.List;


public interface MailFolder {

    /**
     * Obtains a list of <code>StoredMessage</code>s  in this folder
     * that fall within the designated range.
     */
    List getMessages(MsgFilter range)
              throws MailboxException;

    String getUIDValidity()
                   throws MailboxException;

    String getFullName();

    int getNextUID();

    int getExists();

    int getRecent();

    void put(MovingMessage msg, List flags, String date)
      throws MailboxException;

    void put(MovingMessage msg)
      throws MailboxException;
}
