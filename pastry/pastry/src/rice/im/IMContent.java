package rice.im;

import java.util.*;

import rice.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.im.log.*;
import rice.im.messaging.*;

/* Represents a notion of a message in the POST system. This class is designed to be a small representation of an IM message.
   */


public abstract class IMContent implements java.io.Serializable {


    PostUserAddress sender; 
    Buddy[] recipients;  
    String msg = "(NULL)"; // represents the message that the user wants to send 
    IMState sender_state;

    /**
     * Constructs an IMMessage.
     *
     * @param sender: The address of the sender of the message
     * @param recipients: The address of the recipients of the message
     * @param msg: The actual message that's being sent across
     *

     **/

    public IMContent(PostUserAddress sender, 
		     Buddy [] recipients, 
		     String msg, IMState state) {
	this.sender = sender;
	if (recipients.length != 0) {
	    this.recipients = new Buddy[recipients.length];
	    System.arraycopy(recipients,0, this.recipients, 0, recipients.length);
	}
	this.msg = msg;
	sender_state = state;
	}	
    
    /**
   * Returns the sender of this message.
   *
   * @return The sender of this message.
   */
    public PostUserAddress getSender() {
	return sender;
    }

       
  /**
   * Returns the recipients of this message.
   *
   * @return The recipients of this message
   */
  public Buddy[] getRecipients() {
      return recipients;
  }

    
    /**
     * Returns true if Object o is equal to this
     * @return true if Object o is equal to this
     */
    public boolean equals(Object o) {
	return true;
    }


    public String toString() {
	String str = "\n";
	str+="-----------------------------------------An IMContent Message:-----------------------------------\n";
	str+="Sender: " + getSender();
	for (int i= 0; i < recipients.length; i++)
	    str+="Recipient " + i + " : " + recipients[i];
	str+="Message = " + msg + "\n";
	return str;
    }

    public IMState getState() {
	return sender_state;
    }

}



