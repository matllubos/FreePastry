package rice.email.proxy.imap.commands.search;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;


public class NoArgSearchPart extends SearchPart {

  public boolean includes(StoredMessage msg) {
    if (getType().equals("ALL")) {
      return true;
    } else if (getType().equals("ANSWERED")) {
      return handleFlag(msg, FlagList.ANSWERED_FLAG, true);
    } else if (getType().equals("DELETED")) {
      return handleFlag(msg, FlagList.DELETED_FLAG, true);
    } else if (getType().equals("DRAFT")) {
      return handleFlag(msg, FlagList.DRAFT_FLAG, true);
    } else if (getType().equals("FLAGGED")) {
      return handleFlag(msg, FlagList.FLAGGED_FLAG, true);
    } else if (getType().equals("NEW")) {
      return (handleFlag(msg, FlagList.RECENT_FLAG, true) &&
              handleFlag(msg, FlagList.SEEN_FLAG, false));
    } else if (getType().equals("OLD")) {
      return handleFlag(msg, FlagList.RECENT_FLAG, false);
    } else if (getType().equals("RECENT")) {
      return handleFlag(msg, FlagList.RECENT_FLAG, true);
    } else if (getType().equals("SEEN")) {
      return handleFlag(msg, FlagList.SEEN_FLAG, true);
    } else if (getType().equals("UNANSWERED")) {
      return handleFlag(msg, FlagList.ANSWERED_FLAG, false);
    } else if (getType().equals("UNDELETED")) {
      return handleFlag(msg, FlagList.DELETED_FLAG, false);
    } else if (getType().equals("UNDRAFT")) {
      return handleFlag(msg, FlagList.DRAFT_FLAG, false);
    } else if (getType().equals("UNFLAGGED")) {
      return handleFlag(msg, FlagList.FLAGGED_FLAG, false);
    } else if (getType().equals("UNSEEN")) {
      return handleFlag(msg, FlagList.SEEN_FLAG, false);
    } else {
      return false;
    }
  }

  protected boolean handleFlag(StoredMessage msg, String flag, boolean set) {
    if (set)
      return msg.getFlagList().isSet(flag);
    else
      return ! msg.getFlagList().isSet(flag);
  }
}