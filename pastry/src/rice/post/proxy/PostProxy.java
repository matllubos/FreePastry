package rice.post.proxy;

import rice.*;
import rice.Continuation.*;

import rice.pastry.PastryNode;
import rice.pastry.dist.*;
import rice.pastry.commonapi.*;
import rice.pastry.standard.*;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v2.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.aggregation.*;
import rice.p2p.multiring.*;

import rice.persistence.*;

import rice.post.*;
import rice.post.delivery.*;
import rice.post.security.*;
import rice.post.security.ca.*;
import rice.post.storage.*;

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
    
  /**
   * The name of the parameters file for Post
   */
  public static String PROXY_PARAMETERS_NAME = "proxy";

  
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
    * The node the services should use
   */
  protected PastryNode pastryNode;
  
  /**
    * The node running in the global ring (if one exists)
   */
  protected PastryNode globalPastryNode;
  
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
   * The local trash can, if in use
   */
  protected StorageManager trashStorage;
  
  /**
   * The local storage for mutable glacier fragments
   */
  protected StorageManager glacierMutableStorage;

  /**
   * The local storage for immutable glacier fragments
   */
  protected StorageManager glacierImmutableStorage;

  /**
   * The local storage for glacier neighbor certificates
   */
  protected StorageManager glacierNeighborStorage;

  /**
   * The local storage for glacier's 'trash can'
   */
  protected StorageManager glacierTrashStorage;

  /**
   * The local storage for objects waiting to be aggregated
   */
  protected StorageManager aggrWaitingStorage;
  
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
  
  protected RemoteProxy remoteProxy;
  
  protected static void initializeParameters(Parameters result, String[][] parameters) {    
    for (int i=0; i<parameters.length; i++)
      result.registerStringParameter(parameters[i][0], parameters[i][1]);
  }
  
  public static String[][] DEFAULT_PARAMETERS = new String[][] {{"pastry_proxy_enable", "false"},
  {"pastry_proxy", "sys01.cs.rice.edu:10001"},
  {"pastry_proxy_username", System.getProperty("user.name")},
  {"pastry_proxy_password", ""}, 
  {"pastry_protocol", "socket"},
  {"pastry_port", "10001"},
  {"pastry_bootstraps", "128.42.7.237:10001"}, //"sys01.cs.rice.edu:10001,sys02.cs.rice.edu:10001,sys03.cs.rice.edu:10001,sys04.cs.rice.edu:10001," + 
                        //"sys05.cs.rice.edu:10001,sys06.cs.rice.edu:10001,sys07.cs.rice.edu:10001,sys08.cs.rice.edu:10001"},
  {"multiring_enable", "true"},
  {"multiring_ring_name", "Rice"},
  {"multiring_global_enable", "false"},
  {"multiring_global_pastry_protocol", "socket"},
  {"multiring_global_pastry_port", "20001"},
  {"multiring_global_pastry_bootstraps", "128.42.7.237:20001"}, //"thor05.cs.rice.edu:20001,thor06.cs.rice.edu:20001,thor07.cs.rice.edu:20001," + 
                                                                //"thor08.cs.rice.edu:20001,thor09.cs.rice.edu:20001,thor10.cs.rice.edu:20001"},
  {"standard_error_redirect_enable", "true"},
  {"standard_output_redirect_enable", "true"},
  {"standard_output_redirect_append", "true"},
  {"standard_output_redirect_filename", "nohup.out"},
  {"shutdown_hooks_enable", "true"},
  {"security_manager_install", "true"}, 
  {"past_use_garbage_collection", "false"},
  {"post_ca_key_is_file", "false"},
  {"post_ca_key_name", "ca.publickey"}, 
  {"post_proxy_enable", "true"},
  {"post_certificate_verification_enable", "true"},
  {"post_keypair_verification_enable", "true"},
  {"post_log_clone_enable", "false"}, 
  {"post_log_clone_username", ""},
  {"post_allow_log_insert", "false"}, 
  {"post_force_log_reinsert", "false"}, 
  {"post_fetch_log", "true"}, 
  {"post_fetch_log_retries", "3"}, 
  {"storage_root_location", "."}, 
  {"storage_disk_limit", "2000000000"},
  {"storage_cache_limit", "50000000"},
  {"glacier_enable", "false"},
  {"glacier_num_fragments", "30"},
  {"glacier_num_survivors", "4"},
  {"glacier_sync_interval", "3600"},
  {"glacier_sync_max_fragments", "100"},
  {"glacier_max_requests_per_second", "3"},
  {"glacier_neighbor_timeout", "7200"},
  {"aggregation_flush_interval", "180"},
  {"aggregation_max_aggregate_size", "1048576"},
  {"aggregation_max_objects_per_aggregate", "20"},
  {"aggregation_renew_threshold", "72"},
  {"past_replication_factor", "3"},
  {"application_instance_name", "PostProxy"}
  };

  /**
   * Method which sees if we are going to use a proxy for the pastry node, and if so
   * initiates the remote connection.
   *
   * @param parameters The parameters to use
   */
  protected void startPastryProxy(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("pastry_proxy_enable")) {
      sectionStart("Creating Remote Proxy");
      
      InetSocketAddress proxy = parameters.getInetSocketAddressParameter("pastry_proxy");
      
      if (parameters.getStringParameter("pastry_proxy_password") == null) {
        String password = CAKeyGenerator.fetchPassword(parameters.getStringParameter("pastry_proxy_username") + "@" + 
                                                       proxy.getAddress() + "'s SSH Password");
        parameters.registerStringParameter("pastry_proxy_password", password);
      }
      
      stepStart("Launching Remote Proxy to " + proxy.getAddress());
      remoteProxy = new RemoteProxy(proxy.getAddress().getHostAddress(), 
                                    parameters.getStringParameter("pastry_proxy_username"), 
                                    parameters.getStringParameter("pastry_proxy_password"), 
                                    proxy.getPort(),
                                    parameters.getIntParameter("pastry_port"));
      remoteProxy.run();
      stepDone(SUCCESS);
      
      sectionDone();
    }
  }
  
  /**
   * Method which redirects standard output and error, if desired.
   *
   * @param parameters The parameters to use
   */  
  protected void startRedirection(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("standard_output_redirect_enable")) {
      stepStart("Redirecting Standard Output");
      System.setOut(new PrintStream(new FileOutputStream(parameters.getStringParameter("standard_output_redirect_filename"), 
                                                         parameters.getBooleanParameter("standard_output_redirect_append"))));
      stepDone(SUCCESS);
    }
    
    if (parameters.getBooleanParameter("standard_error_redirect_enable")) {
      stepStart("Redirecting Standard Error");
      System.setErr(System.out);
      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which installs shutdown hooks
   *
   * @param parameters The parameters to use
   */  
  protected void startShutdownHooks(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("shutdown_hooks_enable")) {
      stepStart("Installing Shutdown Hooks");
      Runtime.getRuntime().addShutdownHook(new Thread() {
        public void run() {
          int num = Thread.currentThread().getThreadGroup().activeCount();
          System.out.println("ePOST System shutting down with " + num + " active threads");
        }
      });
      stepDone(SUCCESS);
    }    
  }
  
  /**
   * Method which installs a modified security manager
   *
   * @param parameters The parameters to use
   */  
  protected void startSecurityManager(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("security_manager_install")) {
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
  }
  
  /**
   * Method which retrieves the CA's public key 
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveCAKey(Parameters parameters) throws Exception {
    stepStart("Retrieving CA public key");
    InputStream fis = null;
    
    if (parameters.getBooleanParameter("post_ca_key_is_file")) 
      fis = new FileInputStream(parameters.getStringParameter("post_ca_key_name"));
    else 
      fis = ClassLoader.getSystemResource("ca.publickey").openStream();
    
    
    ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
    caPublic = (PublicKey) ois.readObject();
    ois.close();
    stepDone(SUCCESS);
  }
  
  /**
   * Method which determines the username which POST should run with
   *
   * @param parameters The parameters to use
   */
  protected void startRetrieveUsername(Parameters parameters) throws Exception {
    if (parameters.getStringParameter("post_username") == null) {
      stepStart("Determining Local Username");
      String[] files = (new File(".")).list(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.endsWith(".certificate");
        }
      });
    
      if (files.length > 1) {
        stepDone(FAILURE, "ERROR: Expected at most one certificate, found " + files.length);
        System.exit(-13);
      } else if (files.length == 0) {
        stepDone(FAILURE, "ERROR: Did not find any certificates, make sure you have a valid user certificate");
        System.exit(-12);
      } else {
        parameters.registerStringParameter("post_username", files[0].substring(0, files[0].indexOf(".")));
      } 
    }
  }
  
  /**
   * Method which retrieve's the user's certificate
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveUserCertificate(Parameters parameters) throws Exception {
    stepStart("Retrieving " + parameters.getStringParameter("post_username") + "'s certificate");
    File file = new File(parameters.getStringParameter("post_username") + ".certificate");

    if (! file.exists()) {
      stepDone(FAILURE, "ERROR: Could not find certificate for user " + parameters.getStringParameter("post_username"));
      System.exit(-11);
    }
    
    InputStream fis = new FileInputStream(file);
    ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
    
    certificate = (PostCertificate) ois.readObject();
    
    if (ringId == null) 
      ringId = ((RingId) certificate.getAddress().getAddress()).getRingId();
    
    ois.close();
    stepDone(SUCCESS);
  }
  
  /**
   * Method which verifies the user's certificate
   *
   * @param parameters The parameters to use
   */  
  protected void startVerifyUserCertificate(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("post_certificate_verification_enable")) {
      stepStart("Verifying " + parameters.getStringParameter("post_username") + "'s certificate");
      CASecurityModule module = new CASecurityModule(caPublic);
      ExternalContinuation e = new ExternalContinuation();
      module.verify(certificate, e);
      e.sleep();
      
      if (e.exceptionThrown())
        throw e.getException();
      
      if (! ((Boolean) e.getResult()).booleanValue()) {
        System.out.println("Certificate for user " + parameters.getStringParameter("post_username") + " could not be verified.");
        System.exit(-16);
      }
      
      stepDone(SUCCESS);
    }    
  }

  /**
   * Method which decrypt's the user's keypair
   *
   * @param parameters The parameters to use
   * @param cipher The ciphertext
   * @param pass The password user to encrypt the ciphertext
   * @return The decrypted keypair
   */
  protected KeyPair decryptUserKey(Parameters parameters, byte[] cipher, byte[] pass) throws Exception {
    byte[] key = SecurityUtils.hash(pass);
    byte[] data = SecurityUtils.decryptSymmetric(cipher, key);
    
    return (KeyPair) SecurityUtils.deserialize(data);
  }
  
  /**
   * Method which retrieves the user's encrypted keypair
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveUserKey(Parameters parameters) throws Exception {
    stepStart("Retrieving " + parameters.getStringParameter("post_username") + "'s encrypted keypair");
    File file = new File(parameters.getStringParameter("post_username") + ".keypair.enc");
    
    if (! file.exists()) {
      stepDone(FAILURE, "ERROR: Could not find keypair for user " + parameters.getStringParameter("post_username"));
      System.exit(-11);
    }
    
    InputStream fis = new FileInputStream(file);  
    ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
    byte[] cipher = (byte[]) ois.readObject();
    ois.close();
    stepDone(SUCCESS);
    
    if (parameters.getStringParameter("post_password") == null) 
      parameters.registerStringParameter("post_password", CAKeyGenerator.fetchPassword(parameters.getStringParameter("post_username") + "'s password"));
    
    try {
      stepStart("Decrypting " + parameters.getStringParameter("post_username") + "'s keypair");
      pair = decryptUserKey(parameters, cipher, parameters.getStringParameter("post_password").getBytes());
      stepDone(SUCCESS);
    } catch (SecurityException e) {
      stepDone(FAILURE, "Incorrect password.  Please try again.");
      System.exit(-19);
    }
  }
  
  /**
   * Method which verifies the user's encrypted keypair
   *
   * @param parameters The parameters to use
   */  
  protected void startVerifyUserKey(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("post_keypair_verification_enable")) {
      stepStart("Verifying " + parameters.getStringParameter("post_username") + "'s keypair");
      if (! pair.getPublic().equals(certificate.getKey())) {
        stepDone(FAILURE, "Keypair for user " + parameters.getStringParameter("post_username") + " did not match certificate.");
        System.exit(-17);
      }
      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which sets up the post log we're going to clone, if we can't find ours
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveUserClone(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("post_log_clone_enable")) {
      stepStart("Creating PostLog for previous address " + parameters.getBooleanParameter("post_log_clone_username"));
      clone = new PostUserAddress(FACTORY, parameters.getStringParameter("post_log_clone_username"));
      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which retrieve the post user's certificate and key
   *
   * @param parameters The parameters to use
   */  
  protected void startRetrieveUser(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("post_proxy_enable")) {
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
  protected void startStorageManagers(Parameters parameters) throws Exception {
    String prefix = InetAddress.getLocalHost().getHostName() + "-" + parameters.getIntParameter("pastry_port");
    String location = parameters.getStringParameter("storage_root_location");
    int diskLimit = parameters.getIntParameter("storage_disk_limit");
    int cacheLimit = parameters.getIntParameter("storage_cache_limit");
    
    stepStart("Starting Immutable Storage");
    immutableStorage = new StorageManagerImpl(FACTORY,
                                              new PersistentStorage(FACTORY, prefix + "-immutable", location, diskLimit),
                                              new LRUCache(new PersistentStorage(FACTORY, prefix + "-cache", ".", diskLimit), cacheLimit));
    stepDone(SUCCESS);
    
    stepStart("Starting Mutable Storage");
    mutableStorage = new StorageManagerImpl(FACTORY,
                                            new PersistentStorage(FACTORY, prefix + "-mutable", location, diskLimit),
                                            new EmptyCache(FACTORY));    
    stepDone(SUCCESS);
    
    stepStart("Starting Pending Message Storage");
    pendingStorage = new StorageManagerImpl(FACTORY,
                                            new PersistentStorage(FACTORY, prefix + "-pending", location, diskLimit),
                                            new EmptyCache(FACTORY));    
    stepDone(SUCCESS);
    
    stepStart("Starting Delivered Message Storage");
    deliveredStorage = new StorageManagerImpl(FACTORY,
                                              new PersistentStorage(FACTORY, prefix + "-delivered", location, diskLimit),
                                              new EmptyCache(FACTORY));    
    stepDone(SUCCESS);
    
    if (parameters.getBooleanParameter("past_use_garbage_collection")) {
      stepStart("Starting Trashcan Storage");
      trashStorage = new StorageManagerImpl(FACTORY,
                                            new PersistentStorage(FACTORY, prefix + "-trash", location, diskLimit),
                                            new EmptyCache(FACTORY));
      stepDone(SUCCESS);
    }
    
    if (parameters.getBooleanParameter("glacier_enable")) {
      FragmentKeyFactory KFACTORY = new FragmentKeyFactory((MultiringIdFactory) FACTORY);
      VersionKeyFactory VFACTORY = new VersionKeyFactory((MultiringIdFactory) FACTORY);
      PastryIdFactory PFACTORY = new PastryIdFactory();

      stepStart("Starting Glacier Storage");
      glacierMutableStorage = new StorageManagerImpl(KFACTORY,
                                              new PersistentStorage(KFACTORY, prefix + "-glacier-mutable", location, diskLimit),
                                              new EmptyCache(KFACTORY));
      glacierImmutableStorage = new StorageManagerImpl(KFACTORY,
                                              new PersistentStorage(KFACTORY, prefix + "-glacier-immutable", location, diskLimit),
                                              new EmptyCache(KFACTORY));
      glacierNeighborStorage = new StorageManagerImpl(FACTORY,
                                              new PersistentStorage(FACTORY, prefix + "-glacier-neighbor", location, diskLimit),
                                              new EmptyCache(FACTORY));
      aggrWaitingStorage = new StorageManagerImpl(VFACTORY,
                                              new PersistentStorage(VFACTORY, prefix + "-aggr-waiting", location, diskLimit),
                                              new EmptyCache(VFACTORY));
      if (parameters.getBooleanParameter("glacier_use_trashcan")) {
        glacierTrashStorage = new StorageManagerImpl(KFACTORY,
                                              new PersistentStorage(KFACTORY, prefix + "-glacier-trash", location, diskLimit),
                                              new EmptyCache(KFACTORY));
      } else {
        glacierTrashStorage = null;
      }
      
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
    String protocol = parameters.getStringParameter("pastry_protocol");
    int protocolId = 0;
    int port = parameters.getIntParameter("pastry_port");
    
    if (protocol.equalsIgnoreCase("wire")) {
      protocolId = DistPastryNodeFactory.PROTOCOL_WIRE;
    } else if (protocol.equalsIgnoreCase("rmi")) {
      protocolId = DistPastryNodeFactory.PROTOCOL_RMI;
    } else if (protocol.equalsIgnoreCase("socket")) {
      protocolId = DistPastryNodeFactory.PROTOCOL_SOCKET;
    } else {
      stepDone(FAILURE, "Unknown protocol " + protocol);
      System.exit(-18);
    }
    
    DistPastryNodeFactory factory = DistPastryNodeFactory.getFactory(new CertifiedNodeIdFactory(port), protocolId, port);
    InetSocketAddress[] bootAddresses = parameters.getInetSocketAddressArrayParameter("pastry_bootstraps");
    InetSocketAddress proxyAddress = null;
    
    if (parameters.getBooleanParameter("pastry_proxy_enable"))
      proxyAddress = parameters.getInetSocketAddressParameter("pastry_proxy");
    
    node = factory.newNode(factory.getNodeHandle(bootAddresses), proxyAddress);
    pastryNode = (PastryNode) node;
    Thread.sleep(3000);
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
  protected void startMultiringNode(Parameters parameters) throws Exception { 
    if (parameters.getBooleanParameter("multiring_enable")) {
      stepStart("Creating Multiring node in ring " + parameters.getStringParameter("multiring_ring_name"));
      node = new MultiringNode(generateRingId(parameters.getStringParameter("multiring_ring_name")), node);
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
    String protocol = parameters.getStringParameter("multiring_global_pastry_protocol");
    int protocolId = 0;
    int port = parameters.getIntParameter("multiring_global_pastry_port");
    
    if (protocol.equalsIgnoreCase("wire")) {
      protocolId = DistPastryNodeFactory.PROTOCOL_WIRE;
    } else if (protocol.equalsIgnoreCase("rmi")) {
      protocolId = DistPastryNodeFactory.PROTOCOL_RMI;
    } else if (protocol.equalsIgnoreCase("socket")) {
      protocolId = DistPastryNodeFactory.PROTOCOL_SOCKET;
    } else {
      stepDone(FAILURE, "Unknown global protocol " + protocol);
      System.exit(-20);
    }
    
    DistPastryNodeFactory factory = DistPastryNodeFactory.getFactory(new CertifiedNodeIdFactory(port), protocolId, port);
    InetSocketAddress[] bootAddresses = parameters.getInetSocketAddressArrayParameter("multiring_global_pastry_bootstraps");
    
    globalNode = factory.newNode(factory.getNodeHandle(bootAddresses), (rice.pastry.NodeId) ((RingId) node.getId()).getId());
    globalPastryNode = (PastryNode) globalNode;
    Thread.sleep(3000);
    stepDone(SUCCESS);
  }     
  
  /**
   * Method which starts up the global multiring node service
   *
   * @param parameters The parameters to use
   */  
  protected void startGlobalMultiringNode(Parameters parameters) throws Exception { 
    stepStart("Creating Multiring node in Global ring");
    globalNode = new MultiringNode(generateRingId(null), globalNode, (MultiringNode) node);
    Thread.sleep(3000);
    stepDone(SUCCESS); 
  }
  
  /**
   * Method which starts up the global ring node, if required
   *
   * @param parameters The parameters to use
   */  
  protected void startGlobalNode(Parameters parameters) throws Exception { 
    if (parameters.getBooleanParameter("multiring_global_enable")) {
      startGlobalPastryNode(parameters);
      startGlobalMultiringNode(parameters);
    }
  } 
  
  /**
   * Method which initializes and starts up the glacier service
   *
   * @param parameters The parameters to use
   */  
  protected void startGlacier(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("glacier_enable")) {
      stepStart("Starting Glacier service");

      String prefix = InetAddress.getLocalHost().getHostName() + "-" + parameters.getIntParameter("pastry_port");
      VersionKeyFactory VFACTORY = new VersionKeyFactory((MultiringIdFactory) FACTORY);

/*      mutablePast = new AggregationImpl(
        node, 
        new GlacierImpl(
          node, glacierMutableStorage, glacierNeighborStorage,
          parameters.getIntParameter("glacier_num_fragments"),
          parameters.getIntParameter("glacier_num_survivors"),
          (MultiringIdFactory)FACTORY, 
          parameters.getStringParameter("application_instance_name") + "-glacier-mutable",
          new GlacierDefaultPolicy(
            new ErasureCodec(
              parameters.getIntParameter("glacier_num_fragments"),
              parameters.getIntParameter("glacier_num_survivors")
            )
          )
        ),  
        mutablePast,
        aggrWaitingStorage,
        "aggregation.param",
        (MultiringIdFactory) FACTORY,
        parameters.getStringParameter("application_instance_name") + "-aggr-mutable"
      ); */

      GlacierImpl immutableGlacier = new GlacierImpl(
        node, glacierImmutableStorage, glacierNeighborStorage,
        parameters.getIntParameter("glacier_num_fragments"),
        parameters.getIntParameter("glacier_num_survivors"),
        (MultiringIdFactory)FACTORY, 
        parameters.getStringParameter("application_instance_name") + "-glacier-immutable",
        new GlacierDefaultPolicy(
          new ErasureCodec(
            parameters.getIntParameter("glacier_num_fragments"),
            parameters.getIntParameter("glacier_num_survivors")
          )
        )
      );
      
      immutableGlacier.setSyncInterval(parameters.getIntParameter("glacier_sync_interval"));
      immutableGlacier.setSyncMaxFragments(parameters.getIntParameter("glacier_sync_max_fragments"));
      immutableGlacier.setRateLimit(parameters.getIntParameter("glacier_max_requests_per_second"));
      immutableGlacier.setNeighborTimeout(parameters.getIntParameter("glacier_neighbor_timeout"));
      immutableGlacier.setTrashcan(glacierTrashStorage);

      AggregationImpl immutableAggregation = new AggregationImpl(
        node, 
        immutableGlacier,  
        immutablePast,
        aggrWaitingStorage,
        "aggregation.param",
        (MultiringIdFactory) FACTORY,
        parameters.getStringParameter("application_instance_name") + "-aggr-immutable"
      );

      immutableAggregation.setFlushInterval(parameters.getIntParameter("aggregation_flush_interval"));
      immutableAggregation.setMaxAggregateSize(parameters.getIntParameter("aggregation_max_aggregate_size"));
      immutableAggregation.setMaxObjectsInAggregate(parameters.getIntParameter("aggregation_max_objects_per_aggregate"));
      immutableAggregation.setRenewThreshold(parameters.getIntParameter("aggregation_renew_threshold"));

      immutablePast = immutableAggregation;

      stepDone(SUCCESS);
    }
  }
  
  /**
   * Method which starts up the local past service
   *
   * @param parameters The parameters to use
   */  
  protected void startPast(Parameters parameters) throws Exception {
    stepStart("Starting Past services");
    
    if (parameters.getBooleanParameter("past_use_garbage_collection")) {
      immutablePast = new GCPastImpl(node, immutableStorage, 
                                     parameters.getIntParameter("past_replication_factor"), 
                                     parameters.getStringParameter("application_instance_name") + "-immutable",
                                     new PastPolicy.DefaultPastPolicy(),
                                     trashStorage);
    } else {
      immutablePast = new PastImpl(node, immutableStorage, 
                                   parameters.getIntParameter("past_replication_factor"), 
                                   parameters.getStringParameter("application_instance_name") + "-immutable");
    }
      
    mutablePast = new PastImpl(node, mutableStorage, 
                               parameters.getIntParameter("past_replication_factor"), 
                               parameters.getStringParameter("application_instance_name") + "-mutable", new PostPastPolicy());
    deliveredPast = new PastImpl(node, deliveredStorage, 
                                 parameters.getIntParameter("past_replication_factor"), 
                                 parameters.getStringParameter("application_instance_name") + "-delivered");
    pendingPast = new DeliveryPastImpl(node, pendingStorage, 
                                       parameters.getIntParameter("past_replication_factor"), 
                                       parameters.getStringParameter("application_instance_name") + "-pending", deliveredPast);
    stepDone(SUCCESS);
  }
  
  /**
   * Method which starts up the local post service
   *
   * @param parameters The parameters to use
   */  
  protected void startPost(Parameters parameters) throws Exception {
    stepStart("Starting POST service");
    post = new PostImpl(node, immutablePast, mutablePast, pendingPast, deliveredPast, address, pair, certificate, caPublic, 
                        parameters.getStringParameter("application_instance_name"), 
                        parameters.getBooleanParameter("post_allow_log_insert"), clone);
    stepDone(SUCCESS);
  }
  
  /**
   * Method which forces a log reinsertion, if desired (deletes all of the local log, so beware)
   *
   * @param parameters The parameters to use
   */  
  protected void startInsertLog(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("post_force_log_reinsert")) {
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
  protected void startFetchLog(Parameters parameters) throws Exception {
    if (parameters.getBooleanParameter("post_proxy_enable") &&
        parameters.getBooleanParameter("post_fetch_log")) {
      int retries = 0;
      
      stepStart("Fetching POST log at " + address.getAddress());
      boolean done = false;
      
      while (!done) {
        ExternalContinuation c = new ExternalContinuation();
        post.getPostLog(c);
        c.sleep();
        
        if (c.exceptionThrown()) { 
          if (retries < parameters.getIntParameter("post_fetch_log_retries")) {
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
  }
  
  protected Parameters start(Parameters parameters) throws Exception {
    initializeParameters(parameters, DEFAULT_PARAMETERS);
    
    startPastryProxy(parameters);

    sectionStart("Initializing Parameters");
    startRedirection(parameters);
    startShutdownHooks(parameters);
    startSecurityManager(parameters);
    startRetrieveCAKey(parameters);
    startRetrieveUser(parameters);
    sectionDone();
    
    sectionStart("Bootstrapping Local Node");
    startCreateIdFactory(parameters);
    startStorageManagers(parameters);
    startPastryNode(parameters);
    sectionDone();
    
    sectionStart("Bootstrapping Multiring Protocol");
    startMultiringNode(parameters);
    startGlobalNode(parameters);
    sectionDone();
    
    sectionStart("Bootstrapping Local Post Applications");
    startPast(parameters);
    startGlacier(parameters);
    startPost(parameters);
    startInsertLog(parameters);
    startFetchLog(parameters);
    sectionDone();
    
    return parameters;
  }
  
  protected void start() {
    try {
      start(new Parameters(PROXY_PARAMETERS_NAME));
    } catch (Exception e) {
      System.err.println("ERROR: Found Exception while start proxy - exiting - " + e);
      e.printStackTrace();
      System.exit(-1);
    }    
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
          }

  public static void main(String[] args) {
    PostProxy proxy = new PostProxy();
    proxy.start();
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
