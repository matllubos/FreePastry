package rice.post.proxy;

import rice.*;
import rice.Continuation.*;

import rice.pastry.PastryNode;
import rice.pastry.dist.*;
import rice.pastry.socket.*;
import rice.pastry.commonapi.*;
import rice.pastry.standard.*;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v2.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.aggregation.*;
import rice.p2p.multiring.*;
import rice.p2p.util.*;

import rice.persistence.*;
import rice.visualization.LocalVisualization;

import rice.post.*;
import rice.post.delivery.*;
import rice.post.security.*;
import rice.post.security.ca.*;
import rice.post.storage.*;

import rice.selector.*;
import rice.proxy.*;

import rice.email.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.imap.*;
import rice.email.proxy.user.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.environment.Environment;
import rice.environment.logging.simple.SimpleLogManager;
import rice.environment.params.Parameters;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.net.*;
import java.security.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import java.nio.*;
import java.nio.channels.*;

/**
 * This class starts up everything on the Pastry side, and then
 * boots up the PAST, Scribe, POST.
 */
public class PostProxy {
    
  /**
   * The name of the parameters file for Post
   */
  public static String PROXY_PARAMETERS_NAME = "proxy";

  public static String[] DEFAULT_PARAMS_FILES = {"freepastry","epost"}; 
  
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
    * The IdFactory to use for glacier fragments
   */
  protected FragmentKeyFactory KFACTORY;
  
  /**
   * The Internet-visible IP address of this node, if a NAT is present
   */
  protected InetAddress natAddress;
  
  /**
   * The factory used to create the normal Pastry node
   */
  protected DistPastryNodeFactory factory;
  
  /**
    * The node the services should use
   */
  protected PastryNode pastryNode;
  
  /**
   * The ring certificate for the pastry node
   */
  protected RingCertificate cert;
  
  /**
    * The node running in the global ring (if one exists)
   */
  protected PastryNode globalPastryNode;
  
  /**
   * The ring certificate for the global node (if one exists)
   */
  protected RingCertificate globalCert;
  
  /**
   * The port number for the Pastry node
   */
  protected int port;
  
  /**
   * The port number for the global Pastry node
   */
  protected int globalPort;
  
  /**
   * The node the services should use
   */
  protected Node node;

  /**
   * The node running in the global ring (if one exists)
   */
  protected Node globalNode;
  
  /**
   * The local Past service, for immutable objects
   */
  protected Past immutablePast;
  
  /**
    * The local Past service, for immutable objects
   */
  protected Past realImmutablePast;
  
  /**
   * The local Past service, for mutable objects
   */
  protected Past mutablePast;
  
  /**
    * The local Past service for delivery requests
   */
  protected DeliveryPastImpl pendingPast;
  
  /**
    * The local Past service
   */
  protected PastImpl deliveredPast;

  /**
    * The local Glacier service
   */
  protected GlacierImpl immutableGlacier;

  /**
   * The local Post service
   */
  protected Post post;
  
  /**
   * The global timer used for scheduling events
   */
  protected rice.selector.Timer timer;
  
  /**
   * The local storage manager, for immutable objects
   */
  protected StorageManagerImpl immutableStorage;
  
  /**
   * The local storage manager, for mutable objects
   */
  protected StorageManagerImpl mutableStorage;
  
  /**
   * The local storage for pending deliveries
   */
  protected StorageManagerImpl pendingStorage;
  
  /**
   * The local storage for pending deliveries
   */
  protected StorageManagerImpl deliveredStorage;
  
  /**
   * The local trash can, if in use
   */
  protected StorageManagerImpl trashStorage;
  
  /**
   * The local storage for mutable glacier fragments
   */
  protected StorageManagerImpl glacierMutableStorage;

  /**
   * The local storage for immutable glacier fragments
   */
  protected StorageManagerImpl glacierImmutableStorage;

  /**
   * The local storage for glacier neighbor certificates
   */
  protected StorageManagerImpl glacierNeighborStorage;

  /**
   * The local storage for glacier's 'trash can'
   */
  protected StorageManagerImpl glacierTrashStorage;

  /**
   * The local storage for objects waiting to be aggregated
   */
  protected StorageManagerImpl aggrWaitingStorage;
  
  /**
    * The local backup cache, for immutable objects
   */
  protected Cache immutableBackupCache;
  
  /**
    * The local backup cache, for pending deliveries
   */
  protected Cache pendingBackupCache;
  
  /**
    * The local backup cache for pending deliveries
   */
  protected Cache deliveredBackupCache;
  
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
   * The previous address of the user, used to clone the old PostLog
   */
  public PostEntityAddress clone;

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
    
  /**
   * The dialog showing the post status to users
   */
  protected PostDialog dialog;
  
  /**
   * The class which manages the log
   */
  protected LogManager logManager;
  
  /**
   * The parameters we are running with
   */
  protected Parameters parameters;
  
  /**
   * The fetched POST log
   */
  protected PostLog log;
  
  /**
   * The fetched forward log
   */
  protected ForwardLog forwardLog;
  
  /**
   * The SMTP Server
   */
  protected String smtpServer;
  
  protected Environment environment;
  
  /**
   * Method which redirects standard output and error, if desired.
   *
   * @param parameters The parameters to use
   */  
  protected void startRedirection(Environment env) throws Exception {
    environment = env;
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("standard_output_network_enable")) {
      logManager = new NetworkLogManager(env);
    } else if (parameters.getBoolean("standard_output_redirect_enable")) {
      logManager = new StandardLogManager(parameters);
    } else {
      logManager = new ConsoleLogManager(parameters);
    }
    
