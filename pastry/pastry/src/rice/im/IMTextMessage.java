package rice.im;

import java.util.*;

import rice.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.email.log.*;
import rice.email.messaging.*;


public class IMTextMessage extends IMContent {
    

    public IMTextMessage(PostUserAddress sender, Buddy [] recipients, String msg, IMState state) {
	super(sender, recipients, msg, state);
    }



      /**
   * Returns the text content of this message.
   *
   * @return The text content of this message.
   */
  public String getTextMessage() {
      return msg;
  }

}









