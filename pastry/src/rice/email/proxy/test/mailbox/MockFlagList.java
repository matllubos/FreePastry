package rice.email.proxy.test.mailbox;

import java.util.*;

import rice.email.proxy.mailbox.*;

public class MockFlagList implements FlagList
{
  Vector flags = new Vector();

  public void addFlag(String flag) {
    if (! flags.contains(flag)) {
      flags.add(flag);
    }
  }

  public void removeFlag(String flag) {
    flags.remove(flag);
  }

  public void setFlag(String flag, boolean value) {
    if (value && (! flags.contains(flag)))
      flags.add(flag);
    else
      flags.remove(flag);
  }

  public boolean isSet(String flag) {
    return flags.contains(flag);
  }

  public boolean isDeleted() {
    return flags.contains("\\Deleted");
  }

  public boolean isSeen() {
    return flags.contains("\\Seen");
  }

  public boolean isRecent() {
    return flags.contains("\\Recent");
  }

  public String toFlagString() {
    String result = "";

    for (int i=0; i<flags.size(); i++) {
      result += flags.elementAt(i) + " ";
    }

    return result.trim();
  }

  /**
    * Causes any changes in this FlagList's state to be written to
   * the associated Mailbox. This allows colapsing several changes
   * into one disk write, one SQL command, etc.
   */
  public void commit()
    throws MailboxException {}
}
