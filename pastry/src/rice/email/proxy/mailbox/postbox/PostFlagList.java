package rice.email.proxy.mailbox.postbox;

import rice.*;
import rice.email.*;
import rice.email.proxy.mailbox.*;

import java.util.*;

public class PostFlagList implements FlagList {

  PostMessage _msg;

  private static Hashtable loc = new Hashtable();

  protected PostFlagList(PostMessage msg) {
      _msg = msg;
  }

  public static PostFlagList get(PostMessage msg) {
    if (loc.get(msg.getStoredEmail().getEmail()) == null) {
      loc.put(msg.getStoredEmail().getEmail(), new PostFlagList(msg));
    }

    return (PostFlagList) loc.get(msg.getStoredEmail().getEmail());
  }
  
  public void addFlag(String flag)
  {
    setFlag(flag, true);
  }

  public void removeFlag(String flag)
  {
    setFlag(flag, false);
  }

  /**
   * Causes any changes in this FlagList's state to be written to
   * the associated Mailbox. This allows colapsing several changes
   * into one disk write, one SQL command, etc.
   */
  public void commit() throws MailboxException {
    try {
      final Exception[] exception = new Exception[1];
      final Object[] result = new Object[1];
      final Object wait = "wait";

      Continuation c = new Continuation() {
        public void receiveResult(Object o) {
          synchronized(wait) {
            result[0] = o;
            wait.notify();
          }
        }
        public void receiveException (Exception e) {
          synchronized (wait) {
            exception[0] = e;
            result[0] = "result";
            wait.notify();
          }
        }
      };
      _msg.getFolder().updateMessage(_msg.getStoredEmail(), c);
      synchronized (wait) { if (result[0] == null) wait.wait();}

      if (exception[0] != null) {
        throw new Exception(exception[0]);
      }
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }      
  
  public void setFlag(String flag, boolean value) {
    if ("\\Deleted".equalsIgnoreCase(flag))
      _msg.getStoredEmail().getFlags().setDeleted(value);
    else if ("\\Answered".equalsIgnoreCase(flag))
      _msg.getStoredEmail().getFlags().setAnswered(value);
    else if ("\\Seen".equalsIgnoreCase(flag))
      _msg.getStoredEmail().getFlags().setSeen(value);
    else if ("\\Flagged".equalsIgnoreCase(flag))
      _msg.getStoredEmail().getFlags().setFlagged(value);
    else if ("\\Draft".equalsIgnoreCase(flag))
      _msg.getStoredEmail().getFlags().setDraft(value);
    else	 
   	 _msg.getStoredEmail().getFlags().setFlag(flag, value);
  }
    
    public boolean isRecent() {
      return _msg.getStoredEmail().getFlags().isRecent();
  }
    
    public boolean isDeleted() {
      return _msg.getStoredEmail().getFlags().isDeleted();
  }
    
    public boolean isSeen() {
      return _msg.getStoredEmail().getFlags().isSeen();
  }
    
    public boolean isAnswered() {
      return _msg.getStoredEmail().getFlags().isAnswered();
  }
    
    public boolean isFlagged() {
      return _msg.getStoredEmail().getFlags().isFlagged();
  }
    
    public boolean isDraft() {
      return _msg.getStoredEmail().getFlags().isDraft();
  }
    
    public boolean isSet(String flag) {
	return _msg.getStoredEmail().getFlags().isSet(flag);
    }

  /**
    * Returns a Vector representation of the flagList
   * @return the Vector of the set flags
   */
  public Vector flagList() {
      return _msg.getStoredEmail().getFlags().flagList();
  }

  public String toFlagString() {
    StringBuffer flagBuffer = new StringBuffer();

    if (isSeen())
      flagBuffer.append("\\Seen ");
    if (isRecent())
      flagBuffer.append("\\Recent ");
    if (isDeleted())
      flagBuffer.append("\\Deleted ");
    if (isAnswered())
      flagBuffer.append("\\Answered ");
    if (isFlagged())
      flagBuffer.append("\\Flagged ");
    if (isDraft())
      flagBuffer.append("\\Draft ");

    Enumeration e =  _msg.getStoredEmail().getFlags().getFlagList().keys();
    while (e.hasMoreElements()) {
  	String flag = (String) e.nextElement();
  	if (isSet(flag)) {
  	    flagBuffer.append(flag + " ");
	}
    }
    return "(" + flagBuffer.toString().trim() + ")";
  }
}




