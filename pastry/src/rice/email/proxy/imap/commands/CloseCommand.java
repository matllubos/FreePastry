package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mail.StoredMessage;

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.util.Iterator;
import java.util.List;


/**
 * CLOSE command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.2 </a>
 * </p>
 */
public class CloseCommand extends AbstractImapCommand {
  public CloseCommand() {
    super("CLOSE");
  }
  
  public boolean isValidForState(ImapState state) {
    return state.isSelected();
  }
  
  public void execute() {
    try {
      MailFolder fold = getState().getSelectedFolder();
      List l = fold.getMessages(MsgFilter.DELETED);
      
      if (l.size() > 0) {
        StoredMessage[] deleted = new StoredMessage[l.size()];
      
        for (int i=0; i<l.size(); i++) 
          deleted[i] = (StoredMessage) l.get(i);
       
        fold.purge(deleted);
      }
      
      Iterator j = fold.getMessages(MsgFilter.RECENT).iterator();
      
      while (j.hasNext()) 
        ((StoredMessage) j.next()).getFlagList().setRecent(false);
      
      getState().unselect();
      
      taggedSimpleSuccess();
    } catch (MailboxException me) {
      taggedExceptionFailure(me);
    }
  }
}