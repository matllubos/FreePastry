package rice.email.proxy.mailbox;

import java.util.List;

public interface FlagList {
  
  /**
   * static names of all the server-provided flags
   */
  public static final String DELETED_FLAG = "\\Deleted";
  public static final String ANSWERED_FLAG = "\\Answered";
  public static final String SEEN_FLAG = "\\Seen";
  public static final String DRAFT_FLAG = "\\Draft";
  public static final String FLAGGED_FLAG = "\\Flagged";
  public static final String RECENT_FLAG = "\\Recent";
  
  /**
   * Methods which allow the modification of flags
   */
  void setFlag(String flag, boolean value);
  void setDeleted(boolean value);
  void setSeen(boolean value);
  void setDraft(boolean value);
  void setFlagged(boolean value);
  void setAnswered(boolean value);
  
  /**
   * Methods which allow the querying of flags
   */
  boolean isSet(String flag);
  boolean isDeleted();
  boolean isSeen();
  boolean isDraft();
  boolean isAnswered();
  boolean isFlagged();
  
  /**
   * Utility method for conversion to a string
   */
  String toFlagString();
  List getFlags();
  
  /**
   * Methods which support the session flags, as well as
   * the \Recent flag
   */
  boolean isRecent();
  void setRecent(boolean value);
  
  /**
   * Causes any changes in this FlagList's state to be written to
   * the associated Mailbox. This allows colapsing several changes
   * into one disk write, one SQL command, etc.
   */
  void commit() throws MailboxException;
}

