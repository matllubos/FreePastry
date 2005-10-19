package rice.email.proxy.mailbox.filebox;

import java.io.*;
import java.util.*;

import rice.email.proxy.mailbox.*;

public class FileFlagList implements FlagList {

  HashSet _flags;
  File _msg;
  String _prepend;

  FileFlagList(File msg, String prepend, String flags) {
    _msg = msg;
    _prepend = prepend;
    _flags = new HashSet();

    if (flags.indexOf('D') != -1)
      setDeleted(true);

    if (flags.indexOf('S') != -1)
      setSeen(true);

    if (flags.indexOf('C') != -1)
      setFlag("\\Recent", true);
  }

  public void addFlag(String flag) {
    setFlag(flag, true);
  }

  public void removeFlag(String flag) {
    setFlag(flag, false);
  }

  public boolean isSet(String flag) {
    return _flags.contains(flag);
  }

  public void setFlag(String flag, boolean value) {
    if (value)
      _flags.add(flag);
    else
      _flags.remove(flag);
  }

  public void commit() throws MailboxException {
    StringBuffer flagBuffer = new StringBuffer(4);
    if (isRecent())
      flagBuffer.append('C');

    if (isDeleted())
      flagBuffer.append('D');

    if (isSeen())
      flagBuffer.append('S');

    String flagString = flagBuffer.toString();
    File newFile      = new File(_msg.getParent(),
                                 _prepend + flagString);
    if (!_msg.renameTo(newFile))
      throw new MailboxException("Couldn't rename " + _msg + " to " + newFile);

    _msg = newFile;
  }

  public boolean isRecent() {
    return _flags.contains("\\Recent");
  }

  public boolean isDeleted() {
    return _flags.contains("\\Deleted");
  }

  public boolean isSeen() {
    return _flags.contains("\\Seen");
  }

  public void setDeleted(boolean deleted) {
    setFlag("\\Deleted", deleted);
  }

  public void setSeen(boolean seen) {
    setFlag("\\Seen", seen);
  }

  public String toFlagString() {
    StringBuffer flagBuffer = new StringBuffer();

    Iterator i = _flags.iterator();

    while (i.hasNext()) {
      flagBuffer.append((String) i.next());
    }

    return "(" + flagBuffer.toString().trim() + ")";
  }
}