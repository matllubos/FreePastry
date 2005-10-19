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
        ExternalContinuation c = new ExternalContinuation();
        msg.getMessage().getContent(c);
        c.sleep();

        if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

        EmailMessagePart message = (EmailMessagePart) c.getResult();
        
        return (message.getSize() > getArgument());
      } else if (getType().equals("SMALLER")) {
        ExternalContinuation c = new ExternalContinuation();
        msg.getMessage().getContent(c);
        c.sleep();

        if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

        EmailMessagePart message = (EmailMessagePart) c.getResult();
        
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