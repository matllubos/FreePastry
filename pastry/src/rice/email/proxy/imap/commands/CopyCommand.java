package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mail.MailException;
import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.mail.StoredMessage;

import rice.email.proxy.mailbox.FlagList;
import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.Mailbox;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;

import java.io.IOException;

import java.util.*;


/**
 * COPY command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.7">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.7 </a>
 * </p>
 */
public class CopyCommand extends AbstractImapCommand {
  
  public CopyCommand() {
    super("COPY");
  }
  
  public boolean isValidForState(ImapState state) {
    return state.isSelected();
  }
  
  MsgFilter _range;
  String _folder;
  
  public void execute() {
    try {
      ImapState state = getState();
      MailFolder fold = state.getSelectedFolder();
      List msgs       = fold.getMessages(_range);
      
      MovingMessage[] messages = new MovingMessage[msgs.size()];
      List[] flags = new List[msgs.size()];
      long[] internaldates = new long[msgs.size()];
      int j=0;
      
      for (Iterator i = msgs.iterator(); i.hasNext();) {
        StoredMessage msg = (StoredMessage) i.next();
        messages[j] = new MovingMessage(msg.getMessage());
        flags[j] = msg.getFlagList().getFlags();
        internaldates[j] = msg.getInternalDate();
        j++;
      }
      
      state.getMailbox().getFolder(_folder).copy(messages, flags, internaldates);
      
      taggedSimpleSuccess();
    } catch (MailboxException e) {
      taggedExceptionFailure(e);
    }
  }
  
  public String getFolder() {
    return _folder;
  }
  
  public MsgFilter getRange() {
    return _range;
  }
  
  public void setFolder(String mailbox) {
    _folder = mailbox;
  }
  
  public void setRange(MsgFilter range) {
    _range = range;
  }
}