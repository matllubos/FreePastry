package rice.email.proxy.smtp.manager;

import java.io.*;
import java.util.*;

import org.jfree.chart.labels.StandardContourToolTipGenerator;

import rice.*;
import rice.Continuation.*;
import rice.post.*;
import rice.email.*;
import rice.email.proxy.dns.*;
import rice.email.proxy.smtp.client.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

public class SimpleManager implements SmtpManager {

  public final String[] POST_HOST;// = new String[] {"dosa.cs.rice.edu", "thor05.cs.rice.edu", ".epostmail.org"};

  private boolean gateway;

  private DnsService dns;
  
  private EmailService email;

  private PostEntityAddress address;
  
  private String server;
 
  protected Environment environment;
  
//  static {
//    String s = System.getProperty("POST_HOST");
//    
//    if ((s != null) && (s.length() > 2)) {
//      System.outt.println("Using alternative POST_HOST:" + s);
//      POST_HOST = new String[] {s};
//    }
//  }

  public SimpleManager(EmailService email, boolean gateway, PostEntityAddress address, String server, Environment env) throws Exception {
    this.environment = env;
    POST_HOST = environment.getParameters().getStringArray("email_smtp_manager_simpleManager_post_host");
    this.email = email;
    this.dns = new DnsServiceImpl(env);
    this.gateway = gateway;
    this.address = address;
    this.server = server;
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
  
  public boolean isPostAddress(String string) {
    try {
      return isPostAddress(new MailAddress(string));
    } catch (MalformedAddressException e) {
      return false;
    }
  }
  
  public boolean isPostAddress(MailAddress addr) {
    ExternalContinuation c = new ExternalContinuation();

    isPostAddress(addr, c);

    c.sleep();

    if (c.exceptionThrown()) {
      return false;
    } else {
      return ((Boolean)c.getResult()).booleanValue();
    }
  }
  
  public void isPostAddress(String addr, Continuation c) {
    try {
      isPostAddress(new MailAddress(addr), c);
    } catch (MalformedAddressException e) {
      // maybe we should do receieveException, but this is for backwards compatibility
      c.receiveResult(Boolean.FALSE);
    }
  }
  
  public void isPostAddress(final MailAddress addr, final Continuation c) {
    for (int i=0; i<POST_HOST.length; i++) 
      if (addr.getHost().toLowerCase().endsWith(POST_HOST[i].toLowerCase())) {
        email.getPost().getPostLog(
            new PostUserAddress(PostMessage.factory, addr.toString(),
                environment), new Continuation() {

                  public void receiveResult(Object result) {
                    c.receiveResult(new Boolean(result != null));
                  }

                  public void receiveException(Exception result) {
                    logException(Logger.FINE,
                        "got exception looking for PostLog for "+addr+" in isPostAddress", result);
                    c.receiveException(result);
                  }
            });
        return;
      }
      c.receiveResult(Boolean.FALSE);
  }

  public void send(SmtpState state, boolean local) throws Exception {
    HashSet postRecps = new HashSet();
    HashSet nonPostRecps = new HashSet();
    Iterator i = state.getMessage().getRecipientIterator();

    while (i.hasNext()) {
      MailAddress addr = (MailAddress) i.next();

      if (isPostAddress(addr)) {
        postRecps.add(new PostUserAddress(PostMessage.factory, addr.toString(), state.getEnvironment()));
      } else {
        nonPostRecps.add(addr);
      }
    }

    // now, do the expansion to the full mailing lists    
    ExternalContinuation c = new ExternalContinuation();
    this.email.expand((PostUserAddress[]) postRecps.toArray(new PostUserAddress[0]), this, c);
    c.sleep();
    
    if (c.exceptionThrown())
      throw c.getException();
    
    Object[] all = (Object[]) c.getResult();
    
    for (int j=0; j<all.length; j++) 
      if (all[j] instanceof PostUserAddress)
        postRecps.add(all[j]);
      else
        nonPostRecps.add(new MailAddress((String) all[j]));
    
    PostUserAddress[] recipients = (PostUserAddress[]) postRecps.toArray(new PostUserAddress[0]);
    
    log(Logger.FINER, "Sending message of size " + state.getMessage().getResource().getSize() + " to " + postRecps.size() + " POST recipeints and " + nonPostRecps.size() + " normal recipients.");
    
    Email email = PostMessage.parseEmail(state.getRemote(), recipients, state.getMessage().getResource(), address, state.getEnvironment());
    
    ExternalContinuation d = new ExternalContinuation();
    this.email.sendMessage(email, d);
    d.sleep();

    if (d.exceptionThrown())
      throw d.getException(); 
    
    if (! d.getResult().equals(Boolean.TRUE)) 
      throw new RuntimeException("Sending of email did not succeed: " + d.getResult());

    Iterator it = nonPostRecps.iterator();
    
    while (it.hasNext()) {
      MailAddress addr = (MailAddress) it.next();
      String host = server;
      
      if ((host == null) || (host.equals(""))) {
        log(Logger.WARNING, "WARNING: No default SMTP server specified - using DNS MX records for " + addr.getHost() + " to send email.");
        String[] hosts = dns.lookup(addr.getHost());
        
        if (hosts.length == 0)
          throw new IOException("Unable to send SMTP message to " + addr + " - no DNS records found and no default host specified.");
        
        host = hosts[0];
      } 
      
      log(Logger.FINER, "A message is headed to " + addr + " using SMTP server at " + host);
      
      try {
        Reader content = state.getMessage().getContent();
        SmtpClient client = new SmtpClient(host, environment);
        client.connect();
        client.send(state.getMessage().getReturnPath().toString(), addr.toString(), content);
        client.close();
      } catch (Exception e) {
        throw new IOException("Couldn't send a message to " + addr + " due to " + e);
      }
    }
  }
  
  private void log(int level, String m) {
    environment.getLogManager().getLogger(SimpleManager.class, null).log(level, m); 
  }
  
  private void logException(int level, String m, Throwable t) {
    environment.getLogManager().getLogger(SimpleManager.class,null).logException(level, m, t);
  }
}
