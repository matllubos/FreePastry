package rice.email.proxy.imap.commands.search;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;


public class NoArgSearchPart extends SearchPart {

  public boolean includes(StoredMessage msg) {
    if (getType().equals("ALL")) {
      return true;
    } else if (getType().equals("ANSWERED")) {
      return handleFlag(msg, "\\Answered", true);
    } else if (getType().equals("DELETED")) {
      return handleFlag(msg, "\\Deleted", true);
    } else if (getType().equals("DRAFT")) {
      return handleFlag(msg, "\\Draft", true);
    } else if (getType().equals("FLAGGED")) {
      return handleFlag(msg, "\\Flagged", true);
    } else if (getType().equals("NEW")) {
      return handleFlag(msg, "\\Unseen", false);
    } else if (getType().equals("OLD")) {
      return true;
    } else if (getType().equals("RECENT")) {
      return false;
    } else if (getType().equals("SEEN")) {
      return handleFlag(msg, "\\Seen", true);
    } else if (getType().equals("UNANSWERED")) {
      return handleFlag(msg, "\\Answered", false);
    } else if (getType().equals("UNDELETED")) {
      return handleFlag(msg, "\\Deleted", false);
    } else if (getType().equals("UNDRAFT")) {
      return handleFlag(msg, "\\Draft", false);
    } else if (getType().equals("UNFLAGGED")) {
      return handleFlag(msg, "\\Flagged", false);
    } else if (getType().equals("UNSEEN")) {
      return handleFlag(msg, "\\Seen", false);
    } else {
      return false;
    }
  }

  protected boolean handleFlag(StoredMessage msg, String flag, boolean set) {
    String flags = msg.getFlagList().toFlagString().toLowerCase();

    if (set) {
      return (flags.indexOf(flag.toLowerCase()) >= 0);
    } else {
      return (flags.indexOf(flag.toLowerCase()) < 0);
    }
  }
}