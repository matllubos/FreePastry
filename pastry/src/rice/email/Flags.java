package rice.email;

import java.util.*;

import rice.*;
import rice.email.*;
import rice.post.log.*;

/**
 * Flags object to store the flags of an email
 *
 * @version   $Id$
 * @author
 */
public class Flags implements java.io.Serializable {

  boolean _recent;
  boolean _deleted;
  boolean _seen;
  boolean _answered;
  boolean _flagged;
  boolean _draft;

  /**
   * Constructor for email Flags
   */
  public Flags() {
    _deleted = false;
    _seen = false;
    _answered = false;
    _flagged = false;
    _draft = false;
  }


  /**
   * Gets the Recent attribute of the Flags object
   *
   * @return   The Recent value
   */
  public boolean isRecent() {
    return _recent;
  }

  /**
   * Gets the Deleted attribute of the Flags object
   *
   * @return   The Deleted value
   */
  public boolean isDeleted() {
    return _deleted;
  }

  /**
   * Gets the Answered attribute of the Flags object
   *
   * @return   The Answered value
   */
  public boolean isAnswered() {
    return _answered;
  }

  /**
   * Gets the Seen attribute of the Flags object
   *
   * @return   The Seen value
   */
  public boolean isSeen() {
    return _seen;
  }

  /**
   * Gets the Flagged attribute of the Flags object
   *
   * @return   The Flagged value
   */
  public boolean isFlagged() {
    return _flagged;
  }

  /**
   * Gets the Draft attribute of the Flags object
   *
   * @return   The Draft value
   */
  public boolean isDraft() {
    return _draft;
  }

  /**
   * Sets the Deleted attribute of the Flags object
   *
   * @param value  The new Deleted value
   */
  public void setDeleted(boolean value) {
    _deleted = value;
  }

  /**
   * Sets the Answered attribute of the Flags object
   *
   * @param value  The new Answered value
   */
  public void setAnswered(boolean value) {
    _answered = value;
  }

  /**
   * Sets the Seen attribute of the Flags object
   *
   * @param value  The new Seen value
   */
  public void setSeen(boolean value) {
    _seen = value;
  }

  /**
   * Sets the Flagged attribute of the Flags object
   *
   * @param value  The new Flagged value
   */
  public void setFlagged(boolean value) {
    _flagged = value;
  }

  /**
   * Sets the Draft attribute of the Flags object
   *
   * @param value  The new Draft value
   */
  public void setDraft(boolean value) {
    _draft = value;
  }

  /**
   * Returns a Vector representation of the flagList
   *
   * @return   the Vector of the set flags
   */
  public Vector flagList() {
    Vector flaglist = new Vector();
    if (isRecent()) {
      flaglist.add("\\Recent");
    }
    if (isSeen()) {
      flaglist.add("\\Seen");
    }
    if (isDeleted()) {
      flaglist.add("\\Deleted");
    }
    if (isAnswered()) {
      flaglist.add("\\Answered");
    }
    if (isFlagged()) {
      flaglist.add("\\Flagged");
    }
    if (isDraft()) {
      flaglist.add("\\Draft");
    }

    return flaglist;
  }

  /**
   * Returns a string representation of the flags
   *
   * @return   The string representation of the flags
   */
  public String toFlagString() {
    StringBuffer flagBuffer = new StringBuffer();

    if (_seen) {
      flagBuffer.append("\\Seen ");
    }

    if (_recent) {
      flagBuffer.append("\\Recent ");
    }

    if (_deleted) {
      flagBuffer.append("\\Deleted");
    }

    if (_answered) {
      flagBuffer.append("\\Answered");
    }

    if (_draft) {
      flagBuffer.append("\\Draft");
    }

    if (_flagged) {
      flagBuffer.append("\\Flagged");
    }

    return "(" + flagBuffer.toString().trim() + ")";
  }

}

