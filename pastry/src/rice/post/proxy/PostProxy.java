package rice.post.proxy;

import rice.*;
import rice.Continuation.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.commonapi.*;
import rice.pastry.standard.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.multiring.*;

import rice.persistence.*;

import rice.post.*;
import rice.post.delivery.*;
import rice.post.security.*;
import rice.post.security.ca.*;

import rice.serialization.*;
import rice.proxy.*;

import rice.email.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.imap.*;
import rice.email.proxy.user.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mailbox.postbox.*;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.net.*;
import java.security.*;

/**
 * This class starts up everything on the Pastry side, and then
 * boots up the PAST, Scribe, POST.
 */
public class PostProxy {

  // ----- PASTRY CONFIGURATION VARIABLES

  /**
   * The default port to start the Pastry node on.
   */
  static int PORT = 10001;

  /**
   * The default host to boot off of.
   */
  static String[] BOOTSTRAP_HOSTS = new String[] {"sys01.cs.rice.edu", "sys02.cs.rice.edu", "sys03.cs.rice.edu",
    "sys04.cs.rice.edu", "sys05.cs.rice.edu", "sys06.cs.rice.edu", "sys07.cs.rice.edu", "sys08.cs.rice.edu", 
    "thor01.cs.rice.edu", "thor02.cs.rice.edu", "thor03.cs.rice.edu", "thor04.cs.rice.edu", "thor05.cs.rice.edu", 
    "thor06.cs.rice.edu", "thor07.cs.rice.edu", "thor08.cs.rice.edu", "thor09.cs.rice.edu", "thor10.cs.rice.edu", 
    "thor11.cs.rice.edu",  "thor12.cs.rice.edu", "thor13.cs.rice.edu", "thor14.cs.rice.edu", "thor15.cs.rice.edu", 
    "thor16.cs.rice.edu"};

  /**
   * The default port on the remote host to boot off of
   */
  static int[] BOOTSTRAP_PORTS = new int[] {PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT,
    PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT, PORT};
  
  /**
   * The username for the proxy
   */
  static String PROXY_USERNAME;
  
  /**
   * The password for the proxy
   */
  static String PROXY_PASSWORD;
  
  /**
   * The address which we are using as a proxy
   */
  static String PROXY_HOST;
  
  /**
   * The port we are using to proxy
   */
  static int PROXY_PORT;
  
  /**
   * The procotol to use when creating nodes
   */
  static int PROTOCOL = DistPastryNodeFactory.PROTOCOL_SOCKET;

  /**
   * The default size of the cache to use (in bytes)
   */
  static int CACHE_SIZE = 50000000;

  /**
   * The default size of the disk storage to use (in bytes)
   */
  static int DISK_SIZE = 2000000000;

  /**
   * The default instance name to use for the system
   */
  static String INSTANCE_NAME = "PostProxy";

  /**
   * The default replication factor for PAST
   */
  static int REPLICATION_FACTOR = 3;
  
  /**
   * The number of retries to fetch a log
   */
  public static int NUM_RETRIES = 3;
  
  /**
    * Whether to allow insert from POST log
   */
  public static boolean ALLOW_LOG_INSERT = false;
  
  /**
   * Whether or not to redirect output to 'nohup.out'
   */
  public static boolean REDIRECT_OUTPUT = true;

  
  // ----- DISPLAY FIELDS -----

  protected static final String SUCCESS = "SUCCESS";
  protected static final String FAILURE = "FAILURE";
  protected static final int PAD_SIZE = 60;


  // ----- VARIABLE FIELDS -----
  
  /**
   * The ring Id
   */
  protected rice.p2p.commonapi.Id ringId;
  
  /**
   * The IdFactory to use (for protocol independence)
   */
  protected IdFactory FACTORY;
  
  /**
   * The node the services should use
   */
  protected Node node;

  /**
   * The local Past service, for immutable objects
   */
  protected PastImpl immutablePast;
  
  /**
   * The local Past service, for mutable objects
   */
  protected PastImpl mutablePast;
  
  /**
    * The local Past service for delivery requests
   */
  protected DeliveryPastImpl pendingPast;
  
  /**
    * The local Past service
   */
  protected PastImpl deliveredPast;

  /**
   * The local Post service
   */
  protected Post post;
  
  /**
   * The local storage manager, for immutable objects
   */
  protected StorageManager immutableStorage;
  
  /**
   * The local storage manager, for mutable objects
   */
  protected StorageManager mutableStorage;
  
  /**
   * The local storage for pending deliveries
   */
  protected StorageManager pendingStorage;
  
  /**
   * The local storage for pending deliveries
   */
  protected StorageManager deliveredStorage;

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
  
  protected RemoteProxy remoteProxy;

  public PostProxy(String[] args) {
    parseArgs(args);
  }

