package rice.email.proxy.smtp.manager;

import java.io.*;
import java.net.InetAddress;
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

  private boolean relayLocal;

  private DnsService dns;
  
  private EmailService email;

  private PostEntityAddress address;
  
  private String server;
 
  protected Environment environment;
  
  protected boolean authenticate;
  
  protected String smtpUsername;
  
  protected String smtpPassword;
  
  protected Logger logger;
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
    this.relayLocal = environment.getParameters().getBoolean("email_smtp_relay_from_localhost");
    this.logger = environment.getLogManager().getLogger(SimpleManager.class, null);

    this.authenticate = environment.getParameters().getBoolean("email_smtp_send_authentication"); 
    if (authenticate) {
      this.smtpUsername = environment.getParameters().getString("email_smtp_username");
      this.smtpPassword = environment.getParameters().getString("email_smtp_password");
    }
  }
  
  public InetAddress getLocalHost() {
    return email.getLocalHost(); 
  }

  public String checkSender(SmtpConnection conn, SmtpState state, MailAddress sender) {
	  boolean isLocal = false;
	  try {
		  isLocal = conn.isLocal();
	  } catch (IOException e) {
		  if (logger.level <= Logger.INFO)
			  logger.logException("Exception checking local port: ",e);
	  }
    if ((!isLocal || !relayLocal) && (! gateway) && (! isPostAddress(sender)))
      return sender + ": Sender address rejected: Relay access denied";
    else 
      return null;
  }

  public String checkRecipient(SmtpConnection conn, SmtpState state, MailAddress rcpt) {
	  boolean isLocal = false;
	  try {
		  isLocal = conn.isLocal();
	  } catch (IOException e) {
		  if (logger.level <= Logger.INFO)
			  logger.logException("Exception checking local port: ",e);
	  }
    if ((!isLocal || !relayLocal) && gateway && (! isPostAddress(rcpt)))
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
  
  public boolean isPostAddress(final MailAddress addr) {
    ExternalRunnable c = new ExternalRunnable() {
      protected void run(Continuation c) {
        isPostAddress(addr, c);
      }
    };
    c.invokeAndSleep(environment);
    
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
                    if (logger.level <= Logger.FINE) logger.logException(
                        "got exception looking for PostLog for "+addr+" in isPostAddress", result);
                    c.receiveException(result);
                  }
            });
        return;
      }
      c.receiveResult(Boolean.FALSE);
  }

  public void send(SmtpState state, boolean local) throws Exception {
    final HashSet postRecps = new HashSet();
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
    ExternalRunnable c = new ExternalRunnable() {
      protected void run(Continuation c) {
        SimpleManager.this.email.expand((PostUserAddress[]) postRecps.toArray(new PostUserAddress[0]), SimpleManager.this, c);
      }
    };
    c.invokeAndSleep(environment);
    
    if (c.exceptionThrown())
      throw c.getException();
    
    Object[] all = (Object[]) c.getResult();
    
    for (int j=0; j<all.length; j++) 
      if (all[j] instanceof PostUserAddress)
        postRecps.add(all[j]);
      else
        nonPostRecps.add(new MailAddress((String) all[j]));
    
    PostUserAddress[] recipients = (PostUserAddress[]) postRecps.toArray(new PostUserAddress[0]);
    
    if (logger.level <= Logger.FINER) logger.log(
        "Sending message of size " + state.getMessage().getResource().getSize() + " to " + postRecps.size() + " POST recipeints and " + nonPostRecps.size() + " normal recipients.");
    
    final Email email = PostMessage.parseEmail(getLocalHost(), state.getRemote(), recipients, state.getMessage().getResource(), address, state.getEnvironment());
    
    ExternalRunnable d = new ExternalRunnable() {
      protected void run(Continuation d) throws PostException {
        SimpleManager.this.email.sendMessage(email, d);
      }
    };
    d.invokeAndSleep(environment);
    
    if (d.exceptionThrown())
      throw d.getException(); 
    
    if (! d.getResult().equals(Boolean.TRUE)) 
      throw new RuntimeException("Sending of email did not succeed: " + d.getResult());

    Iterator it = nonPostRecps.iterator();
    
    while (it.hasNext()) {
      MailAddress addr = (MailAddress) it.next();
      String host = server;
      
      if ((host == null) || (host.equals(""))) {
        if (logger.level <= Logger.WARNING) logger.log(
            "WARNING: No default SMTP server specified - using DNS MX records for " + addr.getHost() + " to send email.");
        String[] hosts = dns.lookup(addr.getHost());
        
        if (hosts.length == 0)
          throw new IOException("Unable to send SMTP message to " + addr + " - no DNS records found and no default host specified.");
        
        host = hosts[0];
      } 
      
      if (logger.level <= Logger.FINER) logger.log(
          "A message is headed to " + addr + " using SMTP server at " + host);
      
      try {
        Reader content = state.getMessage().getContent();
        SmtpClient client = new SmtpClient(host, environment);
        client.connect();
        if (authenticate) {
          client.send(state.getMessage().getReturnPath().toString(), addr.toString(), content, smtpUsername, smtpPassword);
        } else {
          client.send(state.getMessage().getReturnPath().toString(), addr.toString(), content);
        }
        client.close();
      } catch (Exception e) {
        throw new IOException("Couldn't send a message to " + addr + " due to " + e);
      }
    }
  }  
}
