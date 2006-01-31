package rice.email.proxy.imap.commands.search;

import rice.*;
import rice.Continuation.*;

import rice.email.*;

import rice.email.proxy.imap.ImapConnection;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mail.*;

public class NumberArgSearchPart extends SearchPart {

  int argument;

  public boolean includes(StoredMessage msg) {
    try {
      if (getType().equals("LARGER")) {
        EmailMessagePart message = msg.getMessage().getContent();
        
        return (message.getSize() > getArgument());
      } else if (getType().equals("SMALLER")) {
        EmailMessagePart message = msg.getMessage().getContent();
        
        return (message.getSize() < getArgument());
      } else {
        return false;
      }
    } catch (MailboxException e) {
      System.out.println("Exception " + e + " was thrown in NumArgSearchPart.");
      return false;
    } 
  }
  
  public void setArgument(int argument) {
    this.argument = argument;
  }

  public int getArgument() {
    return argument;
  }
}