package rice.email.proxy.imap.commands;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import rice.email.proxy.imap.ImapState;
import rice.email.proxy.imap.commands.search.SearchPart;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;


/**
 * SEARCH command.
 *
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.4">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.4 </a>
 * </p>
 */
public class SearchCommand extends AbstractImapCommand {

  SearchPart part;
  boolean isUID;

  public SearchCommand(boolean isUID) {
    super("SEARCH");

    this.isUID = isUID;
  }

  public boolean isValidForState(ImapState state) {
    return state.isSelected();
  }

  public void execute() {
    try {
      getConn().print("* SEARCH");
      
      ImapState state = getState();
      MailFolder fold = state.getSelectedFolder();
      List msgs = fold.getMessages(part);
      
      for (Iterator i = msgs.iterator(); i.hasNext();) {
        StoredMessage msg = (StoredMessage) i.next();

        if (isUID) {
          getConn().print(" " + msg.getUID());
        } else {
          getConn().print(" " + msg.getSequenceNumber());
        }
      }

      getConn().print("\r\n");

      taggedSimpleSuccess();
    } catch (Exception e) {
      taggedExceptionFailure(e);
    }
  }

  public void setPart(SearchPart part) {
    this.part = part;
  }
}













