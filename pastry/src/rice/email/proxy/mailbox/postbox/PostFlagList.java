package rice.email.proxy.mailbox.postbox;

import rice.*;
import rice.email.*;
import rice.email.proxy.mailbox.*;

import java.util.*;

public class PostFlagList implements FlagList {

    PostMessage _msg;

  private static Hashtable loc = new Hashtable();

  private PostFlagList(PostMessage msg) {
      _msg = msg;
  }

  public static PostFlagList get(PostMessage msg) {

    if (loc.get(msg.getStoredEmail().getEmail()) == null) {
      loc.put(msg.getStoredEmail().getEmail(), new PostFlagList(msg));
    }
      return (PostFlagList) loc.get(msg.getStoredEmail().getEmail());
      // this is returning emails. Should it be returning MimedMessages??
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
	  _msg.getStoredEmail().getFlags().setDeleted(value);
      if ("\\Answered".equalsIgnoreCase(flag))
	  _msg.getStoredEmail().getFlags().setAnswered(value);
      if ("\\Seen".equalsIgnoreCase(flag))
	  _msg.getStoredEmail().getFlags().setSeen(value);
      if ("\\Flagged".equalsIgnoreCase(flag))
	  _msg.getStoredEmail().getFlags().setFlagged(value);
      if ("\\Draft".equalsIgnoreCase(flag))
	  _msg.getStoredEmail().getFlags().setDraft(value);

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
      }catch (Exception e) {
	  
      }
  }


  public void commit()
  {
  }

  public boolean isRecent()
  {
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


    /** 
     * Returns a Vector representation of the flagList
     * @return the Vector of the set flags
     */
    public Vector flagList() {
	Vector flaglist= new Vector();
	if (isRecent())
	    flaglist.add("\\Recent");
	if (isSeen())
	    flaglist.add("\\Seen");
	if (isDeleted())
	    flaglist.add("\\Deleted");
	if (isAnswered())
	    flaglist.add("\\Answered");
	if (isFlagged())
	    flaglist.add("\\Flagged");
	if (isDraft())
	    flaglist.add("\\Draft");

	return flaglist;
    }

  public String toFlagString()
  {
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

    return "(" + flagBuffer.toString().trim() + ")";
  }
}




