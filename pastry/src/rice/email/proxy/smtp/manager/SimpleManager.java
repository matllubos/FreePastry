/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
    try {
      ExternalContinuationRunnable c = new ExternalContinuationRunnable() {
        protected void execute(Continuation c) {
          isPostAddress(addr, c);
        }
      };
      return ((Boolean)c.invoke(environment)).booleanValue();
    } catch (Exception e) {
      return false;
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
    ExternalContinuationRunnable c = new ExternalContinuationRunnable() {
      protected void execute(Continuation c) {
        SimpleManager.this.email.expand((PostUserAddress[]) postRecps.toArray(new PostUserAddress[0]), SimpleManager.this, c);
      }
    };
    
    Object[] all = (Object[]) c.invoke(environment);
    
    for (int j=0; j<all.length; j++) 
      if (all[j] instanceof PostUserAddress)
        postRecps.add(all[j]);
      else
        nonPostRecps.add(new MailAddress((String) all[j]));
    
    PostUserAddress[] recipients = (PostUserAddress[]) postRecps.toArray(new PostUserAddress[0]);
    
    if (logger.level <= Logger.FINER) logger.log(
        "Sending message of size " + state.getMessage().getResource().getSize() + " to " + postRecps.size() + " POST recipeints and " + nonPostRecps.size() + " normal recipients.");
    
    final Email email = PostMessage.parseEmail(getLocalHost(), state.getRemote(), recipients, state.getMessage().getResource(), address, state.getEnvironment());
    
    ExternalContinuationRunnable d = new ExternalContinuationRunnable() {
      protected void execute(Continuation d) throws PostException {
        SimpleManager.this.email.sendMessage(email, d);
      }
    };
    
    Object result = d.invoke(environment);
    if (! result.equals(Boolean.TRUE)) 
      throw new RuntimeException("Sending of email did not succeed: " + result);

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