  protected void start() throws Exception {
    if ((ringId == null) && (name == null)) {
      System.out.println("ERROR: Without a user, you must specify the ring via the -ring option");
      System.exit(-1);
    }
    
    if (PROXY_HOST != null) {
      sectionStart("Creating Remote Proxy");
      
      if (PROXY_USERNAME == null)
        PROXY_USERNAME = System.getProperty("user.name");
      
      if (PROXY_PASSWORD == null)
        PROXY_PASSWORD = CAKeyGenerator.fetchPassword(PROXY_USERNAME + "@" + PROXY_HOST + "'s SSH Password");
      
      stepStart("Launching Remote Proxy to " + PROXY_HOST);
      remoteProxy = new RemoteProxy(PROXY_HOST, PROXY_USERNAME, PROXY_PASSWORD, PORT, PROXY_PORT);
      remoteProxy.run();
      stepDone(SUCCESS);
      
      sectionDone();
    }
    
    sectionStart("Creating and Initializing Services");
    
    if (REDIRECT_OUTPUT) {
      stepStart("Redirecting Standard Output/Error");
      System.setOut(new PrintStream(new FileOutputStream("nohup.out", true)));
      System.setErr(System.out);
      stepDone(SUCCESS);
    }
    
    stepStart("Retrieving CA public key");
    InputStream fis = ClassLoader.getSystemResource("ca.publickey").openStream();
    ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
    
    caPublic = (PublicKey) ois.readObject();
    ois.close();
    stepDone(SUCCESS);
    
    if (name != null) {
      stepStart("Retrieving " + name + "'s certificate");
      fis = new FileInputStream(name + ".certificate");
      ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
      
      certificate = (PostCertificate) ois.readObject();
      
      if (ringId == null) 
        ringId = ((RingId) certificate.getAddress().getAddress()).getRingId();
      
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
      ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
      
      stepStart("Reading in encrypted keypair");
      byte[] cipher = (byte[]) ois.readObject();
      ois.close();
      stepDone(SUCCESS);
      
      if (pass == null) {
        pass = CAKeyGenerator.fetchPassword(name + "'s password");
      }
      
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
          stepStart("Decrypting " + name + "'s keypair");
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
    }
    
    stepStart("Creating Id Factory");
    FACTORY = new MultiringIdFactory(ringId, new PastryIdFactory());
    stepDone(SUCCESS);
    
    stepStart("Starting StorageManager");
    immutableStorage = new StorageManagerImpl(FACTORY,
                                             new PersistentStorage(FACTORY, InetAddress.getLocalHost().getHostName() + "-" + PORT , ".", DISK_SIZE),  // + "-immutable"
                                             new LRUCache(new PersistentStorage(FACTORY, InetAddress.getLocalHost().getHostName() + "-" + PORT + "-cache", ".", DISK_SIZE), CACHE_SIZE));
//    mutableStorage = new StorageManagerImpl(FACTORY,
//                                           new PersistentStorage(FACTORY, InetAddress.getLocalHost().getHostName() + "-" + PORT + "-mutable", ".", DISK_SIZE),
//                                           new LRUCache(new MemoryStorage(FACTORY), CACHE_SIZE));    
    pendingStorage = new StorageManagerImpl(FACTORY,
                                        new PersistentStorage(FACTORY, InetAddress.getLocalHost().getHostName() + "-" + PORT + "-pending", ".", DISK_SIZE),
                                        new LRUCache(new MemoryStorage(FACTORY), CACHE_SIZE));
    deliveredStorage = new StorageManagerImpl(FACTORY,
                                          new PersistentStorage(FACTORY, InetAddress.getLocalHost().getHostName() + "-" + PORT + "-delivered", ".", DISK_SIZE),
                                          new LRUCache(new MemoryStorage(FACTORY), CACHE_SIZE));
    stepDone(SUCCESS);
    
    stepStart("Creating Pastry node");
    DistPastryNodeFactory factory = DistPastryNodeFactory.getFactory(new CertifiedNodeIdFactory(PORT),
                                                                     PROTOCOL,
                                                                     PORT);
    InetSocketAddress[] bootAddresses = new InetSocketAddress[BOOTSTRAP_HOSTS.length];
    
    for (int i=0; i<BOOTSTRAP_HOSTS.length; i++) 
      bootAddresses[i] = new InetSocketAddress(BOOTSTRAP_HOSTS[i], BOOTSTRAP_PORTS[i]);
    
    InetSocketAddress proxyAddress = null;
    
    if (PROXY_HOST != null)
      proxyAddress = new InetSocketAddress(PROXY_HOST, PROXY_PORT);
    
    PastryNode pastry = factory.newNode(factory.getNodeHandle(bootAddresses), proxyAddress);
    Thread.sleep(3000);
    stepDone(SUCCESS);
    
    stepStart("Creating Multiring node in ring " + ringId);
    node = new MultiringNode(ringId, pastry);
    Thread.sleep(3000);
    stepDone(SUCCESS); 
    
    stepStart("Starting PAST service");
    immutablePast = new PastImpl(node,immutableStorage, REPLICATION_FACTOR, INSTANCE_NAME); // + "-immutable"
//    mutablePast = new PastImpl(node, mutableStorage, REPLICATION_FACTOR, INSTANCE_NAME + "-mutable"); //, new MutablePastPolicy());
    deliveredPast = new PastImpl(node, deliveredStorage, REPLICATION_FACTOR, INSTANCE_NAME + "-delivered");
    pendingPast = new DeliveryPastImpl(node, pendingStorage, REPLICATION_FACTOR, INSTANCE_NAME + "-pending", deliveredPast);
    stepDone(SUCCESS);
    
    stepStart("Starting POST service");
    post = new PostImpl(node, immutablePast, immutablePast, pendingPast, deliveredPast, address, pair, certificate, caPublic, INSTANCE_NAME, ALLOW_LOG_INSERT);
    stepDone(SUCCESS);
    
    if (name != null) {
      int retries = 0;
      
      stepStart("Fetching POST log at " + address.getAddress());
      boolean done = false;
      
      while (!done) {
        ExternalContinuation c = new ExternalContinuation();
        post.getPostLog(c);
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
    
    sectionDone();
  }

  /**
   * This method parses the arguments passed to the proxy, sets up the necessary
   * local variables, and returns the userId.
   *
   * @param args The arguments from the main() method
   * @return The name of the user
   */
  protected void parseArgs(String[] args) {
    String[] files = (new File(".")).list(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.endsWith(".certificate");
      }
    });
    
    if (files.length > 1) {
      System.out.println("ERROR: Expected at most one certificate, found " + files.length);
      System.exit(0);
    } else if (files.length == 0) {
      System.out.println("NOTE: Did not find certificate, running in passive mode...");
    } else {
      name = files[0].substring(0, files[0].indexOf("."));
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String[] addresses = args[i+1].split(";");
        String[] hosts = new String[addresses.length];
        int[] ports = new int[addresses.length];
        
        for (int j=0; j<addresses.length; j++) {
          String str = addresses[j];
          int index = str.indexOf(':');
          if (index == -1) {
            hosts[j] = str;
            ports[j] = PORT;
          } else {
            hosts[j] = str.substring(0, index);
            int tmpport = Integer.parseInt(str.substring(index + 1));
            if (tmpport > 0) 
              ports[j] = tmpport;
            else
              ports[j] = PORT;
          }
        }
        
        BOOTSTRAP_HOSTS = hosts;
        BOOTSTRAP_PORTS = ports;
        
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-proxy") && i+1 < args.length) {
        String str = args[i+1];
        
        if (str.indexOf('@') >= 0) {
          String creds = str.substring(0, str.indexOf('@'));
          str = str.substring(str.indexOf('@')+1);
          
          if (creds.indexOf(':') >= 0) {
            PROXY_PASSWORD = creds.substring(creds.indexOf(':')+1);
            creds = creds.substring(0, creds.indexOf(':'));
          } 
          
          PROXY_USERNAME = creds;
        }
        
        int index = str.indexOf(':');
        if (index == -1) {
          PROXY_HOST = str;
          PROXY_PORT = PORT;
        } else {
          PROXY_HOST = str.substring(0, index);
          int tmpport = Integer.parseInt(str.substring(index + 1));
          if (tmpport > 0) 
            PROXY_PORT = tmpport;
          else
            PROXY_PORT = PORT;
        }
                
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-password") && i+1 < args.length) {
        pass = args[i+1];
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-insert")) {
        ALLOW_LOG_INSERT = true;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-noredirect")) {
        REDIRECT_OUTPUT = false;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-ring") && i+1 < args.length) {
        IdFactory realFactory = new PastryIdFactory();
        rice.p2p.commonapi.Id ringId = realFactory.buildId(args[i+1]);
        byte[] ringData = ringId.toByteArray();
        
        for (int j=0; j<ringData.length - MultiringNodeCollection.BASE; j++) 
          ringData[j] = 0;
        
        this.ringId = realFactory.buildId(ringData);
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
        System.out.println("Usage: java rice.post.proxy.PostProxy userid [-password password] [-bootstrap hostname[:port]] [-port port] [-help]");
        System.exit(0);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i+1 < args.length) {
        String s = args[i+1];

        if (s.equalsIgnoreCase("wire"))
          PROTOCOL = DistPastryNodeFactory.PROTOCOL_WIRE;
        else if (s.equalsIgnoreCase("rmi"))
          PROTOCOL = DistPastryNodeFactory.PROTOCOL_RMI;
        else if (s.equalsIgnoreCase("socket"))
          PROTOCOL = DistPastryNodeFactory.PROTOCOL_SOCKET;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);

        break;
      }
    }    
  }

  public static void main(String[] args) {
    PostProxy proxy = new PostProxy(args);
    try {
      proxy.start();
    } catch (Exception e) {
      System.err.println("ERROR: Found Exception while start proxy - exiting - " + e);
      e.printStackTrace();
      System.exit(-1);
    }
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
