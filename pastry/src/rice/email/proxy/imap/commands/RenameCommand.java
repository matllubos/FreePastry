package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailboxException;

import java.io.IOException;


/**
 * RENAME command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.3">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.3 </a>
 * </p>
 */
public class RenameCommand extends AbstractImapCommand {
  String old_folder;
  String new_folder;
  
  public RenameCommand() {
    super("RENAME");
  }
  
  public boolean isValidForState(ImapState state) {
    return state.isAuthenticated();
  }
  
  public void execute() {
    try {
      getState().getMailbox().renameFolder(getOldFolder(), getNewFolder());
      taggedSimpleSuccess();
    } catch (MailboxException e) {
      taggedExceptionFailure(e);
    }
  }
  
  public String getOldFolder() {
    return old_folder;
  }
  
  public String getNewFolder() {
    return new_folder;
  }
  
  public void setOldFolder(String folder) {
    old_folder = folder;
  }
  
  public void setNewFolder(String folder) {
    new_folder = folder;
  }
}
