package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;

import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.postbox.*;
import java.util.*;

/**
 * LIST command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.8">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.8 </a>
 * </p>
 */
public class ListCommand extends AbstractImapCommand {

  public ListCommand() {
    super("LIST");
  }

  public boolean isValidForState(ImapState state) {

    return state.isAuthenticated();
  }

  String _folder;
  String reference;

  public void execute() {
    if ("".equals(getFolder())) {
      untaggedSimpleResponse("(\\Noselect) \"" + getState().getMailbox().getHierarchyDelimiter() + "\" \"\" ");
      taggedSimpleSuccess();

      return;
    }

    try {
      MailFolder[] folders = getState().getMailbox().listFolders(getReference() + getFolder());

      for (int i = 0; i < folders.length; i++) {
        untaggedSimpleResponse("() \"" + getState().getMailbox().getHierarchyDelimiter() + "\" \"" + folders[i].getFullName() + "\"");
      }

      taggedSimpleSuccess();
    } catch (MailboxException e) {
      taggedExceptionFailure(e);
    }
  }

  public String getFolder() {
    return _folder;
  }

  public void setFolder(String mailbox) {
    this._folder = mailbox;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }
}


