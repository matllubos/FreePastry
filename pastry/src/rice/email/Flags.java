package rice.email;

import rice.*;
import rice.post.log.*;
import rice.email.*;

import java.util.*;

/**
 * Flags object to store the flags of an email
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

  //    void addFlag(String flag) {
//  	setFlag(flag, true);
//      }

//      void removeFlag(String flag) {
//  	setFlag(flag, false);
//      }

    public void setDeleted(boolean value) {
	_deleted = value;
    }

    public void setAnswered(boolean value) {
	_answered = value;
    }

    public void setSeen(boolean value) {
	_seen = value;
    }

    public void setFlagged(boolean value) {
	_flagged = value;
    }

    public void setDraft(boolean value) {
	_draft = value;
    }



    public boolean isRecent() {
	return _recent;
    }

    public boolean isDeleted() {
	return _deleted;
    }

    public boolean isAnswered() {
	return _answered;
    }

    public boolean isSeen() {
	return _seen;
    }

    public boolean isFlagged() {
	return _flagged;
    }

    public boolean isDraft() {
	return _draft;
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

    /**
     * Returns a string representation of the flags
     * @return The string representation of the flags
     */ 
    public String toFlagString()
    {
	StringBuffer flagBuffer = new StringBuffer();
	
	if (_seen)
	    flagBuffer.append("\\Seen ");
	
	if (_recent)
	    flagBuffer.append("\\Recent ");
	
	if (_deleted)
	    flagBuffer.append("\\Deleted");

	if (_answered)
	    flagBuffer.append("\\Answered");

	if (_draft)
	    flagBuffer.append("\\Draft");

	if(_flagged)
	    flagBuffer.append("\\Flagged");
	
	return "(" + flagBuffer.toString().trim() + ")";
    }

}







