package rice.im.proxy;

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
import rice.post.security.*;

import rice.email.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.imap.*;
import rice.email.proxy.user.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mailbox.postbox.*;

import rice.im.*;
import rice.im.log.*;
import rice.im.messaging.*;
import rice.im.io.*;


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
public class IMProxy {

  static final int DEFAULT_PORT = 10001;
  static final int DEFAULT_IMAP_PORT = 1143;
  static final int DEFAULT_SMTP_PORT = 1025;
  static final String DEFAULT_BOOTSTRAP_HOST = "localhost";
  static final int DEFAULT_BOOTSTRAP_PORT = 10001;

  static final int DEFAULT_CACHE_SIZE = 100000;
  static final int DEFAULT_DISK_SIZE = 10000000;

  static final IdFactory FACTORY = new PastryIdFactory();

  static final String INSTANCE_NAME = "EmailProxy";

  static final int REPLICATION_FACTOR = 3;
  
  private Credentials _credentials = new PermissiveCredentials();

  private WirePastryNode pastry;

  private Scribe scribe;

  private Past past;

  private Post post;

  private EmailService email;

  private PostUserAddress address;

  private PostCertificate certificate;

  private KeyPair pair;

  private PublicKey caPublic;

  private UserManagerImpl manager;

  private SmtpServerImpl smtp;

  private ImapServerImpl imap;

  public IMProxy (String userid, String bootstrapHost, int bootstrapPort, int port, int imapport, int smtpport) {
    try {
      System.out.println("IM Proxy");
      System.out.println("-----------------------------------------------------------------------");
      System.out.println("  Creating and Initializing Services");
      System.out.print("    Creating Pastry node\t\t\t\t\t");
      WirePastryNodeFactory factory = new WirePastryNodeFactory(new RandomNodeIdFactory(), port);
      InetSocketAddress bootAddress = new InetSocketAddress(bootstrapHost, bootstrapPort);

      pastry = (WirePastryNode) factory.newNode(factory.getNodeHandle(bootAddress));
      System.out.println("[ DONE ]");

      System.out.print("    Retrieving CA public key\t\t\t\t\t");
      FileInputStream fis = new FileInputStream("ca.publickey");
      ObjectInputStream ois = new ObjectInputStream(fis);

      caPublic = (PublicKey) ois.readObject();
      ois.close();
      System.out.println("[ DONE ]");

      System.out.print("    Retrieving " + userid + "'s certificate\t\t\t\t");
      fis = new FileInputStream(userid + ".certificate");
      ois = new ObjectInputStream(fis);

      certificate = (PostCertificate) ois.readObject();
      ois.close();
      System.out.println("[ DONE ]");

      System.out.print("    Verifying " + userid + "'s certificate\t\t\t\t");
      SecurityService security = new SecurityService(null, null);
      if (! security.verifyCertificate(caPublic, certificate)) {
        System.out.println("Certificate could not be verified.");
        System.exit(0);
      }
      System.out.println("[ DONE ]");

      address = (PostUserAddress) certificate.getAddress();

      fis = new FileInputStream(userid + ".keypair.enc");
      ois = new ObjectInputStream(fis);

      System.out.print("    Reading in encrypted keypair\t\t\t\t");
      byte[] cipher = (byte[]) ois.readObject();
      ois.close();
      System.out.println("[ DONE ]");

      String pass = rice.post.security.CertificateAuthorityKeyGenerator.fetchPassword(userid + "'s password");

      System.out.print("    Decrypting " + userid + "'s keypair\t\t\t\t");
      byte[] key = security.hash(pass.getBytes());
      byte[] data = security.decryptDES(cipher, key);

      pair = (KeyPair) security.deserialize(data);
      System.out.println("[ DONE ]");

      System.out.print("    Verifying " + userid + "'s keypair\t\t\t\t");
      if (! pair.getPublic().equals(certificate.getKey())) {
        System.out.println("KeyPair could not be verified.");
        System.exit(0);
      }
      System.out.println("[ DONE ]");

      System.out.print("    Starting StorageManager\t\t\t\t\t");
      StorageManager storage = new StorageManager(FACTORY,
                                                  new PersistentStorage(FACTORY, ".", DEFAULT_DISK_SIZE),
                                                  new LRUCache(new MemoryStorage(FACTORY), DEFAULT_CACHE_SIZE));
      System.out.println("[ DONE ]");

      System.out.print("    Starting SCRIBE service\t\t\t\t\t");
      scribe = new Scribe(pastry, _credentials);
      System.out.println("[ DONE ]");

      System.out.print("    Starting PAST service\t\t\t\t\t");
      past = new PastImpl(pastry, storage, REPLICATION_FACTOR, INSTANCE_NAME);
      System.out.println("[ DONE ]");

      System.out.print("    Press <ENTER> when you have the Pastry and PAST networks up.\n");
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
      input.readLine();

      System.out.print("    Starting POST service\t\t\t\t\t");
      post = new PostImpl(pastry, past, scribe, address, pair, certificate, caPublic, INSTANCE_NAME);
      System.out.println("[ DONE ]");
      
      Thread.sleep(5000);

      
      IMService imservice = new IMService(post, post.getEntityAddress());
      Thread.sleep(1000);
      IMClient  imclient = new IMClient(imservice);
      imservice.register(imclient);
      
      System.out.println("Gui starting up...");
      IMGui _imGui = new IMGui(imclient);
      IMGui.setIMGui(_imGui);
      _imGui.run();


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
   
   
   */
  public static void main(String[] args) {
    int port = DEFAULT_PORT;
    int imapport = DEFAULT_IMAP_PORT;
    int smtpport = DEFAULT_SMTP_PORT;
    String bootstrapHost = DEFAULT_BOOTSTRAP_HOST;
    int bootstrapPort = DEFAULT_BOOTSTRAP_PORT;

    if (args.length < 1) {
      System.out.println("Usage: java rice.email.proxy.IMProxy userid [-bootstrap hostname[:port]] [-port port] [-imapport port] [-smtpport port] [-help]");
      System.exit(0);
    }

    String userid = args[0];
    
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
      if (args[i].equals("-help")) {
        System.out.println("Usage: java rice.email.proxy.EmailProxy userid [-bootstrap hostname[:port]] [-port port] [-imapport port] [-smtpport port] [-help]");
        System.exit(0);
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
  
    IMProxy proxy = new IMProxy(userid, bootstrapHost, bootstrapPort, port, imapport, smtpport);
  }


}

