package rice.email.proxy.smtp.manager;

import java.io.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.post.*;
import rice.email.*;
import rice.email.proxy.dns.*;
import rice.email.proxy.smtp.client.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.postbox.*;

public class SimpleManager implements SmtpManager {

  public static int MAX_SIZE = 8000000;
  public static String[] POST_HOST = new String[] {"dosa.cs.rice.edu", "thor05.cs.rice.edu", ".epostmail.org"};
  public static String SMTP_HOST = "moe.rice.edu";

  private boolean gateway;

  private DnsService dns;
  
  private EmailService email;

  private PostEntityAddress address;
 
  static {
    String s = System.getProperty("POST_HOST");
    
    if ((s != null) && (s.length() > 2)) {
      System.out.println("Using alternative POST_HOST:" + s);
      POST_HOST = new String[] {s};
    }
  }

  public SimpleManager(EmailService email, boolean gateway, PostEntityAddress address) throws Exception {
    this.email = email;
    this.dns = new DnsServiceImpl();
    this.gateway = gateway;
    this.address = address;
  }

  public String checkSender(SmtpState state, MailAddress sender) {
    if ((! gateway) && (! isPostAddress(sender)))
      return sender + ": Sender address rejected: Relay access denied";
    else 
      return null;
  }

  public String checkRecipient(SmtpState state, MailAddress rcpt) {
    if (gateway && (! isPostAddress(rcpt)))
      return rcpt + ": Recipient address rejected: Relay access denied";
    else 
      return null;
  }

  public String checkData(SmtpState state) { 
    return null;
  }
  
  public boolean isPostAddress(MailAddress addr) {
    for (int i=0; i<POST_HOST.length; i++) 
      if (addr.getHost().toLowerCase().endsWith(POST_HOST[i].toLowerCase()))
        return true;
    
    return false;
  }

  public void send(SmtpState state, boolean local) throws Exception {
    Vector postRecps = new Vector();
    Vector nonPostRecps = new Vector();
    Iterator i = state.getMessage().getRecipientIterator();

    while (i.hasNext()) {
      MailAddress addr = (MailAddress) i.next();

      if (isPostAddress(addr)) {
        postRecps.add(addr);
      } else {
        nonPostRecps.add(addr);
      }
    }

    MailAddress[] recipients = (MailAddress[]) postRecps.toArray(new MailAddress[0]);

    if (state.getMessage().getResource().getSize() > MAX_SIZE)
      throw new IOException("Messages over " + MAX_SIZE + " are not supported by ePOST - message is " + state.getMessage().getResource().getSize());
    else
      System.out.println("COUNT: " + System.currentTimeMillis() + " Sending message of size " + state.getMessage().getResource().getSize() + " to " + postRecps.size() + " POST recipeints and " + nonPostRecps.size() + " normal recipients.");
    
    Email email = null;
    
    if (gateway) {
      email = PostMessage.parseEmail(state.getRemote(), recipients, state.getMessage().getResource(), address);
    } else {
      email = PostMessage.parseEmail(state.getRemote(), recipients, state.getMessage().getResource());
    }
    
    ExternalContinuation c = new ExternalContinuation();
    this.email.sendMessage(email, c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw c.getException(); } 

    for (int j=0; j<nonPostRecps.size();  j++) {
      MailAddress addr = (MailAddress) nonPostRecps.elementAt(j);
//      String[] hosts = dns.lookup(addr.getHost());
      String[] hosts = new String[] {SMTP_HOST};

      if (hosts.length == 0) {
        System.out.println( "No MX records found for " + addr.getHost());
      } else {
        System.out.println("A message is headed to " + addr + " at " + hosts[0]);

        try {
          Reader content = state.getMessage().getContent();
          SmtpClient client = new SmtpClient(hosts[0]);
          client.connect();
          client.send(state.getMessage().getReturnPath().toString(), addr.toString(), content);
          client.close();
        } catch (Exception e) {
          System.out.println("Couldn't send a message to " + addr + " due to " + e);
        }
      }
    }
  }
}
