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
import rice.pastry.wire.*;
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
import rice.email.proxy.pop3.*;
import rice.email.proxy.imap.*;
import rice.email.proxy.user.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mailbox.postbox.*;

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

  static int IMAP_PORT = 1143; 
  static int POP3_PORT = 2110; 
  static int SMTP_PORT = 2025;
  static int WEBMAIL_PORT = 8082;

  static boolean SSL = false;
  
  static boolean GATEWAY = false;

  static boolean ACCEPT_NON_LOCAL = true;
  
  static boolean SEND_PUBLISH = true;

  // we must set up the Data Content Handler
  static {
    MailcapCommandMap cmdmap = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
    cmdmap.addMailcap("application/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
    //cmdmap.addMailcap("text/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
    cmdmap.addMailcap("image/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
    cmdmap.addMailcap("audio/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
    cmdmap.addMailcap("video/*; ;x-java-content-handler=rice.email.proxy.mail.MailDataHandler");
     
     CommandMap.setDefaultCommandMap(cmdmap);
  }
 
  protected EmailService email;

  protected UserManagerImpl manager;

  protected SmtpServerImpl smtp;

  protected Pop3ServerImpl pop3;
     
  protected ImapServerImpl imap;
     
//  protected WebServer web;

  public EmailProxy(String[] args) {
      super(args);
  }

     public void start() throws Exception {
       super.start();
       
       if (name != null) {
         sectionStart("Starting Email services");
         stepStart("Starting Email service");
         email = new EmailService(post, pair, ALLOW_LOG_INSERT);
         stepDone(SUCCESS);
         
         ExternalContinuation c = null;
         
         if (FETCH_LOGS) {
           stepStart("Fetching Email INBOX log");
           int retries = 0;
           boolean done = false;
         
           while (!done) {
             c = new ExternalContinuation();
             email.getRootFolder(c);
             c.sleep();
           
             if (c.exceptionThrown()) { 
               if (retries < NUM_RETRIES) {
                 retries++;
               } else {
                 throw c.getException(); 
               }
             } else {
               done = true;
             }
           }
           stepDone(SUCCESS);
         }
         
         stepStart("Starting User Email Services");
         
         if (FETCH_LOGS) 
           manager = new UserManagerImpl(email, new PostMailboxManager(email, (rice.email.Folder) c.getResult()));
         else 
           manager = new UserManagerImpl(email, new PostMailboxManager(email, null));
           
         String addr = address.toString();
         manager.createUser(addr.substring(0, addr.indexOf("@")), null, pass);
         stepDone(SUCCESS);
         
         try {
           if (! SSL) {
             stepStart("Starting SMTP server on port " + SMTP_PORT);
             smtp = new SmtpServerImpl(SMTP_PORT, email, GATEWAY, address, ACCEPT_NON_LOCAL);
             smtp.start();
             stepDone(SUCCESS);
           } else {
             stepStart("Starting SSL SMTP server on port " + SMTP_PORT);
             smtp = new SSLSmtpServerImpl(SMTP_PORT, email, GATEWAY, address, ACCEPT_NON_LOCAL);
             smtp.start();
             stepDone(SUCCESS);
           }
         } catch (Exception e) {
           System.err.println("ERROR: Unable to launch SMTP server - continuing - " + e);
         }
         
         try {
           if (! SSL) {
             stepStart("Starting POP3 server on port " + POP3_PORT);
             pop3 = new Pop3ServerImpl(POP3_PORT, email, manager, GATEWAY, ACCEPT_NON_LOCAL);
             pop3.start();
             stepDone(SUCCESS);
           } else {
             stepStart("Starting SSL POP3 server on port " + POP3_PORT);
             pop3 = new SSLPop3ServerImpl(POP3_PORT, email, manager, GATEWAY, ACCEPT_NON_LOCAL);
             pop3.start();
             stepDone(SUCCESS);
           }
         } catch (Exception e) {
           System.err.println("ERROR: Unable to launch POP3 server - continuing - " + e);
         }
         
         try {
           if (! SSL) {
             stepStart("Starting IMAP server on port " + IMAP_PORT);
             imap = new ImapServerImpl(IMAP_PORT, email, manager, GATEWAY, ACCEPT_NON_LOCAL);
             imap.start();
             stepDone(SUCCESS);
           } else {
             stepStart("Starting SSL IMAP server on port " + IMAP_PORT);
             imap = new SSLImapServerImpl(IMAP_PORT, email, manager, GATEWAY, ACCEPT_NON_LOCAL);
             imap.start();
             stepDone(SUCCESS);
           }
         } catch (Exception e) {
           System.err.println("ERROR: Unable to launch IMAP server - continuing - " + e);
         }
         
         //    stepStart("Starting WebMail server on port " + WEBMAIL_PORT);
         //    web = new WebServer();
         //    web.start();
         //    stepDone(SUCCESS);
         
         sectionDone();
         
         PostMessage.factory = FACTORY;
         
   /*      Thread t = new Thread("POST Presence Announcement Thread") {
           public void run() {
             try {
               while (true) {
                 Thread.sleep(60000);
                 post.announcePresence();
               }
             } catch (Throwable t) {
               System.out.println("Error: (PostAnnouncement.run): " + t);
               t.printStackTrace();
               System.exit(-1);
             }
           }
         };
         
         if (SEND_PUBLISH) {
           t.start();
         } */
       }
     }
     
  public void parseArgs(String[] args) {
    super.parseArgs(args);

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-imapport") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) IMAP_PORT = n;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-pop3port") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) POP3_PORT = n;
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-smtpport") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) SMTP_PORT = n;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-ssl")) {
        SSL = true;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-gateway")) {
        GATEWAY = true;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nonlocal")) {
        ACCEPT_NON_LOCAL = true;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nopublish")) {
        SEND_PUBLISH = false;
        break;
      }
    }
  }    

  /**
   * Usage:
   * java rice.email.proxy.EmailProxy userid [-bootstrap hostname[:port]] [-port port] [-imapport port] [-smtpport port]
   */
  public static void main(String[] args) {
    EmailProxy proxy = new EmailProxy(args);
    try {
      proxy.start();
    } catch (Exception e) {
      System.err.println("ERROR: Found Exception while start proxy - exiting - " + e);
      System.exit(-1);
    }
  }
}
