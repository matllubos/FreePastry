package rice.email.proxy.mailbox.postbox;

import rice.email.*;
import rice.email.proxy.mailbox.*;

import java.util.*;

public class PostFlagList implements FlagList {
  boolean _seen;
  boolean _deleted;
  boolean _recent;

  private static Hashtable loc = new Hashtable();

  private PostFlagList() {
  }

  public static PostFlagList get(Email email) {
    if (loc.get(email) == null) {
      loc.put(email, new PostFlagList());
    }
PostFlagList list = (PostFlagList) loc.get(email);
list._recent = true;
return list;
    //  return (PostFlagList) loc.get(email);
  }
  
  public void addFlag(String flag)
  {
    setFlag(flag, true);
  }

  public void removeFlag(String flag)
  {
    setFlag(flag, false);
  }

  public void setFlag(String flag, boolean value)
  {
    if ("\\Deleted".equalsIgnoreCase(flag))
      setDeleted(value);

    if ("\\Seen".equalsIgnoreCase(flag))
      setSeen(value);
  }

  public void commit()
  {
  }

  public boolean isRecent()
  {

    return _recent;
  }

  public boolean isDeleted()
  {

    return _deleted;
  }

  public boolean isSeen()
  {

    return _seen;
  }

  public void setDeleted(boolean deleted)
  {
    this._deleted = deleted;
  }

  public void setSeen(boolean seen)
  {
    this._seen = seen;
  }

  public String toFlagString()
  {
    StringBuffer flagBuffer = new StringBuffer();

    if (isSeen())
      flagBuffer.append("\\Seen ");

    if (isRecent())
      flagBuffer.append("\\Recent ");

    if (isDeleted())
      flagBuffer.append("\\Deleted");

    return "(" + flagBuffer.toString().trim() + ")";
  }
}
