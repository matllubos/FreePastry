package rice.email.proxy;

import rice.p2p.commonapi.IdFactory;

import rice.pastry.client.*;
import rice.pastry.commonapi.*;
import rice.pastry.leafset.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;

import rice.p2p.past.*;

import rice.persistence.*;

import rice.*;
import rice.Continuation.*;

import rice.post.*;
import rice.post.proxy.*;
import rice.post.security.*;
import rice.post.security.ca.*;
 
import rice.email.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.web.*;
import rice.email.proxy.pop3.*;
import rice.email.proxy.imap.*;
import rice.email.proxy.user.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;

import rice.proxy.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;

import javax.activation.*;
import javax.mail.*;

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
   protected void startRetrieveKeystore(Parameters parameters) throws Exception {
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
       
       w.write("ePOST User at " + InetAddress.getLocalHost() + "\n");
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
  protected void startEmailService(Parameters parameters) throws Exception {
   stepStart("Starting Email service");
   email = new EmailService(post, pair, parameters.getBoolean("post_allow_log_insert"));
   PostMessage.factory = FACTORY;
   stepDone(SUCCESS);
  }
     
  /**
   * Method which fetch the local user's email log and inbox
   *
   * @param parameters The parameters to use
   */
  protected void startFetchEmailLog(Parameters parameters) throws Exception {    
    if (parameters.getBoolean("email_fetch_log")) {
      stepStart("Fetching Email INBOX log");
      int retries = 0;
      boolean done = false;
      
      while (!done) {
        ExternalContinuation c = new ExternalContinuation();
        email.getRootFolder(c);
        c.sleep();
        
        if (c.exceptionThrown()) { 
          stepDone(FAILURE, "Fetching email log caused exception " + c.getException());
          stepStart("Sleeping and then retrying to fetch email log (" + retries + "/" + parameters.getInt("email_fetch_log_retries"));
          if (retries < parameters.getInt("email_fetch_log_retries")) {
            retries++;
            Thread.sleep(parameters.getInt("email_fetch_log_retry_sleep"));
          } else {
            throw c.getException(); 
          }
        } else {
          emailFolder = (rice.email.Folder) c.getResult();
          done = true;
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
  protected void startUserManager(Environment env) throws Exception {    
    Parameters parameters = env.getParameters();
    stepStart("Starting User Email Services");
    
    if (parameters.getBoolean("email_fetch_log"))
      manager = new UserManagerImpl(email, new PostMailboxManager(email, emailFolder, env));
    else 
      manager = new UserManagerImpl(email, new PostMailboxManager(email, null, env));
    
    String addr = address.toString();
    manager.createUser(addr.substring(0, addr.indexOf("@")), null, pass);
    stepDone(SUCCESS);
  }

  /**
   * Method which starts the local SMTP server
   *
   * @param parameters The parameters to use
   */
  protected void startSMTPServer(Environment env) throws Exception {    
    Parameters parameters = env.getParameters();
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
          smtp = new SSLSmtpServerImpl(port, email, gateway, address, accept, authenticate, manager, file, pass, server, env);
          smtp.start();
          stepDone(SUCCESS);
        } else if (parameters.getBoolean("email_smtp_non_blocking")) {
          stepStart("Starting Non-Blocking SMTP server on port " + port);
          smtp = new NonBlockingSmtpServerImpl(port, email, gateway, address, accept, authenticate, manager, server, env);
          smtp.start();
          stepDone(SUCCESS);
        } else {
          stepStart("Starting SMTP server on port " + port);
          smtp = new SmtpServerImpl(port, email, gateway, address, accept, authenticate, manager, server, env);
          smtp.start();
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        stepDone(FAILURE, "ERROR: Unable to launch SMTP server - continuing - " + e);
        env.getLogManager().getLogger(EmailProxy.class, null).logException(Logger.SEVERE,
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
  protected void startIMAPServer(Environment env) throws Exception {    
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("email_imap_enable")) {
      try {
        boolean log = parameters.getBoolean("email_imap_log");
        int port = parameters.getInt("email_imap_port");
        boolean gateway = parameters.getBoolean("email_gateway");
        boolean accept = parameters.getBoolean("email_accept_nonlocal");
        String file = parameters.getString("email_ssl_keystore_filename");
        String pass = parameters.getString("email_ssl_keystore_password");
        
        if (parameters.getBoolean("email_imap_ssl")) {
          stepStart("Starting SSL IMAP server on port " + port);
          imap = new SSLImapServerImpl(port, email, manager, gateway, accept, file, pass, log, env);
          imap.start();
          stepDone(SUCCESS);
        } else if (parameters.getBoolean("email_imap_non_blocking")) {
          stepStart("Starting Non-Blocking IMAP server on port " + port);
          imap = new NonBlockingImapServerImpl(port, email, manager, gateway, accept, log, env);
          imap.start();
          stepDone(SUCCESS);
        } else {
          stepStart("Starting IMAP server on port " + port);
          imap = new ImapServerImpl(port, email, manager, gateway, accept, env);
          imap.start();
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        stepDone(FAILURE, "ERROR: Unable to launch IMAP server - continuing - " + e);
        env.getLogManager().getLogger(EmailProxy.class, null).logException(Logger.SEVERE,
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
  protected void startPOP3Server(Environment env) throws Exception {    
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("email_pop3_enable")) {
      try {
        int port = parameters.getInt("email_pop3_port");
        boolean gateway = parameters.getBoolean("email_gateway");
        boolean accept = parameters.getBoolean("email_accept_nonlocal");
        String file = parameters.getString("email_ssl_keystore_filename");
        String pass = parameters.getString("email_ssl_keystore_password");
        
        if (parameters.getBoolean("email_pop3_ssl")) {
          stepStart("Starting SSL POP3 server on port " + port);
          pop3 = new SSLPop3ServerImpl(port, email, manager, gateway, accept, file, pass, env);
          pop3.start();
          stepDone(SUCCESS);
        } else if (parameters.getBoolean("email_pop3_non_blocking")) {
          stepStart("Starting Non-Blocking POP3 server on port " + port);
          pop3 = new NonBlockingPop3ServerImpl(port, email, manager, gateway, accept, env);
          pop3.start();
          stepDone(SUCCESS);
        } else {
          stepStart("Starting POP3 server on port " + port);
          pop3 = new Pop3ServerImpl(port, email, manager, gateway, accept, env);
          pop3.start();
          stepDone(SUCCESS);
        }
      } catch (Exception e) {
        stepDone(FAILURE, "ERROR: Unable to launch IMAP server - continuing - " + e);
        env.getLogManager().getLogger(EmailProxy.class, null).logException(Logger.SEVERE,
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
  protected void startWebMailServer(Environment env) throws Exception { 
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("email_webmail_enable")) {
      int port = parameters.getInt("email_webmail_port");
      stepStart("Starting WebMail server on port " + port);
      web = new WebServerImpl(port, email, manager, env);
      web.start();
      stepDone(SUCCESS);
    }
  }

  public Environment start(Environment env) throws Exception {
    super.start(env);

    if (System.getProperty("RECOVER") != null)
      return env;

    sectionStart("Starting Email services");
    startMailcap(parameters);
       
    if (parameters.getBoolean("post_proxy_enable")) {
      startRetrieveKeystore(parameters);
      startEmailService(parameters);
      startFetchEmailLog(parameters);
      startUserManager(env);
      startSMTPServer(env);
      startIMAPServer(env);
      startPOP3Server(env);
      startWebMailServer(env);
    }

    sectionDone();
    
    return env;
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
