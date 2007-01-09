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
package rice.email.proxy;

import java.io.*;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;

import rice.Continuation;
import rice.Continuation.ExternalContinuationRunnable;
import rice.email.EmailService;
import rice.email.messaging.EmailNotificationMessage;
import rice.email.proxy.imap.*;
import rice.email.proxy.mailbox.postbox.PostMailboxManager;
import rice.email.proxy.mailbox.postbox.PostMessage;
import rice.email.proxy.pop3.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.user.UserManagerImpl;
import rice.email.proxy.web.WebServer;
import rice.email.proxy.web.WebServerImpl;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.post.messaging.NotificationMessage;
import rice.post.proxy.PostProxy;
import rice.post.rawserialization.NotificationMessageDeserializer;

/**
* This class starts up everything on the Pastry side, and then
 * boots up the PAST, Scribe, POST, and Emails services, and then
 * starts the Foedus IMAP and SMTP servers.
 */
public class EmailProxy extends PostProxy {
 
  protected EmailService email;

  protected UserManagerImpl manager;

  protected SmtpServer smtp;

  protected Pop3Server pop3;
     
  protected ImapServer imap;
  
  protected WebServer web;
  
  protected rice.email.Folder emailFolder;
     
  /**
   * Method which initializes the mailcap
   *
   * @param parameters The parameters to use
   */  
  protected void startMailcap(Parameters parameters) throws Exception {
    stepStart("Installing custom Mailcap entries");
    MailcapCommandMap cmdmap = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
    cmdmap.addMailcap("application/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
    cmdmap.addMailcap("image/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
    cmdmap.addMailcap("audio/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
    cmdmap.addMailcap("video/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
    CommandMap.setDefaultCommandMap(cmdmap);    
    stepDone(SUCCESS);
  }
   
   /**
    * Method which retrieve the post user's certificate and key
    *
    * @param parameters The parameters to use
    */  
   protected void startRetrieveKeystore() throws Exception {
     Parameters parameters = environment.getParameters();
     String file = parameters.getString("email_ssl_keystore_filename");
     
     if ((! new File(file).exists()) &&
         (parameters.getBoolean("email_imap_ssl") ||
          parameters.getBoolean("email_pop3_ssl") ||
          parameters.getBoolean("email_smtp_ssl"))) {
       stepStart("Creating keypair for SSL servers");
       
       String pass = parameters.getString("email_ssl_keystore_password");
       int validity = parameters.getInt("email_ssl_keystore_validity");
       
       StringBuffer command = new StringBuffer();
       command.append(System.getProperty("java.home"));
       command.append(System.getProperty("file.separator"));
       command.append("bin");
       command.append(System.getProperty("file.separator"));
       command.append("keytool -genkey -keystore ");
       command.append(file);
       command.append(" -alias ePOST -validity ");
       command.append("" + validity);
       command.append(" -keypass ");
       command.append(pass);
       command.append(" -storepass "); 
       command.append(pass);
       
       Process process = Runtime.getRuntime().exec(command.toString());
       BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()));
       BufferedWriter w = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
       
       w.write("ePOST User at " + getLocalHost() + "\n");
       w.write("Unknown\n");
       w.write("Unknown\n");
       w.write("Unknown\n");
       w.write("Unknown\n");
       w.write("Unknown\n");
       w.write("yes\n");
       w.flush();

       process.waitFor();
       
       if (process.exitValue() != 0)
         stepDone(FAILURE);
       else
         stepDone(SUCCESS);
     }
   }
     
  /**
   * Method which fetches the local email service
   *
   * @param parameters The parameters to use
   */  
  protected void startEmailService() throws Exception {
   Parameters parameters = environment.getParameters();
   stepStart("Starting Email service");
   post.setNotificationMessageDeserializer(new NotificationMessageDeserializer() {
  
    public NotificationMessage deserializeNotificationMessage(InputBuffer buf,
        Endpoint endpoint, short contentType) throws IOException {
      switch(contentType) {
        case EmailNotificationMessage.TYPE:
          return new EmailNotificationMessage(buf, endpoint);
      }
      throw new IllegalArgumentException("Unknown type:"+contentType);
    }
  
  });
   email = new EmailService(getLocalHost(), post, pair, parameters.getBoolean("post_allow_log_insert"));
   PostMessage.factory = FACTORY;
   stepDone(SUCCESS);
  }
     
  /**
   * Method which fetch the local user's email log and inbox
   *
   * @param parameters The parameters to use
   */
  protected void startFetchEmailLog() throws Exception {    
    Parameters parameters = environment.getParameters();
    if (parameters.getBoolean("email_fetch_log")) {
      stepStart("Fetching Email INBOX log");
      int retries = 0;
      boolean done = false;
      
      while (!done) {
        try {
          emailFolder = (rice.email.Folder)(new ExternalContinuationRunnable() {
            protected void execute(Continuation c) {
              email.getRootFolder(c);
            }
          }).invoke(environment);
          done = true;
        } catch (Exception e) {
          stepDone(FAILURE, "Fetching email log caused exception " + e);
          stepStart("Sleeping and then retrying to fetch email log (" + retries + "/" + parameters.getInt("email_fetch_log_retries"));
          if (retries < parameters.getInt("email_fetch_log_retries")) {
            retries++;
            Thread.sleep(parameters.getInt("email_fetch_log_retry_sleep"));
          } else {
            throw e; 
          }
        }
      }
      stepDone(SUCCESS);
    }
  }
     
  /**
   * Method which starts the local user manager
   *
   * @param parameters The parameters to use
   */
  protected void startUserManager() throws Exception {    
    Parameters parameters = environment.getParameters();
    stepStart("Starting User Email Services");
    
    if (parameters.getBoolean("email_fetch_log"))
      manager = new UserManagerImpl(email, new PostMailboxManager(email, emailFolder, environment));
    else 
      manager = new UserManagerImpl(email, new PostMailboxManager(email, null, environment));
    
    String addr = address.toString();
    // note this means you can't have a a "" password
    if (pass == null || "".equals(pass)) {
      stepDone(FAILURE, "ERROR: Unable to determine IMAP password (no Post password found)");
      if (logger.level <= Logger.SEVERE) logger.log(
          "ERROR: Unable to determine IMAP password (no Post password found)");
      
      int i = message("Could not find a password for your account.\nYou will not be able to log into IMAP, SMTP, or other services.", 
                      new String[] {"Continue", "Kill ePOST Proxy"}, "Continue");
      
      if (i == 1)
        System.exit(-1);
    } else {
      manager.createUser(addr.substring(0, addr.indexOf("@")), null, pass);
      stepDone(SUCCESS);
    }
  }

  /**
   * Method which starts the local SMTP server
   *
   * @param parameters The parameters to use
   */
  protected void startSMTPServer() throws Exception {    
    Parameters parameters = environment.getParameters();
    if (parameters.getBoolean("email_smtp_enable")) {
      try {
        int port = parameters.getInt("email_smtp_port");
        boolean gateway = parameters.getBoolean("email_gateway");
        boolean accept = parameters.getBoolean("email_accept_nonlocal");
        boolean authenticate = parameters.getBoolean("email_smtp_authenticate");
        String file = parameters.getString("email_ssl_keystore_filename");
        String pass = parameters.getString("email_ssl_keystore_password");
        String sendersA = parameters.getString("email_allowed_senders");
        String server = smtpServer;
        
        String[] senders = new String[0];
        
        if ((sendersA != null) && (sendersA.length() > 0))
          senders = sendersA.split(",");
        
        if (parameters.getBoolean("email_smtp_ssl")) {
          stepStart("Starting SSL SMTP server on port " + port);
          smtp = new SSLSmtpServerImpl(getLocalHost(), port, email, gateway, address, accept, authenticate, manager, file, pass, server, environment);
          smtp.start();
          stepDone(SUCCESS);
        } else if (parameters.getBoolean("email_smtp_non_blocking")) {
          stepStart("Starting Non-Blocking SMTP server on port " + port);
          smtp = new NonBlockingSmtpServerImpl(getLocalHost(), port, email, gateway, address, accept, authenticate, manager, server, environment);
          smtp.start();
          stepDone(SUCCESS);
        } else {
          stepStart("Starting SMTP server on port " + port);
          smtp = new SmtpServerImpl(getLocalHost(), port, email, gateway, address, accept, authenticate, manager, server, environment);
          smtp.start();
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        stepDone(FAILURE, "ERROR: Unable to launch SMTP server - continuing - " + e);
        if (logger.level <= Logger.SEVERE) logger.logException(
            "ERROR: Unable to launch SMTP server - continuing - ",e);
        
        int i = message("The SMTP server failed to launch due to an exception.\n\n" + 
                        e + " - " + e.getMessage(), new String[] {"Continue", "Kill ePOST Proxy"}, "Continue");
        
        if (i == 1)
          System.exit(-1);
      }
    }
  }

  /**
   * Method which starts the local IMAP server
   *
   * @param parameters The parameters to use
   */
  protected void startIMAPServer() throws Exception {    
    Parameters parameters = environment.getParameters();
    if (parameters.getBoolean("email_imap_enable")) {
      try {
        int port = parameters.getInt("email_imap_port");
        boolean gateway = parameters.getBoolean("email_gateway");
        boolean accept = parameters.getBoolean("email_accept_nonlocal");
        String file = parameters.getString("email_ssl_keystore_filename");
        String pass = parameters.getString("email_ssl_keystore_password");
        
        if (parameters.getBoolean("email_imap_ssl")) {
          stepStart("Starting SSL IMAP server on port " + port);
          imap = new SSLImapServerImpl(getLocalHost(), port, email, manager, gateway, accept, file, pass, environment);
          imap.start();
          stepDone(SUCCESS);
        } else if (parameters.getBoolean("email_imap_non_blocking")) {
          stepStart("Starting Non-Blocking IMAP server on port " + port);
          imap = new NonBlockingImapServerImpl(getLocalHost(), port, email, manager, gateway, accept, environment);
          imap.start();
          stepDone(SUCCESS);
        } else {
          stepStart("Starting IMAP server on port " + port);
          imap = new ImapServerImpl(getLocalHost(), port, email, manager, gateway, accept, environment);
          imap.start();
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        stepDone(FAILURE, "ERROR: Unable to launch IMAP server - continuing - " + e);
        if (logger.level <= Logger.SEVERE) logger.logException(
            "ERROR: Unable to launch IMAP server - continuing - ",e);
        
        int i = message("The IMAP server failed to launch due to an exception.\n\n" + 
                        e + " - " + e.getMessage(), new String[] {"Continue", "Kill ePOST Proxy"}, "Continue");
        
        if (i == 1)
          System.exit(-1);
      }
    }
  }

  /**
   * Method which starts the local POP3 server
   *
   * @param parameters The parameters to use
   */
  protected void startPOP3Server() throws Exception {    
    Parameters parameters = environment.getParameters();
    if (parameters.getBoolean("email_pop3_enable")) {
      try {
        int port = parameters.getInt("email_pop3_port");
        boolean gateway = parameters.getBoolean("email_gateway");
        boolean accept = parameters.getBoolean("email_accept_nonlocal");
        String file = parameters.getString("email_ssl_keystore_filename");
        String pass = parameters.getString("email_ssl_keystore_password");
        
        if (parameters.getBoolean("email_pop3_ssl")) {
          stepStart("Starting SSL POP3 server on port " + port);
          pop3 = new SSLPop3ServerImpl(getLocalHost(), port, email, manager, gateway, accept, file, pass, environment);
          pop3.start();
          stepDone(SUCCESS);
        } else if (parameters.getBoolean("email_pop3_non_blocking")) {
          stepStart("Starting Non-Blocking POP3 server on port " + port);
          pop3 = new NonBlockingPop3ServerImpl(getLocalHost(), port, email, manager, gateway, accept, environment);
          pop3.start();
          stepDone(SUCCESS);
        } else {
          stepStart("Starting POP3 server on port " + port);
          pop3 = new Pop3ServerImpl(getLocalHost(), port, email, manager, gateway, accept, environment);
          pop3.start();
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        stepDone(FAILURE, "ERROR: Unable to launch IMAP server - continuing - " + e);
        if (logger.level <= Logger.SEVERE) logger.logException(
            "ERROR: Unable to launch IMAP server - continuing - ",
            e);
        
        int i = message("The POP3 server failed to launch due to an exception.\n\n" + 
                        e + " - " + e.getMessage(), new String[] {"Continue", "Kill ePOST Proxy"}, "Continue");
        
        if (i == 1)
          System.exit(-1);
      }
    }
  }

  /**
   * Method which starts the local WebMail server
   *
   * @param parameters The parameters to use
   */
  protected void startWebMailServer() throws Exception { 
    Parameters parameters = environment.getParameters();
    if (parameters.getBoolean("email_webmail_enable")) {
      int port = parameters.getInt("email_webmail_port");
      stepStart("Starting WebMail server on port " + port);
      web = new WebServerImpl(port, email, manager, environment);
      web.start();
      stepDone(SUCCESS);
    }
  }

  public void start2() throws Exception {
    super.start2();

    if (System.getProperty("RECOVER") != null)
      return;

    sectionStart("Starting Email services");
    startMailcap(parameters);
       
    if (parameters.getBoolean("post_proxy_enable")) {
      startRetrieveKeystore();
      startEmailService();
      startFetchEmailLog();
      startUserManager();
      startSMTPServer();
      startIMAPServer();
      startPOP3Server();
      startWebMailServer();
    }

    sectionDone();
  }    


  /**
   * Usage:
   * java rice.email.proxy.EmailProxy userid [-bootstrap hostname[:port]] [-port port] [-imapport port] [-smtpport port]
   */
  public static void main(String[] args) {
    EmailProxy proxy = new EmailProxy();
    proxy.start();
  }
}
