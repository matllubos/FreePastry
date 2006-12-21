/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.email.proxy.mailbox.postbox;

import rice.*;
import rice.Continuation.*;
import rice.email.*;
import rice.email.proxy.mailbox.*;

import java.util.*;

public class PostFlagList implements FlagList {
  
  /**
   * The static hashtable mapping message -> flag list, ensuring that
   * there is only ever one list per message
   */
  private static WeakHashMap FLAG_MAP = new WeakHashMap();

  /**
   * The internal message which the flag list holds the flags for
   */
  protected PostMessage message;
  
  /**
   * Any session flags, which are not permanently stored.  NOTE: Currently,
   * this is not implemented on a per-session basis, so all sessions will
   * see each other's flags.  Too bad.
   */
  protected HashSet sessionFlags;

  /**
   * Protected constructor which takes in the wrapped message.
   * This constructor does *NOT* set the recent flag.
   */
  protected PostFlagList(PostMessage message) {
    this.message = message;
    this.sessionFlags = new HashSet();
  }

  /**
   * Method by which other classes can get the flag list for a 
   * given message
   *
   * @param msg The message to wrap
   * @return The flag list for the message
   */
  public static PostFlagList get(PostMessage msg) {
    PostFlagList result = (PostFlagList) FLAG_MAP.get(msg.getStoredEmail());
    
    if (result == null) {
      result = new PostFlagList(msg);
      FLAG_MAP.put(msg.getStoredEmail(), result);
    }

    return result;
  }

