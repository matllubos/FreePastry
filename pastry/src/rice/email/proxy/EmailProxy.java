package rice.email.proxy;

import rice.pastry.client.*;
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

import rice.past.*;

import rice.persistence.*;

import rice.post.*;
import rice.post.security.*;

import rice.email.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.imap.*;

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
public class EmailProxy {

  static final int DEFAULT_PORT = 10001;
  static final int DEFAULT_IMAP_PORT = 1143;
  static final int DEFAULT_SMTP_PORT = 1025;
  static final String DEFAULT_BOOTSTRAP_HOST = "localhost";
  static final int DEFAULT_BOOTSTRAP_PORT = 10001;

  static final int DEFAULT_CACHE_SIZE = 100000;
  static final int DEFAULT_DISK_SIZE = 10000000;
  
  private Credentials _credentials = new PermissiveCredentials();

  private WirePastryNode pastry;

  private Scribe scribe;

  private PASTService past;

  private Post post;

  private EmailService email;

  private PostUserAddress address;

  private PostCertificate certificate;

  private KeyPair pair;

  private KeyPair caPair;

  private SmtpServerImpl smtp;

  private ImapServerImpl imap;

  public EmailProxy (String bootstrapHost, int bootstrapPort, int port, int imapport, int smtpport) {
    try {
      System.out.println("Email Proxy");
      System.out.println("-----------------------------------------------------------------------");
      System.out.println("  Creating and Initializing Services");
      System.out.print("    Creating Pastry node\t\t\t\t\t");
      WirePastryNodeFactory factory = new WirePastryNodeFactory(new RandomNodeIdFactory(), port);
      InetSocketAddress bootAddress = new InetSocketAddress(bootstrapHost, bootstrapPort);

      pastry = (WirePastryNode) factory.newNode(factory.getNodeHandle(bootAddress));
      System.out.println("[ DONE ]");

      System.out.print("    Retrieving CA key pair\t\t\t\t\t");
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");

      FileInputStream fis = new FileInputStream("capair.txt");
      ObjectInputStream ois = new ObjectInputStream(fis);

      caPair = (KeyPair) ois.readObject();

      System.out.println("[ DONE ]");

      System.out.print("    Generating user address\t\t\t\t\t");
      address = new PostUserAddress("amislove@" + InetAddress.getLocalHost().getHostAddress());
      System.out.println("[ DONE ]");

      System.out.print("    Generating user key pair\t\t\t\t\t");
      pair = kpg.generateKeyPair();
      System.out.println("[ DONE ]");

      System.out.print("    Generating user certificate\t\t\t\t\t");
      SecurityService security = new SecurityService(null, null);
      certificate = security.generateCertificate(address, pair.getPublic(), caPair.getPrivate());
      System.out.println("[ DONE ]");

      System.out.print("    Starting StorageManager\t\t\t\t\t");
      StorageManager storage = new StorageManager(new PersistentStorage(".", DEFAULT_DISK_SIZE),
                                                  new LRUCache(new MemoryStorage(), DEFAULT_CACHE_SIZE));
      System.out.println("[ DONE ]");

      System.out.print("    Starting SCRIBE service\t\t\t\t\t");
      scribe = new Scribe(pastry, _credentials);
      System.out.println("[ DONE ]");

      System.out.print("    Starting PAST service\t\t\t\t\t");
      past = new PASTServiceImpl(pastry, storage);
      System.out.println("[ DONE ]");

      System.out.print("    Press <ENTER> when you have the Pastry and PAST networks up.\n");
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
      input.readLine();

      System.out.print("    Starting POST service\t\t\t\t\t");
      post = new Post(pastry, past, scribe, address, pair, certificate, caPair.getPublic());
      System.out.println("[ DONE ]");
      
      Thread.sleep(5000);

      System.out.print("    Starting Email service\t\t\t\t\t");
      email = new EmailService(post);
      System.out.println("[ DONE ]");

      System.out.print("    Starting SMTP server on port " + smtpport + "\t\t\t\t");
      smtp = new SmtpServerImpl(smtpport, email);
      smtp.start();
      System.out.println("[ DONE ]");

      System.out.print("    Starting IMAP server on port " + imapport + "\t\t\t\t");
      imap = new ImapServerImpl(imapport, email);
      imap.start();
      System.out.println("[ DONE ]");

      while (true) {
        Thread.sleep(5000);
        post.announcePresence();
      }
    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Usage:
   * java rice.email.proxy.EmailProxy [-bootstrap hostname[:port]] [-port port] [-imapport port] [-smtpport port]
   */
  public static void main(String[] args) {
    int port = DEFAULT_PORT;
    int imapport = DEFAULT_IMAP_PORT;
    int smtpport = DEFAULT_SMTP_PORT;
    String bootstrapHost = DEFAULT_BOOTSTRAP_HOST;
    int bootstrapPort = DEFAULT_BOOTSTRAP_PORT;
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String str = args[i+1];
        int index = str.indexOf(':');
        if (index == -1) {
          bootstrapHost = str;
          bootstrapPort = port;
        } else {
          bootstrapHost = str.substring(0, index);
          int tmpport = Integer.parseInt(str.substring(index + 1));
          if (tmpport > 0) {
            bootstrapPort = tmpport;
            port = tmpport;
          }
        }

        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) port = n;
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-imapport") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) imapport = n;
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-smtpport") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) smtpport = n;
        break;
      }
    }
  
    EmailProxy proxy = new EmailProxy(bootstrapHost, bootstrapPort, port, imapport, smtpport);
  }
}