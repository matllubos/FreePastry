package rice.post.proxy;

import rice.*;

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
 * boots up the PAST, Scribe, POST.
 */
public abstract class PostProxy {

  // ----- PASTRY CONFIGURATION VARIABLES

  /**
   * The default port to start the Pastry node on.
   */
  static int PORT = 10001;

  /**
   * The default host to boot off of.
   */
  static String BOOTSTRAP_HOST = "localhost";

  /**
   * The default port on the remote host to boot off of
   */
  static int BOOTSTRAP_PORT = PORT;

  /**
   * The default size of the cache to use (in bytes)
   */
  static int CACHE_SIZE = 100000;

  /**
   * The default size of the disk storage to use (in bytes)
   */
  static int DISK_SIZE = 10000000;

  /**
   * The IdFactory to use (for protocol independence)
   */
  static IdFactory FACTORY = new PastryIdFactory();

  /**
   * The default instance name to use for the system
   */
  static String INSTANCE_NAME = "PostProxy";

  /**
   * The default replication factor for PAST
   */
  static int REPLICATION_FACTOR = 3;

  
  // ----- DISPLAY FIELDS -----

  protected static final String SUCCESS = "SUCCESS";
  protected static final String FAILURE = "FAILURE";
  protected static final int PAD_SIZE = 60;


  // ----- VARIABLE FIELDS -----

  /**
   * The credentials to use for Pastry
   */
  protected Credentials _credentials = new PermissiveCredentials();

  /**
   * The local pastry node
   */
  protected PastryNode pastry;

  /**
   * The local Scribe service
   */
  protected Scribe scribe;

  /**
   * The local Past service
   */
  protected Past past;

  /**
   * The local Post service
   */
  protected Post post;

  /**
   * The name of the local user
   */
  protected String name;

  /**
    * The password of the local user
   */
  protected String pass;

  /**
   * The address of the local user
   */
  protected PostUserAddress address;

  /**
   * The certificate of the local user
   */
  protected PostCertificate certificate;

  /**
   * The keypair of the local user
   */
  protected KeyPair pair;

  /**
   * The well-known public key of the CA
   */
  protected PublicKey caPublic;

  public PostProxy(String userid) {
    name = userid;
  }