  /**
   * Causes any changes in this FlagList's state to be written to
   * the associated Mailbox. This allows colapsing several changes
   * into one disk write, one SQL command, etc.
   */
  public void commit() throws MailboxException {
    try {
      new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          message.getFolder().updateMessage(message.getStoredEmail(), c);
        }
      }.invoke(message.getFolder().getPost().getEnvironment());
    } catch (Exception e) {
      throw new MailboxException(e);
    }
  }   
  
  /**
   * Gets the Deleted attribute of the Flags object
   *
   * @return The Deleted value
   */
  public boolean isDeleted() {
    return isSet(DELETED_FLAG);
  }
  
  /**
   * Gets the Answered attribute of the Flags object
   *
   * @return The Answered value
   */
  public boolean isAnswered() {
    return isSet(ANSWERED_FLAG);
  }
  
  /**
   * Gets the Seen attribute of the Flags object
   *
   * @return The Seen value
   */
  public boolean isSeen() {
    return isSet(SEEN_FLAG);
  }
  
  /**
   * Gets the Flagged attribute of the Flags object
   *
   * @return The Flagged value
   */
  public boolean isFlagged() {
    return isSet(FLAGGED_FLAG);
  }
  
  /**
   * Gets the Draft attribute of the Flags object
   *
   * @return The Draft value
   */
  public boolean isDraft() {
    return isSet(DRAFT_FLAG);
  }
  
  /**
   * Returns whether or not the given flag is set
   *
   * @param flag The flag to check
   * @return Whether or not it is set
   */
  public boolean isSet(String flag) {
    if (flag.equalsIgnoreCase(RECENT_FLAG)) {
      return isSessionFlagSet(RECENT_FLAG);
    } else {
      if (flag.equalsIgnoreCase(DELETED_FLAG))
        flag = DELETED_FLAG;
      else if (flag.equalsIgnoreCase(ANSWERED_FLAG))
        flag = ANSWERED_FLAG;
      else if (flag.equalsIgnoreCase(FLAGGED_FLAG))
        flag = FLAGGED_FLAG;
      else if (flag.equalsIgnoreCase(DRAFT_FLAG))
        flag = DRAFT_FLAG;
      else if (flag.equalsIgnoreCase(SEEN_FLAG))
        flag = SEEN_FLAG;
      
      return message.getStoredEmail().getFlags().isSet(flag);
    }    
  }
  
  /**
   * Sets the Deleted attribute of the Flags object
   *
   * @param value The new Deleted value
   */
  public void setDeleted(boolean value) {
    setFlag(DELETED_FLAG, value);
  }
  
  /**
   * Sets the Answered attribute of the Flags object
   *
   * @param value The new Answered value
   */
  public void setAnswered(boolean value) {
    setFlag(ANSWERED_FLAG, value);
  }
  
  /**
   * Sets the Seen attribute of the Flags object
   *
   * @param value The new Seen value
   */
  public void setSeen(boolean value) {
    setFlag(SEEN_FLAG, value);
  }
  
  /**
   * Sets the Flagged attribute of the Flags object
   *
   * @param value The new Flagged value
   */
  public void setFlagged(boolean value) {
    setFlag(FLAGGED_FLAG, value);
  }
  
  /**
   * Sets the Draft attribute of the Flags object
   *
   * @param value The new Draft value
   */
  public void setDraft(boolean value) {
    setFlag(DRAFT_FLAG, value);
  }
  
  /**
   * Sets the given flag, if value is true, removes it 
   * otherwise
   *
   * @param flag The flag
   * @param value The value
   */
  public void setFlag(String flag, boolean value) {
    if (flag.equalsIgnoreCase(RECENT_FLAG)) {
      setSessionFlag(RECENT_FLAG, value);
    } else {
      if (flag.equalsIgnoreCase(DELETED_FLAG))
        flag = DELETED_FLAG;
      else if (flag.equalsIgnoreCase(ANSWERED_FLAG))
        flag = ANSWERED_FLAG;
      else if (flag.equalsIgnoreCase(FLAGGED_FLAG))
        flag = FLAGGED_FLAG;
      else if (flag.equalsIgnoreCase(DRAFT_FLAG))
        flag = DRAFT_FLAG;
      else if (flag.equalsIgnoreCase(SEEN_FLAG))
        flag = SEEN_FLAG;

      message.getStoredEmail().getFlags().setFlag(flag, value);
    }
  }
  
  /**
   * Gets the Recent attribute of the Flags object
   *
   * @return The Recent value
   */
  public boolean isRecent() {
    return isSessionFlagSet(RECENT_FLAG);
  }
  
  /**
   * Sets the Recent attribute of the Flags object
   *
   * @param value The new Recent value
   */
  public void setRecent(boolean value) {
    setSessionFlag(RECENT_FLAG, value);
  }
  
  /**
   * Returns whether or not the given session flag is set
   *
   * @param flag The flag to check
   * @return Whether or not it's set
   */
  public boolean isSessionFlagSet(String flag) {
    return sessionFlags.contains(flag);
  }
  
  /**
   * Sets the given session flag, if value is true, removes it 
   * otherwise
   *
   * @param flag The flag
   * @param value The value
   */
  public void setSessionFlag(String flag, boolean value) {
    if (value)
      sessionFlags.add(flag);
    else
      sessionFlags.remove(flag);
  }
  
  /**
   * Returns a vector containing all of the flags
   *
   * @return A Vector containing all of the flags
   */
  public List getFlags() {
    return message.getStoredEmail().getFlags().flagList();
  }
  
  /**
   * Returns a vector containing all of the session flags
   *
   * @return A Vector containing all of the session flags
   */
  public Set getSessionFlags() {
    return sessionFlags;
  }

  /**
   * Returns a string representation of the flags
   * 
   * @return THe flags, in string form
   */
  public String toFlagString() {
    StringBuffer flagBuffer = new StringBuffer();
    Iterator i = getFlags().iterator();
    
    while (i.hasNext()) 
      flagBuffer.append(i.next() + " ");
    
    Iterator j = getSessionFlags().iterator();
    
    while (j.hasNext())
      flagBuffer.append(j.next() + " ");
    
    return "(" + flagBuffer.toString().trim() + ")";
  }
}




