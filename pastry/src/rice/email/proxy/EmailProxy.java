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

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.p2p.past.*;

import rice.persistence.*;

import rice.post.*;
import rice.post.proxy.*;
import rice.post.security.*;
import rice.post.security.ca.*;
 
import rice.email.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.imap.*;
import rice.email.proxy.user.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mailbox.postbox.*;

import rice.testharness.*;
import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.*;

/**
* This class starts up everything on the Pastry side, and then
 * boots up the PAST, Scribe, POST, and Emails services, and then
 * starts the Foedus IMAP and SMTP servers.
 */
public class EmailProxy extends PostProxy {

  static int IMAP_PORT = 1143; 
  static int SMTP_PORT = 1025;

  static boolean GATEWAY = false;

  static boolean ACCEPT_NON_LOCAL = false;
 
  private EmailService email;

  private UserManagerImpl manager;

  private SmtpServerImpl smtp;

  private ImapServerImpl imap;

  public EmailProxy(String[] args) {
      super(args);
  }

  public void start() {
    super.start();
      
    try {
      sectionStart("Starting Email services");
      stepStart("Starting Email service");
      email = new EmailService(post, pair);
      manager = new UserManagerImpl(email, new PostMailboxManager(email));
      String addr = address.toString();
      manager.createUser(addr.substring(0, addr.indexOf("@")), null, pass);
      stepDone(SUCCESS);

      stepStart("Starting SMTP server on port " + SMTP_PORT);
      smtp = new SmtpServerImpl(SMTP_PORT, email, GATEWAY, address, ACCEPT_NON_LOCAL);
      smtp.start();
      stepDone(SUCCESS);

      stepStart("Starting IMAP server on port " + IMAP_PORT);
      imap = new ImapServerImpl(IMAP_PORT, email, manager, GATEWAY, ACCEPT_NON_LOCAL);
      imap.start();
      stepDone(SUCCESS);

      sectionDone();

      while (true) {
        Thread.sleep(5000);
        post.announcePresence();
      }
    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
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
      if (args[i].equals("-smtpport") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) SMTP_PORT = n;
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
  }    

  /**
   * Usage:
   * java rice.email.proxy.EmailProxy userid [-bootstrap hostname[:port]] [-port port] [-imapport port] [-smtpport port]
   */
  public static void main(String[] args) {
    EmailProxy proxy = new EmailProxy(args);
    proxy.start();
  }
}