  protected void start() {
    try {
      sectionStart("Creating and Initializing Services");
      stepStart("Creating Pastry node");
      WirePastryNodeFactory factory = new WirePastryNodeFactory(new IPNodeIdFactory(PORT), PORT);
      InetSocketAddress bootAddress = new InetSocketAddress(BOOTSTRAP_HOST, BOOTSTRAP_PORT);

      pastry = factory.newNode(factory.getNodeHandle(bootAddress));
      stepDone(SUCCESS);

      stepStart("Retrieving CA public key");
      FileInputStream fis = new FileInputStream("ca.publickey");
      ObjectInputStream ois = new ObjectInputStream(fis);

      caPublic = (PublicKey) ois.readObject();
      ois.close();
      stepDone(SUCCESS);

      stepStart("Retrieving " + name + "'s certificate");
      fis = new FileInputStream(name + ".certificate");
      ois = new ObjectInputStream(fis);

      certificate = (PostCertificate) ois.readObject();
      ois.close();
      stepDone(SUCCESS);

      stepStart("Verifying " + name + "'s certificate");
      CASecurityModule module = new CASecurityModule(caPublic);

      final Object[] result = new Object[1];
      final Exception[] exception = new Exception[1];

      final Object wait = "wait";

      Continuation cont = new Continuation() {
        public void receiveResult(Object o) {
          synchronized (wait) {
            result[0] = o;
            wait.notifyAll();
          }
        }

        public void receiveException(Exception e) {
          synchronized (wait) {
            exception[0] = e;
            wait.notifyAll();
          }
        }
      };

      module.verify(certificate, cont);

      synchronized (wait) { if ((result[0] == null) && (exception[0] == null)) wait.wait(); }

      if (exception[0] != null)
        throw exception[0];
        
      
      if (! ((Boolean) result[0]).booleanValue()) {
        System.out.println("Certificate could not be verified.");
        System.exit(0);
      }
      stepDone(SUCCESS);

      address = (PostUserAddress) certificate.getAddress();

      fis = new FileInputStream(name + ".keypair.enc");
      ois = new ObjectInputStream(fis);

      stepStart("Reading in encrypted keypair");
      byte[] cipher = (byte[]) ois.readObject();
      ois.close();
      stepDone(SUCCESS);

      pass = CAKeyGenerator.fetchPassword(name + "'s password");
      byte[] key = null;
      byte[] data = null;

      try {
        stepStart("Decrypting " + name + "'s keypair");
        key = SecurityUtils.hash(pass.getBytes());
        data = SecurityUtils.decryptSymmetric(cipher, key);
      } catch (SecurityException e) {
        stepDone(FAILURE, "Incorrect password.  Please try again.");

        pass = CAKeyGenerator.fetchPassword(name + "'s password");

        try {
          System.out.print("    Decrypting " + name + "'s keypair");
          key = SecurityUtils.hash(pass.getBytes());
          data = SecurityUtils.decryptSymmetric(cipher, key);
        } catch (SecurityException se) {
          stepDone(FAILURE, "Incorrect password.  Exiting.");
          System.exit(-1);
        }
      }

      pair = (KeyPair) SecurityUtils.deserialize(data);
      stepDone(SUCCESS);

      stepStart("Verifying " + name + "'s keypair");
      if (! pair.getPublic().equals(certificate.getKey())) {
        System.out.println("KeyPair could not be verified.");
        System.exit(0);
      }
      stepDone(SUCCESS);

      stepStart("Starting StorageManager");
      StorageManager storage = new StorageManager(FACTORY,
                                                  new PersistentStorage(FACTORY, pastry.getNodeId().toString(), ".", DISK_SIZE),
                                                  new LRUCache(new MemoryStorage(FACTORY), CACHE_SIZE));
      stepDone(SUCCESS);

      stepStart("Starting SCRIBE service");
      scribe = new Scribe(pastry, _credentials);
      stepDone(SUCCESS);

      stepStart("Starting PAST service");
      past = new PastImpl(pastry, storage, REPLICATION_FACTOR, INSTANCE_NAME);
      stepDone(SUCCESS);

      stepStart("Starting POST service");
      post = new PostImpl(pastry, past, scribe, address, pair, certificate, caPublic, INSTANCE_NAME);
      stepDone(SUCCESS);

      sectionDone();

    } catch (Exception e) {
      System.out.println("Exception occured during construction " + e + " " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * This method parses the arguments passed to the proxy, sets up the necessary
   * local variables, and returns the userId.
   *
   * @param args The arguments from the main() method
   * @return The name of the user
   */
  protected static String parseArgs(String[] args) {
    if (args.length < 1) {
      System.out.println("Usage: java rice.post.proxy.PostProxy userid [-bootstrap hostname[:port]] [-port port] [-imapport port] [-smtpport port] [-help]");
      System.exit(0);
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String str = args[i+1];
        int index = str.indexOf(':');
        if (index == -1) {
          BOOTSTRAP_HOST = str;
          BOOTSTRAP_PORT = PORT;
        } else {
          BOOTSTRAP_HOST = str.substring(0, index);
          int tmpport = Integer.parseInt(str.substring(index + 1));
          if (tmpport > 0) {
            BOOTSTRAP_PORT = tmpport;
            PORT = tmpport;
          }
        }

        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) PORT = n;
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out.println("Usage: java rice.post.proxy.EmailProxy userid [-bootstrap hostname[:port]] [-port port] [-imapport port] [-smtpport port] [-help]");
        System.exit(0);
      }
    }

    return args[0];
  }

  protected void sectionStart(String name) {
    System.out.println(name);
  }

  protected void sectionDone() {
    System.out.println();
  }

  protected void stepStart(String name) {
    System.out.print(pad("  " + name));
  }

  protected void stepDone(String status) {
    System.out.println("[" + status + "]");
  }

  protected void stepDone(String status, String message) {
    System.out.println("[" + status + "]");
    System.out.println("    " + message);
  }

  protected void stepException(Exception e) {
    System.out.println();

    System.out.println("Exception " + e + " occurred during testing.");

    e.printStackTrace();
    System.exit(0);
  }

  private String pad(String start) {
    if (start.length() >= PAD_SIZE) {
      return start.substring(0, PAD_SIZE);
    } else {
      int spaceLength = PAD_SIZE - start.length();
      char[] spaces = new char[spaceLength];
      Arrays.fill(spaces, '.');

      return start.concat(new String(spaces));
    }
  }
}