    System.setOut(new PrintStream(logManager));
    System.setErr(new PrintStream(logManager));
    ((SimpleLogManager)env.getLogManager()).setPrintStream(System.out);
  }
  
  /**
    * Method which sees if we are using a liveness monitor, and if so, sets up this
   * VM to have a client liveness monitor.
   *
   * @param parameters The parameters to use
   */
  protected void startLivenessMonitor(Environment env) throws Exception {
    if (env.getParameters().getBoolean("proxy_liveness_monitor_enable")) {
      LivenessThread lt = new LivenessThread(env);
      lt.start();
    }
  }
    
  /**
   * Method which check all necessary boot conditions before starting the proxy.
   *
   * @param parameters The parameters to use
   */
  protected void startCheckBoot(Environment env) throws Exception {
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("proxy_compatibility_check_enable")) {
      String address = InetAddress.getLocalHost().getHostAddress();
      
      if (! CompatibilityCheck.testIPAddress(address)) {
        if (parameters.getBoolean("pastry_proxy_connectivity_show_message")) {
          int i = message("You computer appears to have the non-routable address " + address + ".\n" +
                          "This is likely because you are connected from behind a NAT - ePOST can\n" + 
                          "run from behind a NAT, but you must set up port forwarding on port 10001\n" +
                          "for both TCP and UDP to your internal address '" + address + "'.\n\n" +
                          "If you have set up your NAT box, select 'NAT is Set Up' to test your\n" + 
                          "connection, otherwise, select 'Kill ePOST Proxy'.", 
                          new String[] {"Kill ePOST Proxy", "NAT is Set Up"}, "Kill ePOST Proxy");
          
          if (i == 0) {
            System.exit(-1);
          } else {
            startCheckNAT(env);
          }
        } else {
          startCheckNAT(env);
        }
      } 
          
      
      String version = System.getProperty("java.version");
      
      if (! CompatibilityCheck.testJavaVersion(version))
        panic("You appear to be running an incompatible version of Java '" + System.getProperty("java.version") + "'.\n" +
              "Currently, only Java 1.4.2 or higher is supported Please see http://java.sun.com in order\n" +
              "to download a compatible version.");
      
      String os = System.getProperty("os.name");
      
      if (! CompatibilityCheck.testOS(os)) {
        String message = "You appear to be running an untested operating system '" + System.getProperty("os.name") + "'.\n" +
        "Currently, only Windows, Linux, and OS X are tested with ePOST, although\n" +
        "you are welcome to continue running ePOST on your system.";
        
        int i = message(message, new String[] {"Kill ePOST Proxy", "I'm brave! Launch ePOST!"}, "Kill ePOST Proxy");
        
        if (i == 0) 
          System.exit(-1);
      }
    }
  }
  
  /**
   * Internal method which returns a random subset of the address to ping in parallel
   *
   * @param array The list of all hosts
   * @param num The number of hosts to return
   * @return A subset of the specified length
   */
  protected InetSocketAddress[] randomSubset(InetSocketAddress[] array, int num) {
    InetSocketAddress[] result = new InetSocketAddress[num];
    
    for (int i=0; i<result.length; i++)
      result[i] = array[environment.getRandomSource().nextInt(array.length)];
    
    return result;
  }
  
  /**
   * Method which checks the NAT connection
   *
   * @param parameters The parameters to use
   */
  protected void startCheckNAT(Environment env) throws Exception {
    Parameters parameters = env.getParameters();
    System.err.println("Starting parsing...");
    InetSocketAddress[] addresses = parameters.getInetSocketAddressArray("pastry_proxy_connectivity_hosts");
    System.err.println("Done parsing...");
    
    try {
      natAddress = SocketPastryNodeFactory.verifyConnection(parameters.getInt("pastry_proxy_connectivity_timeout")/4,
                                                            new InetSocketAddress(InetAddress.getLocalHost(), parameters.getInt("pastry_proxy_connectivity_port")),
                                                            randomSubset(addresses, 5), env).getAddress();
    } catch (SocketTimeoutException e) {}
  
    if (natAddress == null) 
      try {
        natAddress = SocketPastryNodeFactory.verifyConnection(parameters.getInt("pastry_proxy_connectivity_timeout")/2,
                                                              new InetSocketAddress(InetAddress.getLocalHost(), parameters.getInt("pastry_proxy_connectivity_port")),
                                                              randomSubset(addresses, 5), env).getAddress();
      } catch (SocketTimeoutException e) {}
    
    if (natAddress == null) 
      try {
        natAddress = SocketPastryNodeFactory.verifyConnection(parameters.getInt("pastry_proxy_connectivity_timeout"),
                                                              new InetSocketAddress(InetAddress.getLocalHost(), parameters.getInt("pastry_proxy_connectivity_port")),
                                                              randomSubset(addresses, 5), env).getAddress();
      } catch (SocketTimeoutException e) {}
    
    if (natAddress == null) {
      int j = message("ePOST attempted to determine the NAT IP address, but was unable to.  This\n" +
                      "is likely caused by an incorrectly configured NAT - make sure that your NAT\n" +
                      "is set up to forward both TCP and UDP packets on port " + parameters.getInt("pastry_proxy_connectivity_port") + " to '" + InetAddress.getLocalHost() + "'.\n\n" + 
                      "Error: java.net.SocketTimeoutException", new String[] {"Kill ePOST Proxy", "Retry"}, "Kill ePOST Proxy");
    
      if (j == 1)
        startCheckNAT(env);
      else
        System.exit(-1);
    } else {
      if (parameters.getBoolean("pastry_proxy_connectivity_show_message")) {
        message("ePOST successfully checked your connection - it appears that the IP address\n" +
                "of your NAT is " + natAddress.getHostAddress() + ".  Please do not remove the port forwarding on\n" + 
                "your NAT as long as you are using ePOST.", new String[] {"OK"}, "OK");
        
        parameters.setBoolean("pastry_proxy_connectivity_show_message", false);
        parameters.store();
      }
    }
  }
    
  
  /**
   * Method which sees if we are going to use a proxy for the pastry node, and if so
   * initiates the remote connection.
   *
   * @param parameters The parameters to use
   */
  protected void startDialog(Parameters parameters) throws Exception {
    if (parameters.getBoolean("proxy_show_dialog")) {
      dialog = new PostDialog(this); 
    }
  }
  
  /**
   * Method which installs shutdown hooks
   *
   * @param parameters The parameters to use
   */  
  protected void startShutdownHooks(Parameters parameters) throws Exception {
    try {
      if (parameters.getBoolean("shutdown_hooks_enable")) {
        stepStart("Installing Shutdown Hooks");
        Runtime.getRuntime().addShutdownHook(new Thread() {
          public void run() {
            int num = Thread.currentThread().getThreadGroup().activeCount();
            System.out.println("ePOST System shutting down with " + num + " active threads");
          }
        });
        stepDone(SUCCESS);
      }    
    } catch (Exception e) {
      panic(e, "There was an error installing the shutdown hooks.", "shutdown_hooks_enable");
    }
  }
  
  /**
   * Method which installs a modified security manager
   *
   * @param parameters The parameters to use
   */  
  protected void startSecurityManager(Parameters parameters) throws Exception {
    try {
      if (parameters.getBoolean("security_manager_install")) {
        stepStart("Installing Custom System Security Manager");
        System.setSecurityManager(new SecurityManager() {
          public void checkPermission(java.security.Permission perm) {}
          public void checkDelete(String file) {}
          public void checkRead(FileDescriptor fd) {}
          public void checkRead(String file) {}
          public void checkRead(String file, Object context) {}
          public void checkWrite(FileDescriptor fd) {}
          public void checkWrite(String file) {}
          public void checkExit(int status) {
            System.out.println("System.exit() called with status " + status + " - dumping stack!");
            Thread.dumpStack();
            super.checkExit(status);
          }
        }); 
        stepDone(SUCCESS);
      }
    } catch (Exception e) {
      panic(e, "There was an error setting the SecurityManager.", "security_manager_install");
    }
  }
  
  /**
   * Method which retrieves the CA's public key 
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveCAKey(Parameters parameters) throws Exception {
    stepStart("Retrieving CA public key");
    InputStream fis = null;
    
    if (parameters.getBoolean("post_ca_key_is_file")) {
      try {
        fis = new FileInputStream(parameters.getString("post_ca_key_name"));
      } catch (Exception e) {
        panic(e, "There was an error locating the certificate authority's public key.", new String[] {"post_ca_key_is_file", "post_ca_key_name"});
      }
    } else {
      try {
        fis = ClassLoader.getSystemResource("ca.publickey").openStream();
      } catch (Exception e) {
        panic(e, "There was an error locating the certificate authority's public key.", "post_ca_key_is_file");
      }
    }
    
    try {
      ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
      caPublic = (PublicKey) ois.readObject();
      ois.close();
      stepDone(SUCCESS);
    } catch (Exception e) {
      panic(e, "There was an error reading the certificate authority's public key.", new String[] {"post_ca_key_is_file", "post_ca_key_name"});
    }
  }
  
  /**
   * Method which updates the user certificate from userid.certificate and userid.keypair.enc
   * to userid.epost.
   *
   * @param parameters The parameters to use
   */
  protected void startUpdateUser(Parameters parameters) throws Exception {
    String[] files = (new File(".")).list(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.endsWith(".certificate");
      }
    });
    
    for (int i=0; i<files.length; i++) {
      String username = files[i].substring(0, files[i].indexOf("."));
    
      File certificate = new File(username + ".certificate");
      File keypair = new File(username + ".keypair.enc");
      
      if (keypair.exists()) {
        stepStart("Updating " + certificate + " and " + keypair + " to " + username + ".epost");

        CACertificateGenerator.updateFile(certificate, keypair, new File(username + ".epost"));
        certificate.delete();
        keypair.delete();
        
        stepDone(SUCCESS);
      }
    }
  }
    
  
  /**
   * Method which determines the username which POST should run with
   *
   * @param parameters The parameters to use
   */
  protected void startRetrieveUsername(Parameters parameters) throws Exception {
    if ((parameters.getString("post_username") == null) || (parameters.getString("post_username").equals(""))) {
      stepStart("Determining Local Username");
      String[] files = (new File(".")).list(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.endsWith(".epost");
        }
      });
    
      if (files.length > 1) {
        panic("POST could not determine which username to run with - \n" + files.length + " certificates were found in the root directory.\n" +
              "Please remove all but the one which you want to run POST with.");
      } else if (files.length == 0) {
        panic("POST could not determine which username to run with - \n" + 
              "no certificates were found in the root directory.\n" +
              "Please place the userid.epost certificate you want to run POST \n" + 
              "with in the root directory.\n\n" + 
              "If you do not yet have a certificate, once can be created from\n" + 
              "http://www.epostmail.org/");
      } else {
        parameters.setString("post_username", files[0].substring(0, files[0].length()-6));
        stepDone(SUCCESS);
      } 
    }
  }
  
  /**
   * Method which retrieve's the user's certificate
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveUserCertificate(Parameters parameters) throws Exception {
    stepStart("Retrieving " + parameters.getString("post_username") + "'s certificate");
    File file = new File(parameters.getString("post_username") + ".epost");

    if (! file.exists()) 
      panic("POST could not find the certificate file for the user '" + parameters.getString("post_username") + "'.\n" +
            "Please place the file '" + parameters.getString("post_username") + ".epost' in the root directory.");
    
    try {
      certificate = CACertificateGenerator.readCertificate(file);
    
      if (ringId == null) 
        ringId = ((RingId) certificate.getAddress().getAddress()).getRingId();
          
      stepDone(SUCCESS);
    } catch (Exception e) {
      panic(e, "There was an error reading the file '" + parameters.getString("post_username") + ".certificate'.", new String[] {"post_username"});
    }
  }
  
  /**
   * Method which verifies the user's certificate
   *
   * @param parameters The parameters to use
   */  
  protected void startVerifyUserCertificate(Parameters parameters) throws Exception {
    if (parameters.getBoolean("post_certificate_verification_enable")) {
      stepStart("Verifying " + parameters.getString("post_username") + "'s certificate");
      CASecurityModule module = new CASecurityModule(caPublic);
      ExternalContinuation e = new ExternalContinuation();
      module.verify(certificate, e);
      e.sleep();
      
      if (e.exceptionThrown())
        panic(e.getException(), "Certificate for user " + parameters.getString("post_username") + " could not be verified.", new String[] {"post_username"});
      
      if (! ((Boolean) e.getResult()).booleanValue()) 
        panic("Certificate for user " + parameters.getString("post_username") + " could not be verified.");
      
      stepDone(SUCCESS);
    }    
  }
  
  /**
   * Method which retrieves the user's encrypted keypair
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveUserKey(Parameters parameters) throws Exception {
    stepStart("Retrieving " + parameters.getString("post_username") + "'s encrypted keypair");
    File file = new File(parameters.getString("post_username") + ".epost");
    
    if (! file.exists()) 
      panic("ERROR: ePOST could not find the keypair for user " + parameters.getString("post_username"));
    
    String password = parameters.getString("post_password");
    
    if ((password == null) || (password.equals(""))) {
      password = new PasswordFrame(parameters).getPassword();
      
      if (parameters.getBoolean("post_password_remember"))
        parameters.setString("post_password", password);
    }
    
    try {
      pair = CACertificateGenerator.readKeyPair(file, password);
      stepDone(SUCCESS);
    } catch (SecurityException e) {
      parameters.remove("post_password");
      parameters.store();
      stepDone(FAILURE);
      startRetrieveUserKey(parameters);
    }
  }
  
  /**
   * Method which verifies the user's encrypted keypair
   *
   * @param parameters The parameters to use
   */  
  protected void startVerifyUserKey(Parameters parameters) throws Exception {
    if (parameters.getBoolean("post_keypair_verification_enable")) {
      stepStart("Verifying " + parameters.getString("post_username") + "'s keypair");
      if (! pair.getPublic().equals(certificate.getKey())) 
        panic("Keypair for user " + parameters.getString("post_username") + " did not match certificate.");

      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which sets up the post log we're going to clone, if we can't find ours
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveUserClone(Parameters parameters) throws Exception {
    if (parameters.getBoolean("post_log_clone_enable")) {
      stepStart("Creating log for previous address " + parameters.getString("post_log_clone_username"));
      clone = new PostUserAddress(new MultiringIdFactory(generateRingId("Rice"), new PastryIdFactory()), parameters.getString("post_log_clone_username"));
      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which retrieve the post user's certificate and key
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveUser(Parameters parameters) throws Exception {
    if (parameters.getBoolean("post_proxy_enable")) {
      startUpdateUser(parameters);
      startRetrieveUsername(parameters);
      startRetrieveUserCertificate(parameters);
      startVerifyUserCertificate(parameters);
      startRetrieveUserKey(parameters);
      startVerifyUserKey(parameters);
      startRetrieveUserClone(parameters);
      
      address = (PostUserAddress) certificate.getAddress();
    }
  }
  
  /**
   * Method which loads the ring certificates ports to use
   *
   * @param parameters The parameters to use
   */
  protected void startLoadRingCertificates(Parameters parameters) throws Exception {
    stepStart("Loading Signed Ring Certificates");
    cert = RingCertificate.getCertificate(((RingId) address.getAddress()).getRingId());
    
    if (cert == null)
      throw new RuntimeException("Could not find a ring certificate for ring '" + ((RingId) address.getAddress()).getRingId() + "'\n" +
                                 "Please make sure you have the latest code from www.epostmail.org.");
    
    globalCert = RingCertificate.getCertificate(generateRingId(null));
    
    if ((globalCert == null) && (parameters.getBoolean("multiring_global_enable")))
        throw new RuntimeException("Could not find a ring certificate for the global ring.\n" +
                                   "Please make sure you have the latest code from www.epostmail.org.");
        
        stepDone(SUCCESS);
  }

  /**
   * Method which determines the local ports to use
   *
   * @param parameters The parameters to use
   */
  protected void startDeterminePorts(Parameters parameters) throws Exception {
    stepStart("Determining Local Ports");
    String parameter = "pastry_ring_" + ((RingId) address.getAddress()).getRingId().toStringFull() + "_port";
    port = (parameters.contains(parameter) ? parameters.getInt(parameter) : cert.getPort());
    
    if (parameters.getBoolean("multiring_global_enable")) {
      parameter = "pastry_ring_" + generateRingId(null).toStringFull()+ "_port";
      globalPort = (parameters.contains(parameter) ? parameters.getInt(parameter) : globalCert.getPort());
    }
    
    stepDone(SUCCESS);
  }
  
  /**
   * Method which determines the local SMTP server to use
   *
   * @param parameters The parameters to use
   */
  protected void startDetermineSMTPServer(Parameters parameters) throws Exception {
    stepStart("Determining Default SMTP Server");
    String parameter = "email_ring_" + ((RingId) address.getAddress()).getRingId().toStringFull() + "_smtp_server";
    if (parameters.getString(parameter) != null) {
      smtpServer = parameters.getString(parameter);
    } else {
      smtpServer = "";

      try {
        if (parameters.getBoolean("proxy_show_dialog")) {
          SMTPServerPanel panel = new SMTPServerPanel(parameters);
          smtpServer = panel.getSMTPServer();
          
          if (panel.remember()) {
            parameters.setString(parameter, smtpServer);
            parameters.store();
          }
        }
      } catch (Throwable t) {
        System.out.println("Determining SMTP server causing error " + t);
      }
    }
    
    stepDone(SUCCESS);
  }

  /**
   * Method which creates the IdFactory to use
   *
   * @param parameters The parameters to use
   */  
  protected void startCreateIdFactory(Parameters parameters) throws Exception {
    stepStart("Creating Id Factory");
    FACTORY = new MultiringIdFactory(ringId, new PastryIdFactory());
    stepDone(SUCCESS);
  }
    
  /**
   * Method which initializes the storage managers
   *
   * @param parameters The parameters to use
   */  
  protected void startStorageManagers(Environment env) throws Exception {
    Parameters parameters = env.getParameters();
    String hostname = "localhost";
    
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {}
    
    String prefix = hostname + "-" + port;
    String location = parameters.getString("storage_root_location");
    int diskLimit = parameters.getInt("storage_disk_limit");
    int cacheLimit = parameters.getInt("storage_cache_limit");
    
    stepStart("Starting Immutable Storage");
    immutableStorage = new StorageManagerImpl(FACTORY,
                                              new PersistentStorage(FACTORY, prefix + "-immutable", location, diskLimit, env),
                                              new LRUCache(new PersistentStorage(FACTORY, prefix + "-cache", ".", diskLimit, env), cacheLimit));
    stepDone(SUCCESS);
    
    stepStart("Starting Mutable Storage");
    mutableStorage = new StorageManagerImpl(FACTORY,
                                            new PersistentStorage(FACTORY, prefix + "-mutable", location, diskLimit, env),
                                            new EmptyCache(FACTORY));    
    stepDone(SUCCESS);
    
    stepStart("Starting Pending Message Storage");
    pendingStorage = new StorageManagerImpl(FACTORY,
                                            new PersistentStorage(FACTORY, prefix + "-pending", location, diskLimit, env),
                                            new EmptyCache(FACTORY));    
    stepDone(SUCCESS);
    
    stepStart("Starting Delivered Message Storage");
    deliveredStorage = new StorageManagerImpl(FACTORY,
                                              new PersistentStorage(FACTORY, prefix + "-delivered", location, diskLimit, env),
                                              new EmptyCache(FACTORY));    
    stepDone(SUCCESS);
    
    if (parameters.getBoolean("past_garbage_collection_enable")) {
      stepStart("Starting Trashcan Storage");
      trashStorage = new StorageManagerImpl(FACTORY,
                                            new PersistentStorage(FACTORY, prefix + "-trash", location, diskLimit, false, env),
                                            new EmptyCache(FACTORY));
      stepDone(SUCCESS);
    }
    
    if (parameters.getBoolean("glacier_enable")) {
      FragmentKeyFactory KFACTORY = new FragmentKeyFactory((MultiringIdFactory) FACTORY);
      VersionKeyFactory VFACTORY = new VersionKeyFactory((MultiringIdFactory) FACTORY);
      PastryIdFactory PFACTORY = new PastryIdFactory();

      stepStart("Starting Glacier Storage");
      glacierMutableStorage = new StorageManagerImpl(KFACTORY,
                                              new PersistentStorage(KFACTORY, prefix + "-glacier-mutable", location, diskLimit, env),
                                              new EmptyCache(KFACTORY));
      glacierImmutableStorage = new StorageManagerImpl(KFACTORY,
                                              new PersistentStorage(KFACTORY, prefix + "-glacier-immutable", location, diskLimit, env),
                                              new EmptyCache(KFACTORY));
      glacierNeighborStorage = new StorageManagerImpl(FACTORY,
                                              new PersistentStorage(FACTORY, prefix + "-glacier-neighbor", location, diskLimit, env),
                                              new EmptyCache(FACTORY));
      aggrWaitingStorage = new StorageManagerImpl(VFACTORY,
                                              new PersistentStorage(VFACTORY, prefix + "-aggr-waiting", location, diskLimit, env),
                                              new EmptyCache(VFACTORY));
      if (parameters.getBoolean("glacier_use_trashcan")) {
        glacierTrashStorage = new StorageManagerImpl(KFACTORY,
                                              new PersistentStorage(KFACTORY, prefix + "-glacier-trash", location, diskLimit, false, env),
                                              new EmptyCache(KFACTORY));
      } else {
        glacierTrashStorage = null;
      }
      
      stepDone(SUCCESS);
    }
    
    if (parameters.getBoolean("past_backup_cache_enable")) {
      long backupLimit = parameters.getLong("past_backup_cache_limit");
      
      stepStart("Starting Immutable Backup Cache");
      immutableBackupCache = new LRUCache(new PersistentStorage(FACTORY, prefix + "-immutable-cache", ".", diskLimit, env), cacheLimit);
      stepDone(SUCCESS);
      
      stepStart("Starting Pending Backup Cache");
      pendingBackupCache = new LRUCache(new PersistentStorage(FACTORY, prefix + "-pending-cache", ".", diskLimit, env), cacheLimit);
      stepDone(SUCCESS);
      
      stepStart("Starting Delivered Backup Cache");
      deliveredBackupCache = new LRUCache(new PersistentStorage(FACTORY, prefix + "-delivered-cache", ".", diskLimit, env), cacheLimit);
      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which starts up the local pastry node
   *
   * @param parameters The parameters to use
   */  
  protected void startPastryNode(Parameters parameters) throws Exception {    
    stepStart("Creating Pastry node");
    String prefix = ((RingId) address.getAddress()).getRingId().toStringFull();
    
    if (logManager instanceof NetworkLogManager)
      ((NetworkLogManager) logManager).setInfo(port, cert.getKey(), cert.getLogServer());
    
    factory = DistPastryNodeFactory.getFactory(new CertifiedNodeIdFactory(port), cert.getProtocol(), port);
    InetSocketAddress proxyAddress = null;
        
    if (natAddress != null)
      proxyAddress = new InetSocketAddress(natAddress, port);
    
    rice.pastry.NodeHandle bootHandle = factory.getNodeHandle(cert.getBootstraps());
    
    if ((bootHandle == null) && (! parameters.getBoolean("pastry_ring_" + prefix+ "_allow_new_ring")))
      panic(new RuntimeException(), 
            "Could not contact existing ring and not allowed to create a new ring. This\n" +
            "is likely because your computer is not properly connected to the Internet\n" +
            "or the ring you are attempting to connect to is off-line.  Please check\n" +
            "your connection and try again later.", "pastry_ring_" + prefix + "_allow_new_ring");

    node = factory.newNode(bootHandle, proxyAddress);
    pastryNode = (PastryNode) node;
    timer = ((DistPastryNode) node).getTimer();
    
    ((PersistentStorage) immutableStorage.getStorage()).setTimer(timer);
    ((PersistentStorage) ((LRUCache) immutableStorage.getCache()).getStorage()).setTimer(timer);
    
    ((PersistentStorage) mutableStorage.getStorage()).setTimer(timer);
    ((PersistentStorage) pendingStorage.getStorage()).setTimer(timer);
    ((PersistentStorage) deliveredStorage.getStorage()).setTimer(timer);
    if (trashStorage != null)
      ((PersistentStorage) trashStorage.getStorage()).setTimer(timer);
    
    ((PersistentStorage) glacierImmutableStorage.getStorage()).setTimer(timer);
    ((PersistentStorage) glacierMutableStorage.getStorage()).setTimer(timer);
    ((PersistentStorage) glacierNeighborStorage.getStorage()).setTimer(timer);
    ((PersistentStorage) aggrWaitingStorage.getStorage()).setTimer(timer);
    
    if (glacierTrashStorage != null)
      ((PersistentStorage) glacierTrashStorage.getStorage()).setTimer(timer);
    
    int count = 0;
    
    do {
      System.out.println("Sleeping to allow node to boot into the ring");
      Thread.sleep(3000);
      count++;
      
      if (count > 10) {
        panic("The Pastry node has unsuccessfully tried for 30 seconds to boot into the\n" +
              "ring - it is highly likely that there is a problem preventing the connection.\n" + 
              "The most common error is a firewall which is preventing incoming connections - \n" +
              "please ensure that any firewall protecting you machine allows incoming traffic \n" +
              "in both UDP and TCP on port " + port);
      }
    } while ((! parameters.getBoolean("pastry_ring_" + prefix+ "_allow_new_ring")) &&
             (pastryNode.getLeafSet().size() == 0));
    
    stepDone(SUCCESS);
  }  
  
  /**
   * Method which builds a ring id given a string to hash.  If the string is null, then
   * the global ring is returned
   *
   * @param name The name to generate a ring from
   * @return The ringId
   */
  protected rice.p2p.commonapi.Id generateRingId(String name) {
    IdFactory factory = new PastryIdFactory();

    if (name != null) {
      rice.p2p.commonapi.Id ringId = factory.buildId(name);
      byte[] ringData = ringId.toByteArray();
    
      for (int i=0; i<ringData.length - MultiringNodeCollection.BASE; i++) 
        ringData[i] = 0;
    
      return factory.buildId(ringData);
    } else {
      return factory.buildId(new byte[20]);
    }
  }
  
  /**
   * Method which starts up the local multiring node service
   *
   * @param parameters The parameters to use
   */  
  protected void startMultiringNode(Environment env) throws Exception { 
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("multiring_enable")) {
      Id ringId = ((RingId) address.getAddress()).getRingId();

      stepStart("Creating Multiring node in ring " + ringId);
      node = new MultiringNode(ringId, node, env);
      Thread.sleep(3000);
      stepDone(SUCCESS); 
    }
  } 
  
  /**
    * Method which starts up the local pastry node
   *
   * @param parameters The parameters to use
   */  
  protected void startGlobalPastryNode(Parameters parameters) throws Exception {    
    stepStart("Creating Global Pastry node");
    String prefix = generateRingId(null).toStringFull();
        
    DistPastryNodeFactory factory = DistPastryNodeFactory.getFactory(new CertifiedNodeIdFactory(port), globalCert.getProtocol(), globalPort);
    InetSocketAddress proxyAddress = null;
    
    if (parameters.getBoolean("pastry_ring_" + prefix+ "_proxy_enable"))
      proxyAddress = parameters.getInetSocketAddress("pastry_ring_" + prefix+ "_proxy_address");
    
    globalNode = factory.newNode(factory.getNodeHandle(globalCert.getBootstraps()), (rice.pastry.NodeId) ((RingId) node.getId()).getId(), proxyAddress);
    globalPastryNode = (PastryNode) globalNode;

    int count = 0;
    
    do {
      System.out.println("Sleeping to allow global node to boot into the ring");
      Thread.sleep(3000);
      count++;
      
      if (count > 10) {
        panic("The global Pastry node has unsuccessfully tried for 30 seconds to boot into the\n" +
              "ring - it is highly likely that there is a problem preventing the connection.\n" + 
              "The most common error is a firewall which is preventing incoming connections - \n" +
              "please ensure that any firewall protecting you machine allows incoming traffic \n" +
              "in both UDP and TCP on port " + globalPort);
      }
    } while ((! parameters.getBoolean("pastry_ring_" + prefix + "_allow_new_ring")) &&
             (globalPastryNode.getLeafSet().size() == 0));
    
    
    stepDone(SUCCESS);
  }     
  
  /**
   * Method which starts up the global multiring node service
   *
   * @param parameters The parameters to use
   */  
  protected void startGlobalMultiringNode(Environment env) throws Exception { 
    Parameters parameters = env.getParameters();
    stepStart("Creating Multiring node in Global ring");
    globalNode = new MultiringNode(generateRingId(null), globalNode, (MultiringNode) node, env);
    Thread.sleep(3000);
    stepDone(SUCCESS); 
  }
  
  /**
   * Method which starts up the global ring node, if required
   *
   * @param parameters The parameters to use
   */  
  protected void startGlobalNode(Environment env) throws Exception { 
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("multiring_global_enable") && (natAddress == null)) {
      startGlobalPastryNode(parameters);
      startGlobalMultiringNode(env);
    }
  } 
  
  /**
   * Method which initializes and starts up the glacier service
   *
   * @param parameters The parameters to use
   */  
  protected void startGlacier(Environment env) throws Exception {
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("glacier_enable")) {
      stepStart("Starting Glacier service");

      String prefix = InetAddress.getLocalHost().getHostName() + "-" + port;
      VersionKeyFactory VFACTORY = new VersionKeyFactory((MultiringIdFactory) FACTORY);

      immutableGlacier = new GlacierImpl(
        node, glacierImmutableStorage, glacierNeighborStorage,
        parameters.getInt("glacier_num_fragments"),
        parameters.getInt("glacier_num_survivors"),
        (MultiringIdFactory)FACTORY, 
        parameters.getString("application_instance_name") + "-glacier-immutable",
        new GlacierDefaultPolicy(
          new ErasureCodec(
            parameters.getInt("glacier_num_fragments"),
            parameters.getInt("glacier_num_survivors")
          )
        ),
        env
      );
      
      immutableGlacier.setSyncInterval(parameters.getInt("glacier_sync_interval"));
      immutableGlacier.setSyncMaxFragments(parameters.getInt("glacier_sync_max_fragments"));
      immutableGlacier.setRateLimit(parameters.getInt("glacier_max_requests_per_second"));
      immutableGlacier.setNeighborTimeout(parameters.getInt("glacier_neighbor_timeout"));
      immutableGlacier.setBandwidthLimit(1024*parameters.getInt("glacier_max_kbytes_per_sec"), 1024*parameters.getInt("glacier_max_kbytes_per_sec")*parameters.getInt("glacier_max_burst_factor"));
      immutableGlacier.setLogLevel(parameters.getInt("glacier_log_level"));
      immutableGlacier.setTrashcan(glacierTrashStorage);

      final Integer[] done = new Integer[1];
      env.getSelectorManager().invoke(new Runnable() {
        public void run() {
          immutableGlacier.startup();
          done[0] = new Integer(1);
        }
      });
      
      while (done[0] == null)
        Thread.sleep(1000);

      AggregationImpl immutableAggregation = new AggregationImpl(
        node, 
        immutableGlacier,  
        immutablePast,
        aggrWaitingStorage,
        "aggregation.param",
        (MultiringIdFactory) FACTORY,
        parameters.getString("application_instance_name") + "-aggr-immutable",
        new PostAggregationPolicy(), env
      );

      immutableAggregation.setFlushInterval(parameters.getInt("aggregation_flush_interval"));
      immutableAggregation.setMaxAggregateSize(parameters.getInt("aggregation_max_aggregate_size"));
      immutableAggregation.setMaxObjectsInAggregate(parameters.getInt("aggregation_max_objects_per_aggregate"));
      immutableAggregation.setRenewThreshold(parameters.getInt("aggregation_renew_threshold"));
      immutableAggregation.setConsolidationInterval(parameters.getInt("aggregation_consolidation_interval"));
      immutableAggregation.setConsolidationThreshold(parameters.getInt("aggregation_consolidation_threshold"));
      immutableAggregation.setConsolidationMinObjectsPerAggregate(parameters.getInt("aggregation_min_objects_per_aggregate"));
      immutableAggregation.setConsolidationMinUtilization(parameters.getDouble("aggregation_min_aggregate_utilization"));
      immutableAggregation.setLogLevel(parameters.getInt("aggregation_log_level"));

      immutablePast = immutableAggregation;

      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which starts up the local past service
   *
   * @param parameters The parameters to use
   */  
  protected void startPast(Environment env) throws Exception {
    Parameters parameters = env.getParameters();
    stepStart("Starting Past services");
    
    if (parameters.getBoolean("past_garbage_collection_enable")) {
      immutablePast = new GCPastImpl(node, immutableStorage, immutableBackupCache, 
                                     parameters.getInt("past_replication_factor"), 
                                     parameters.getString("application_instance_name") + "-immutable",
                                     new PastPolicy.DefaultPastPolicy(),
                                     parameters.getLong("past_garbage_collection_interval"),
                                     trashStorage, env);
    } else {
      immutablePast = new PastImpl(node, immutableStorage, immutableBackupCache,
                                   parameters.getInt("past_replication_factor"), 
                                   parameters.getString("application_instance_name") + "-immutable", 
                                   new PastPolicy.DefaultPastPolicy(), trashStorage, env);
    }
    
    realImmutablePast = immutablePast;
      
    mutablePast = new PastImpl(node, mutableStorage, null,
                               parameters.getInt("past_replication_factor"), 
                               parameters.getString("application_instance_name") + "-mutable",
                               new PostPastPolicy(), trashStorage, env);
    deliveredPast = new GCPastImpl(node, deliveredStorage, deliveredBackupCache,
                                 parameters.getInt("past_replication_factor"), 
                                 parameters.getString("application_instance_name") + "-delivered",
                                 new PastPolicy.DefaultPastPolicy(),
                                 parameters.getLong("past_garbage_collection_interval"),
                                 trashStorage, env);
    pendingPast = new DeliveryPastImpl(node, pendingStorage, pendingBackupCache,
                                       parameters.getInt("past_replication_factor"), 
                                       parameters.getInt("post_redundancy_factor"),
                                       parameters.getString("application_instance_name") + "-pending", deliveredPast,
                                       parameters.getLong("past_garbage_collection_interval"), env);
    stepDone(SUCCESS);
  }
  
  /**
   * Method which starts up the local post service
   *
   * @param parameters The parameters to use
   */  
  protected void startPost(Environment env) throws Exception {
    if (System.getProperty("RECOVER") != null) {
      stepStart("Recovering/Restoring POST Logs backup");
      ExternalContinuation d = new ExternalContinuation();
      
      String[] pieces = System.getProperty("RECOVER").split("/|:| ");
      if (pieces.length != 5) {
        panic(new RuntimeException(), "The correct usage for the RECOVER option is '-DRECOVER=\"mm/dd/yyyy hh:mm\"' (use 24h format).", "RECOVER");
      }
      
      int month = Integer.parseInt(pieces[0]) - 1;  /* month is 0-based */
      int day = Integer.parseInt(pieces[1]);
      int year = Integer.parseInt(pieces[2]);
      int hour = Integer.parseInt(pieces[3]);
      int minute = Integer.parseInt(pieces[4]);
      if (year < 100)
        year += 2000;
        
      Calendar cal = Calendar.getInstance();
      System.out.println("COUNT: "+env.getTimeSource().currentTimeMillis()+" Recovery: Using timestamp "+(month+1)+"/"+day+"/"+year+" "+hour+":"+minute);
      cal.set(year, month, day, hour, minute, 0);
      StorageService.recoverLogs(address.getAddress(), cal.getTimeInMillis(), pair, immutablePast, mutablePast, d, env);
      d.sleep();
      
      if (d.exceptionThrown())
        throw d.getException();
      stepDone(SUCCESS);
      
      Serializable aggregate = (Serializable) d.getResult();
      
      if (immutablePast instanceof Aggregation) {
        stepStart("Restoring Aggregation Root");
        ExternalContinuation e = new ExternalContinuation();
        ((Aggregation) immutablePast).setHandle(aggregate, e);
        e.sleep();
        
        if (e.exceptionThrown())
          throw e.getException();
        stepDone(SUCCESS);
      }
    }
    
    stepStart("Starting POST service");
    post = new PostImpl(node, immutablePast, mutablePast, pendingPast, deliveredPast, address, pair, certificate, caPublic, 
                        parameters.getString("application_instance_name"), 
                        parameters.getBoolean("post_allow_log_insert"), 
                        parameters.getBoolean("post_announce_presence"), clone,
                        parameters.getLong("post_synchronize_interval"),
                        parameters.getLong("post_object_refresh_interval"),
                        parameters.getLong("post_object_timeout_interval"),
                        env);
        
    stepDone(SUCCESS);
  }
  
  /**
   * Method which forces a log reinsertion, if desired (deletes all of the local log, so beware)
   *
   * @param parameters The parameters to use
   */  
  protected void startInsertLog(Parameters parameters) throws Exception {
    if (parameters.getBoolean("post_force_log_reinsert")) {
      stepStart("Manually inserting new PostLog");
      ExternalContinuation c = new ExternalContinuation();
      ((PostImpl) post).createPostLog(c);
      c.sleep();
      
      if (c.exceptionThrown())
        throw c.getException();
      
      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which fetches the local user's log
   *
   * @param parameters The parameters to use
   */  
  protected void startFetchLog(Environment env) throws Exception {
    Parameters parameters = env.getParameters();
    if (parameters.getBoolean("post_proxy_enable") &&
        parameters.getBoolean("post_fetch_log")) {
      int retries = 0;
      
      stepStart("Fetching POST log at " + address.getAddress());
      boolean done = false;
      
      while (!done) {
        final ExternalContinuation c = new ExternalContinuation();
        env.getSelectorManager().invoke(new Runnable() {
          public void run() {
            post.getPostLog(c); 
          }
        });
        
        c.sleep();
        
        if (c.exceptionThrown()) { 
          stepDone(FAILURE, "Fetching POST log caused exception " + c.getException());
          stepStart("Sleeping and then retrying to fetch POST log (" + retries + "/" + parameters.getInt("post_fetch_log_retries"));
          if (retries < parameters.getInt("post_fetch_log_retries")) {
            retries++;
            Thread.sleep(parameters.getInt("post_fetch_log_retry_sleep"));
          } else {
            throw c.getException(); 
          }
        } else {
          done = true;
          System.out.println("LOG IS A " + c.getResult() + " " + c.getResult().getClass().getName());
          log = (PostLog) c.getResult();
        }
      }
      
      
      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which fetches the local user's forwarding log
   *
   * @param parameters The parameters to use
   */  
  protected void startFetchForwardingLog(Parameters parameters) throws Exception {
    if (parameters.getBoolean("post_proxy_enable") &&
        parameters.getBoolean("post_fetch_log")) {
      
      stepStart("Fetching POST forwarding log");
      
      ExternalContinuation c = new ExternalContinuation();
      log.getChildLog(ForwardLog.FORWARD_NAME, c);
      c.sleep();
      
      if (c.exceptionThrown()) { 
        stepDone(FAILURE, "Fetching POST forward log caused exception " + c.getException());
        throw c.getException(); 
      } else {
        forwardLog = (ForwardLog) c.getResult();
      }
      
      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which updates the local user's forward log
   *
   * @param parameters The parameters to use
   */  
  protected void startUpdateForwardingLog(Parameters parameters) throws Exception {
    if (parameters.getBoolean("post_proxy_enable") &&
        parameters.getBoolean("post_fetch_log")) {
      String[] addresses = parameters.getStringArray("post_forward_addresses");
      
      if (((forwardLog == null) && (addresses.length > 0)) ||
          ((forwardLog != null) && (! Arrays.equals(forwardLog.getAddresses(), addresses)))) {
        stepStart("Updating POST forwarding log");
        ExternalContinuation c = new ExternalContinuation();
        
        if (forwardLog == null) {
          forwardLog = new ForwardLog(log, addresses, post.getStorageService().getRandomNodeId(), post, c);
        } else {   
          forwardLog.setAddresses(addresses, c);
        }

        c.sleep();
        
        if (c.exceptionThrown()) { 
          stepDone(FAILURE, "Updating POST forward log caused exception " + c.getException());
          throw c.getException(); 
        } 
        
        stepDone(SUCCESS);
      }
    }
  }
  
  protected Environment start(Environment env) throws Exception {
//    parameters = env.getParameters();  // done in start(void)
    startLivenessMonitor(env);
    startRedirection(env);
    startCheckBoot(env);    
    startDialog(parameters);
        
    System.out.println("-- Booting ePOST 2.0 with classpath " + System.getProperty("java.class.path") + " --");
    
    if (dialog != null) 
      dialog.append("\n-- Booting ePOST 2.0 with classpath " + System.getProperty("java.class.path") + " --\n");
    
    sectionStart("Initializing Parameters");
    startShutdownHooks(parameters);
    startSecurityManager(parameters);
    startRetrieveCAKey(parameters);
    startRetrieveUser(parameters);
    startLoadRingCertificates(parameters);
    startDeterminePorts(parameters);
    startDetermineSMTPServer(parameters);
    sectionDone();
    
    sectionStart("Initializing Disk Storage");
    startCreateIdFactory(parameters);
    startStorageManagers(env);
    sectionDone();
    
    sectionStart("Bootstrapping Local Node");
    startPastryNode(parameters);
    sectionDone();
    
    sectionStart("Bootstrapping Multiring Protocol");
    startMultiringNode(env);
    startGlobalNode(env);
    sectionDone();
    
    sectionStart("Bootstrapping Local Post Applications");
    startPast(env);
    startGlacier(env);
    startPost(env);
    startInsertLog(parameters);
    startFetchLog(env);
    startFetchForwardingLog(parameters);
    startUpdateForwardingLog(parameters);
    
    sectionDone();
    
    return env;
  }
  
  protected void updateParameters(Parameters parameters) {
    if (parameters.getBoolean("post_allow_log_insert") && parameters.getBoolean("post_allow_log_insert_reset")) {
      parameters.setBoolean("post_allow_log_insert", false);
    }
  }
  
  protected void start() {
    try {
      Environment env = new Environment(DEFAULT_PARAMS_FILES, PROXY_PARAMETERS_NAME);
      parameters = env.getParameters();
      start(env);
      updateParameters(parameters);
      
      if (dialog != null) 
        dialog.append("\n-- Your node is now up and running --\n");
    } catch (Exception e) {
      System.err.println("ERROR: Found Exception while start proxy - exiting - " + e);
      if (dialog != null)
        dialog.append("\n-- ERROR: Found Exception while start proxy - exiting - " + e + " --\n");
      e.printStackTrace();
      
      panic("An error occurred while starting the proxy - the proxy is now exiting.\n" + e.toString());
      
      System.exit(-1);
    }
  }
  
  /**
   * Helper method which throws an exception and tells the user a message
   * why the error occurred.
   *
   * @param e The exception
   * @param m The message why
   */
  public void panic(Exception e, String m, String params) throws Exception {
    panic(e, m, new String[] {params});
  }
    
  public void panic(Exception e, String m, String[] params) throws Exception {
    resign();
    StringBuffer message = new StringBuffer();
    message.append(m + "\n\n");
    message.append("This was most likely due to the setting ");
    
    for (int i=0; i<params.length; i++) {
      message.append("'" + params[i] + "'");
      
      if (i < params.length-1)
        message.append(" or ");
    }
    
    message.append(" in your proxy.params file.\n\n");
    message.append(e.getClass().getName() + ": " + e.getMessage());

    System.err.println("PANIC : " + message + " --- " + e);
    
    try {
      if (! GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadless()) 
      JOptionPane.showMessageDialog(null, message.toString() ,"Error: " + e.getClass().getName(), JOptionPane.ERROR_MESSAGE); 
    } catch (Throwable f) {}
    
    System.exit(-1);
  }
  
  public void panic(String m) {
    System.err.println("PANIC : " + m);
    resign();

    try {
      if (! GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadless()) 
        JOptionPane.showMessageDialog(null, m, "Error Starting POST Proxy", JOptionPane.ERROR_MESSAGE); 
    } catch (Throwable f) {}
    
    System.exit(-1);
  }
  
  public void resign() {
    if (pastryNode != null)
      pastryNode.resign();
    
    if (globalPastryNode != null)
      globalPastryNode.resign();
  }

  public int message(String m, String[] options, String def) {
    System.err.println("MESSAGE : " + m);
    
    try {
      if (! GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadless()) 
        return JOptionPane.showOptionDialog(null, m, "ePOST Message", 
                                             0, JOptionPane.INFORMATION_MESSAGE, null, 
                                             options, def);
    } catch (Throwable f) {}
    
    return 0;
  }
  
  public static void main(String[] args) {
    PostProxy proxy = new PostProxy();
    proxy.start();
  }

  protected void sectionStart(String name) {
    System.out.println(name);
    
    if (dialog != null) dialog.append(name + "\n");
  }

  protected void sectionDone() {
    System.out.println();
    if (dialog != null) dialog.append("\n");
  }

  protected void stepStart(String name) {
    System.out.print(pad("  " + name));
    if (dialog != null) dialog.append(pad("  " + name));
  }

  protected void stepDone(String status) {
    System.out.println("[" + status + "]"); 
    if (dialog != null) dialog.append("[" + status + "]\n");
  }

  protected void stepDone(String status, String message) {
    System.out.println("[" + status + "]");
    System.out.println("    " + message);
    
    if (dialog != null) dialog.append("[" + status + "]\n" + message + "\n");
  }

  protected void stepException(Exception e) {
    System.out.println();

    System.out.println("Exception " + e + " occurred during testing.");

    e.printStackTrace();
    System.exit(0);
  }
  
  protected void dialogPrint(String message) {
    if (dialog != null) dialog.append(message);
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
  
  public GlacierImpl getGlacier() {
    return immutableGlacier;
  }
  
  protected class PostDialog extends JFrame {
    protected JTextArea area;
    protected JScrollPane scroll;
    protected JPanel panel;
    protected JPanel kill;
    
    public PostDialog(PostProxy proxy) {
      panel = new PostPanel();
      kill = new KillPanel(proxy);
      setResizable(false);

      area = new JTextArea(15,75);
      area.setFont(new Font("Courier", Font.PLAIN, 10));
      scroll = new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      
      GridBagLayout layout = new GridBagLayout();
      getContentPane().setLayout(layout);
      
      GridBagConstraints c = new GridBagConstraints();
      layout.setConstraints(panel, c);      
      getContentPane().add(panel);
      
      GridBagConstraints d = new GridBagConstraints();
      d.gridy=1;
      layout.setConstraints(scroll, d);      
      getContentPane().add(scroll);
      
      GridBagConstraints e = new GridBagConstraints();
      e.gridy=2;
      layout.setConstraints(kill, e);      
      getContentPane().add(kill);
      
      setTitle("ePOST");
      pack();
      show();
      
      setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      
      addWindowListener(new WindowListener() {
        public void windowClosing(WindowEvent e) {
          String message = "Do you wish to kill the ePOST proxy or just hide the status window?";
          int i = JOptionPane.showOptionDialog(null, message, "Kill ePOST Proxy", 
                                               0, JOptionPane.INFORMATION_MESSAGE, null, 
                                               new Object[] {"Cancel", "Kill ePOST Proxy", "Hide Status Window"}, "Hide Status Window");
          
          if (i == 2)
            hide();
            
          if (i == 1) 
            System.exit(-1);
        }
        public void windowActivated(WindowEvent e) {}
        public void windowClosed(WindowEvent e) {}
        public void windowDeactivated(WindowEvent e) {}
        public void windowDeiconified(WindowEvent e) {}
        public void windowIconified(WindowEvent e) {}
        public void windowOpened(WindowEvent e) {}
      });
    }
    
    public void append(String s) {
      Dimension dim = area.getPreferredSize();
      scroll.getViewport().setViewPosition(new Point(0,(int) (dim.getHeight()+20)));
      area.append(s);
    }
  }
  
  protected class PostPanel extends JPanel {
    public Dimension getPreferredSize() {
      return new Dimension(300,80); 
    }
    
    public void paint(Graphics g) {
      g.setFont(new Font("Times", Font.BOLD, 24));
      g.drawString("Welcome to ePOST!", 50, 40);
      
      g.setFont(new Font("Times", Font.PLAIN, 12));
      g.drawString("The status of your node is shown below.", 52, 60);
      
    }
  }
  
  protected class KillPanel extends JPanel {
    public KillPanel(final PostProxy proxy) {
      JButton restart = new JButton("Restart");
      JButton kill = new JButton("Kill");
      JButton status = new JButton("Stats");
      JButton configuration = new JButton("Prefs");
      
      restart.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int i = JOptionPane.showConfirmDialog(KillPanel.this, "Are your sure you wish to restart your ePOST proxy?\n\n" +
                                                "If you click yes, you node will die and relaunch itself\n" + 
                                                "in 30 seconds.", "Restart", 
                                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
          
          if (i == JOptionPane.YES_OPTION) 
            System.exit(-2);
        }
      });
      
      kill.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int i = JOptionPane.showConfirmDialog(KillPanel.this, "Are your sure you wish to kill your ePOST proxy?", "Kill", 
                                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
          
          if (i == JOptionPane.YES_OPTION) 
            System.exit(-1);
        }
      });
      
      status.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            if (natAddress == null) {
              LocalVisualization vis = new LocalVisualization((DistNodeHandle) pastryNode.getLocalNodeHandle());
            } else {
              DistNodeHandle handle = (DistNodeHandle) factory.getNodeHandle(new InetSocketAddress(InetAddress.getLocalHost(), 10001));
              LocalVisualization vis = new LocalVisualization(handle);
            }
          } catch (Exception f) {
            System.err.println("Got Error launching Vis: " + f);
          }
        }
      });
      
      configuration.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final ConfigurationFrame frame = new ConfigurationFrame(parameters, proxy);
          
          Thread t = new Thread() {
            public void run() {
              try {
                synchronized (frame) {
                  frame.wait();
                }
                
                startUpdateForwardingLog(parameters);
              } catch (Exception f) {
                System.err.println("Got Exception e waiting for config frame" + f);
              }
            }
          };
          
          t.start();
        }
      });
      
      GridBagLayout layout = new GridBagLayout();
      setLayout(layout);
      
      GridBagConstraints a = new GridBagConstraints();
      layout.setConstraints(configuration, a);      
      add(configuration);
      
      GridBagConstraints b = new GridBagConstraints();
      b.gridx=1;
      layout.setConstraints(status, b);      
      add(status);
      
      GridBagConstraints c = new GridBagConstraints();
      c.gridx=2;
      layout.setConstraints(restart, c);      
      add(restart);
      
      GridBagConstraints d = new GridBagConstraints();
      d.gridx=3;
      layout.setConstraints(kill, d);      
      add(kill);
    }
    
    public Dimension getPreferredSize() {
      return new Dimension(300, 30);
    }
    
  }

  protected class LivenessThread extends Thread {
    
    protected InputStream in;
    protected OutputStream out;
    
    protected Pipe.SinkChannel sink;    
    protected Pipe.SourceChannel source;
    
    protected byte[] buffer1;
    protected byte[] buffer2;
    
    protected LivenessKeyHandler handler;
    
    public LivenessThread(Environment env) throws IOException {
      Pipe pipeA = Pipe.open();
      Pipe pipeB = Pipe.open();
      this.in = System.in;
      this.out = System.out;
      
      this.sink = pipeA.sink();
      this.source = pipeB.source();
      
      this.buffer1 = new byte[1];
      this.buffer2 = new byte[1];
      
      this.handler = new LivenessKeyHandler(env, pipeA.source(), pipeB.sink());
    }
    
    public void run() {
      try {
        while (true) {
          int i = in.read(buffer1);

          if (i > 0) {
            ByteBuffer b1 = ByteBuffer.wrap(buffer1);
            sink.write(b1);  

            ByteBuffer b2 = ByteBuffer.wrap(buffer2);
            source.read(b2);
            
            out.write(buffer2);
            out.flush();
          } else {
            System.err.println("ERROR: Liveness thread read " + i + " bytes - exiting!");
            return;
          }
        }
      } catch (IOException e) {
        System.err.println("Got IOException " + e + " while monitoring liveness - exiting!");
        e.printStackTrace();
      }
    }
  }

  protected class LivenessKeyHandler extends SelectionKeyHandler {
    
    protected ByteBuffer buffer;
    
    protected Pipe.SourceChannel source;
    protected Pipe.SinkChannel sink;
    
    protected SelectionKey sourceKey;
    protected SelectionKey sinkKey;
   
    public LivenessKeyHandler(Environment env, Pipe.SourceChannel source, Pipe.SinkChannel sink) throws IOException {
      this.buffer = ByteBuffer.allocate(1);
      this.source = source;
      this.sink = sink;
      
      this.source.configureBlocking(false);
      this.sink.configureBlocking(false);
      
      SelectorManager manager = env.getSelectorManager();
      
      this.sourceKey = manager.register(source, this, SelectionKey.OP_READ);
      this.sinkKey = manager.register(sink, this, 0);
    }
    
    public void read(SelectionKey key) {
      try {
        buffer.clear();
        source.read(buffer);
        sinkKey.interestOps(SelectionKey.OP_WRITE);
      } catch (IOException e) {
        System.out.println("IOException while reading liveness monitor! " + e);
        e.printStackTrace();
      }
    }
    
    public void write(SelectionKey key) {
      try {
        buffer.flip();
        sink.write(buffer);
        sinkKey.interestOps(0);
      } catch (IOException e) {
        System.out.println("IOException while reading liveness monitor! " + e);
        e.printStackTrace();
      }
    }
  }
  
  public class PasswordFrame extends JFrame {
    
    protected JPasswordField field;
    
    protected JCheckBox box;
    
    protected Parameters parameters;
    
    protected boolean submitted = false;
    
    public PasswordFrame(Parameters p) {
      super("Password");
      this.parameters = p;
      this.field = new JPasswordField(20);
      this.box = new JCheckBox((javax.swing.Icon) null, parameters.getBoolean("post_password_remember"));
      GridBagLayout layout = new GridBagLayout();
      
      addWindowListener(new WindowListener() {
        public void windowActivated(WindowEvent e) {}      
        public void windowClosed(WindowEvent e) {
          done();
        }      
        public void windowClosing(WindowEvent e) {
          done();
        }      
        public void windowDeactivated(WindowEvent e) {}      
        public void windowDeiconified(WindowEvent e) {}      
        public void windowIconified(WindowEvent e) {}      
        public void windowOpened(WindowEvent e) {}
      });
      
      getContentPane().setLayout(layout);
      
      JLabel fieldLabel = new JLabel("Please enter your password: ", JLabel.TRAILING);
      fieldLabel.setLabelFor(field);
      
      JLabel boxLabel = new JLabel("Remember password: ", JLabel.TRAILING);
      boxLabel.setLabelFor(box);
      
      GridBagConstraints gbc1 = new GridBagConstraints();
      layout.setConstraints(fieldLabel, gbc1);      
      getContentPane().add(fieldLabel);
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 1;
      layout.setConstraints(field, gbc2);      
      getContentPane().add(field);
      
      GridBagConstraints gbc5 = new GridBagConstraints();
      gbc5.gridy = 1;
      layout.setConstraints(boxLabel, gbc5);      
      getContentPane().add(boxLabel);
      
      GridBagConstraints gbc6 = new GridBagConstraints();
      gbc6.gridx = 1;
      gbc6.gridy = 1;
      layout.setConstraints(box, gbc6);      
      getContentPane().add(box);
      
      JButton submit = new JButton("Submit");
      
      GridBagConstraints gbc3 = new GridBagConstraints();
      gbc3.gridx = 1;
      gbc3.gridy = 2;
      layout.setConstraints(submit, gbc3);      
      getContentPane().add(submit);
      
      final JFrame frame = this;
      
      getRootPane().setDefaultButton(submit);
      
      submit.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          done();
        }
      });
      
      pack();
      show();
    }
    
    protected void done() {
      if (! submitted) {
        dispose();
        submitted = true;

        parameters.setBoolean("post_password_remember", box.isSelected());
        parameters.store();
    
        synchronized (parameters) {
          parameters.notifyAll();
        } 
      }
    }
    
    protected String getPassword() throws Exception {
      synchronized (parameters) {
        if (! submitted)
          parameters.wait();
      }
      
      return new String(this.field.getPassword());
    }
  }
  
  public class SMTPServerPanel extends JFrame {
    
    protected JTextField field;
    
    protected JCheckBox box;
    
    protected Parameters parameters;
    
    protected boolean submitted = false;
    
    public SMTPServerPanel(Parameters p) {
      super("SMTP Server");
      this.parameters = p;
      this.field = new JTextField(20);
      this.box = new JCheckBox((javax.swing.Icon) null, false);
      GridBagLayout layout = new GridBagLayout();
      
      addWindowListener(new WindowListener() {
        public void windowActivated(WindowEvent e) {}      
        public void windowClosed(WindowEvent e) {
          done();
        }      
        public void windowClosing(WindowEvent e) {
          done();
        }      
        public void windowDeactivated(WindowEvent e) {}      
        public void windowDeiconified(WindowEvent e) {}      
        public void windowIconified(WindowEvent e) {}      
        public void windowOpened(WindowEvent e) {}
      });
      
      getContentPane().setLayout(layout);
      
      JLabel fieldLabel = new JLabel("Please enter your default SMTP server: ", JLabel.TRAILING);
      fieldLabel.setLabelFor(field);
      
      JLabel boxLabel = new JLabel("Remember setting: ", JLabel.TRAILING);
      boxLabel.setLabelFor(box);
      
      GridBagConstraints gbc1 = new GridBagConstraints();
      layout.setConstraints(fieldLabel, gbc1);      
      getContentPane().add(fieldLabel);
      
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.gridx = 1;
      layout.setConstraints(field, gbc2);      
      getContentPane().add(field);
      
      GridBagConstraints gbc5 = new GridBagConstraints();
      gbc5.gridy = 1;
      layout.setConstraints(boxLabel, gbc5);      
      getContentPane().add(boxLabel);
      
      GridBagConstraints gbc6 = new GridBagConstraints();
      gbc6.gridx = 1;
      gbc6.gridy = 1;
      layout.setConstraints(box, gbc6);      
      getContentPane().add(box);
      
      JButton submit = new JButton("Submit");
      
      GridBagConstraints gbc3 = new GridBagConstraints();
      gbc3.gridx = 1;
      gbc3.gridy = 2;
      layout.setConstraints(submit, gbc3);      
      getContentPane().add(submit);
      
      final JFrame frame = this;
      
      getRootPane().setDefaultButton(submit);
      
      submit.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          done();
        }
      });
      
      pack();
      show();
    }
    
    protected void done() {
      if (! submitted) {
        dispose();
        submitted = true;
        
        synchronized (parameters) {
          parameters.notifyAll();
        } 
      }
    }
    
    protected boolean remember() {
      return box.isSelected();
    }
    
    protected String getSMTPServer() throws Exception {
      synchronized (parameters) {
        if (! submitted)
          parameters.wait();
      }
      
      return new String(this.field.getText());
    }
  }
  

}
