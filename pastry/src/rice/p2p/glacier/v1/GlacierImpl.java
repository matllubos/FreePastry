package rice.p2p.glacier.v1;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.logging.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v1.*;
import rice.p2p.glacier.v1.messaging.*;
import rice.p2p.multiring.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.PastMessage;
import rice.p2p.replication.*;
import rice.p2p.replication.manager.*;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.dist.DistPastryNode;
import rice.pastry.leafset.LeafSet;
import rice.persistence.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author Andreas Haeberlen
 */
public class GlacierImpl extends PastImpl implements Glacier, Past, Application, ReplicationManagerClient {

  /**
   * DESCRIBE THE FIELD
   */
  protected StorageManager storage;

  /**
   * DESCRIBE THE FIELD
   */
  protected Node node;

  /**
   * DESCRIBE THE FIELD
   */
  protected int numFragments;

  /**
   * DESCRIBE THE FIELD
   */
  protected int numSurvivors;

  /**
   * DESCRIBE THE FIELD
   */
  protected int numInitialFragments;

  /**
   * DESCRIBE THE FIELD
   */
  protected GlacierPolicy policy;

  /**
   * DESCRIBE THE FIELD
   */
  protected ErasureCodec codec;

  /**
   * DESCRIBE THE FIELD
   */
  protected GlacierState state;

  /**
   * DESCRIBE THE FIELD
   */
  protected Hashtable insertList;

  /**
   * DESCRIBE THE FIELD
   */
  protected Hashtable auditList;

  /**
   * DESCRIBE THE FIELD
   */
  protected Hashtable handoffList;

  /**
   * DESCRIBE THE FIELD
   */
  protected Hashtable restoreList;

  /**
   * DESCRIBE THE FIELD
   */
  protected LinkedList stickyQueue;

  /**
   * DESCRIBE THE FIELD
   */
  protected String configDir;

  private final char tiInsert = 1;
  private final char tiStatusCast = 2;
  private final char tiAudit = 3;
  private final char tiRestore = 4;
  private final char tiHandoff = 5;

  private Hashtable timers;

  private MultiringIdFactory factory;

  private boolean insertTimeoutActive;

  private int SECONDS = 1000;
  private int MINUTES = 60 * SECONDS;
  private int HOURS = 60 * MINUTES;

  private int insertTimeout = 30 * SECONDS;
//  private int statusCastInterval = 60*MINUTES;
//  private int statusCastMinDelay = 40*MINUTES;
//  private int statusCastJitter = 20*MINUTES;
  private int statusCastInterval = 10 * MINUTES;
  private int statusCastMinDelay = 3 * MINUTES;
  private int statusCastJitter = 1 * MINUTES;
//  private int statusCastInterval = 24 * HOURS;
//  private int statusCastMinDelay = 2 * HOURS;
//  private int statusCastJitter = 6 * HOURS;

  private int maxConcurrentAudits = 3;
  private int auditTimeout = 20 * SECONDS;
  private int restoreCycle = 20 * SECONDS;
  private int handoffTimeout = 20 * SECONDS;
  private int numIncludePreviousStatusCasts = 1;
  private int maxConcurrentRestores = 100;
  private float maxRestoreAttemptFactor = (float) 2.0;
  private int deadHolderEntryTimeout = (int) (1.2 * statusCastInterval);
  private int uncertainHolderEntryTimeout = (int) (3.2 * statusCastInterval);
  private int certainHolderEntryTimeout = (int) (4.2 * statusCastInterval);
  private int maxAuditAttempts = 2;
  private int stickyPacketLifetime = 3;
  private float maxRestoreFromFragmentFactor = (float) 2.0;
  private String configFileName = ".glacier-config";
  private String stateFileName = ".glacier-state";
  private Date nextStatusCastDate;

  /**
   * Constructor for GlacierImpl.
   *
   * @param node The node on which this Glacier instance is to be run
   * @param configDir Directory where the configuration files are stored
   * @param pastManager The storage manager used by PAST (contains full replicas)
   * @param glacierManager The storage manager used by Glacier (contains fragments)
   * @param replicas The number of full replicas per object
   * @param numFragments The number of different fragments per object
   * @param numSurvivors The minimum number of fragments from which the object can be recovered
   * @param factory Used to create IDs
   * @param instance Unique identifier for this instance of Glacier
   */
  public GlacierImpl(Node node, String configDir, StorageManager pastManager, StorageManager glacierManager, GlacierPolicy policy, int replicas, int numFragments, int numSurvivors, MultiringIdFactory factory, String instance) {
    super(node, pastManager, replicas, instance);

    /*
     *  Initialize internal variables
     */
    this.storage = glacierManager;
    this.node = node;
    this.policy = policy;
    this.numFragments = numFragments;
    this.numSurvivors = numSurvivors;
    this.timers = new Hashtable();
    this.insertList = new Hashtable();
    this.auditList = new Hashtable();
    this.handoffList = new Hashtable();
    this.restoreList = new Hashtable();
    this.stickyQueue = new LinkedList();
    this.configDir = configDir;
    this.state = null;
    this.numInitialFragments = 2 * numSurvivors;
    this.insertTimeoutActive = false;
    this.codec = new ErasureCodec(numFragments, numSurvivors);
    this.factory = factory;

    /*
     *  Read Glacier's current state from the configuration file. If the file is
     *  not found, start with a blank configuration.
     */
    File configDirF = new File(this.configDir);
    if (!configDirF.exists()) {
      configDirF.mkdir();
    }

    String configFilePath = configDir + "/" + configFileName;
    File configFile = new File(configFilePath);
    if (configFile.exists()) {
      try {
        FileInputStream fis = new FileInputStream(configFile);
        ObjectInputStream ois = new ObjectInputStream(fis);
        log("Reading configuration from " + configFilePath);
        state = (GlacierState) ois.readObject();
        ois.close();
      } catch (Exception e) {
        System.err.println("GlacierImpl cannot read configuration file: " + configFilePath);
        e.printStackTrace();
        System.exit(1);
      }
    }

    if (state == null) {
      state = new GlacierState();
    }

    scheduleNextStatusCast();
  }

  /**
   * Gets the State attribute of the GlacierImpl object
   *
   * @return The State value
   */
  public GlacierState getState() {
    return state;
  }

  /**
   * Gets the NumFragments attribute of the GlacierImpl object
   *
   * @return The NumFragments value
   */
  public int getNumFragments() {
    return numFragments;
  }

  /**
   * Gets the NextStatusCastDate attribute of the GlacierImpl object
   *
   * @return The NextStatusCastDate value
   */
  public Date getNextStatusCastDate() {
    return nextStatusCastDate;
  }

  /**
   * Restarts Glacier with an existing set of fragments (for debugging)
   */
  public void refreeze() {
    Date now = new Date();

    /*
     *  In the event of a refreeze (for testing purposes), we do
     *  the following:
     *  1) Clear the history
     *  2) Mark all local fragments as newly acquired
     *  3) Set all remote fragments to Uncertain
     *  This ensures that the system can be restarted after
     *  a global crash with partial data loss; the next series
     *  of status casts will change the surviving fragments
     *  back to Certain.
     */
    log("REFREEZING");
    state.history = new LinkedList();

    Enumeration enu = state.fileList.elements();
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo) enu.nextElement();

      for (int i = 0; i < numFragments; i++) {
        for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j]) {
            if (!fileInfo.holderDead[i][j]) {
              if (fileInfo.holderId[i][j] == null) {
                recordNewFragmentEvent(new FragmentKey(fileInfo.key, i));
              } else {
                fileInfo.holderCertain[i][j] = false;
              }
              fileInfo.lastHeard[i][j] = now;
            } else {
              fileInfo.holderKnown[i][j] = false;
            }
          }
        }
      }
    }

    /*
     *  Also, reset the timer on all the known fragment holders
     *  to prevent fragments from being deleted just because their
     *  holder seems to have failed. If a holder is really down,
     *  its entry will eventually expire again.
     */
    enu = state.holderList.elements();
    while (enu.hasMoreElements()) {
      HolderInfo holderInfo = (HolderInfo) enu.nextElement();
      holderInfo.lastHeardOf = now;
    }
  }

  /**
   * Get an authenticator for an object that is to be stored in Glacier.
   * Authenticators are typically included in the storage manifest;
   * they are extracted by the GlacierPolicy object.
   *
   * @param obj The object that is to be stored in Glacier.
   */

  public Authenticator getAuthenticator(Serializable obj)
  {
    /* The authenticator includes a SHA-1 hash of the object itself.
       Since the hash can only be computed over a byte array,
       the object must be serialized first. */
  
    log("Serialize object: " + obj);
    byte bytes[] = null;
    try {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);

      objectStream.writeObject(obj);
      objectStream.flush();

      bytes = byteStream.toByteArray();
    } catch (IOException ioe) {
      System.err.println(ioe);
      return null;
    }

    /* We also need hashes of the fragments. */

    log("Create fragments: " + obj);
    Fragment[] fragments = codec.encode(bytes);
    log("Completed: " + obj);

    if (fragments == null)
      return null;

    /* Get the SHA-1 hash object. */

    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      System.err.println("No SHA support!");
      return null;
    }

    /* Compute the hash values. */

    byte[][] fragmentHash = new byte[numFragments][];
    for (int i = 0; i < numFragments; i++) {
      md.reset();
      md.update(fragments[i].getPayload());
      fragmentHash[i] = md.digest();
    }

    byte[] objectHash = null;
    md.reset();
    md.update(bytes);
    objectHash = md.digest();

    /* Return the new authenticator. Note that the version number is
       set to zero by default; it is the application's responsibility
       to update it. */

    return new Authenticator(objectHash, fragmentHash, 0);
  }

  /**
   * Insert an object into PAST and Glacier. This method is included for
   * backward compatibility; it uses a default manifest.
   *
   * @param obj The object that is to be stored
   * @param command Called when the object has been successfully inserted into PAST
   */
  
  public void insert(final PastContent obj, final Continuation command) {
    try {
      insert(obj, null, command);
    } catch (InvalidManifestException ime) {
      warn("Invalid manifest during legacy insert?!?");
    }  
  }

  /**
   * Insert an object into PAST and Glacier.
   *
   * @param obj The object that is to be stored
   * @param manifest The storage manifest, which contains an authenticator for the object and its fragments.
   * @param command Called when the object has been successfully inserted into PAST
   */

  public void insert(final PastContent obj, final StorageManifest manifest, final Continuation command) throws InvalidManifestException{
    log("Insert " + obj + " " + command + " (mutable=" + obj.isMutable() + ")");

    super.insert(obj, command);

    if (!obj.isMutable()) {

      /* Start a timer to handle lost InsertMessages */

      if (!insertTimeoutActive) {
        insertTimeoutActive = true;
        addTimer(insertTimeout, tiInsert);
      }

      /* Compute the fragments */

      log("Encode object: " + obj);
      Fragment[] fragments = codec.encodeObject(obj);
      log("Completed: " + obj);

      if (fragments != null) {
        StorageManifest effectiveManifest;
        int effectiveVersion;

        /* If no manifest is provided, use the default manifest */

        if (manifest == null) {
          effectiveVersion = 0;
          effectiveManifest = getAuthenticator(obj);
        } else {
          Authenticator auth = policy.extractAuthenticator(obj.getId(), manifest);
          if (auth == null)
            throw new InvalidManifestException("Cannot extract authenticator with current policy");
            
          effectiveVersion = auth.getVersion();
          effectiveManifest = manifest;
        }

        /* Add an entry to the list of pending insertions */

        VersionKey vkey = new VersionKey(obj.getId(), effectiveVersion);
        log("Creating new ILE at " + vkey);

        InsertListEntry ile = new InsertListEntry(
          vkey, fragments, effectiveManifest,
          System.currentTimeMillis() + insertTimeout - 100,
          numInitialFragments, getLocalNodeHandle(), 2
        );

        if (!insertList.containsKey(vkey)) {
          insertList.put(vkey, ile);
          syncState();

          /* Send QueryMessages for some of the fragments. These messages
             serve two purposes: First, they are routed to the nodes that are
             closest to the fragments' final locations; thus, we can learn
             the positions of the prospective holders from the corresponding
             ResponseMessages. Second, we can use the responses to find out
             whether the object already exists. */

          for (int i = 0; i < numInitialFragments; i++) {
            Id fragmentLoc = getFragmentLocation(obj.getId(), i);
            endpoint.route(
              fragmentLoc,
              new GlacierQueryMessage(getUID(), new FragmentKey(vkey, i), getLocalNodeHandle(), fragmentLoc),
              null
              );
          }
        } else {
          warn("Immutable object inserted a second time: " + vkey);
        }
      } else {
        warn("Cannot encode object -- too large?");
      }
    }
  }

  /**
   * This method is part of the KBR API. It is called whenever the leaf set
   * of a node changes.
   *
   * @param handle Handle of the node that is being added to (or removed from) the leaf set
   * @param joined True if the node is being added, false if it is being removed
   */
  public void update(NodeHandle handle, boolean joined) {
    ListIterator sqi = stickyQueue.listIterator(0);

    log("UPDATE " + handle + " joined=" + joined);

    /* When nodes leave, we do nothing. */

    if (!joined) {
      return;
    }

    /* When a node joins, we scan our list of sticky messages and deliver
       any messages that are addressed to the new node. */

    while (sqi.hasNext()) {
      GlacierStatusMessage lsm = (GlacierStatusMessage) sqi.next();
      log("STICKY checking " + lsm.getDestination() + "/" + handle.getId() + " -- " + lsm);
      if (lsm.getDestination().equals(handle.getId())) {
        log("STICKY delivering " + lsm);
        endpoint.route(
          null,
          lsm,
          handle
          );

        sqi.remove();
      }
    }
  }

  /**
   * This method is part of the KBR API. It is called whenever a new message
   * is delivered to the local node.
   *
   * @param id Destination of the message.
   * @param message The message that is being delivered.
   */

  public void deliver(Id id, Message message) {

    /* PAST handles its own messages */
  
    if (message instanceof PastMessage) {
      try {
        super.deliver(id, message);
      } catch (Exception e) {
        System.out.println("PAST reports Exception in deliver(): "+e);
        e.printStackTrace();
      }
      return;
    }

    final GlacierMessage msg = (GlacierMessage) message;
    log("Received message " + msg + " with destination " + id + " from " + msg.getSource().getId());

    if (msg instanceof GlacierQueryMessage) {

      /* When a QueryMessage arrives, we check whether we have the fragment
         with the corresponding key and then send back a ResponseMessage. */

      GlacierQueryMessage gqm = (GlacierQueryMessage) msg;
      log("Queried for " + gqm.getKey());

      boolean haveIt = storage.exists(gqm.getKey());
      if (haveIt) {
        log("EXISTS: " + gqm.getKey());
      } else {
        log("DOES NOT EXIST: " + gqm.getKey());
      }

      endpoint.route(
        null,
        new GlacierResponseMessage(gqm.getUID(), gqm.getKey(), haveIt, getLocalNodeHandle(), gqm.getSource().getId()),
        gqm.getSource()
        );
    } else if (msg instanceof GlacierTimeoutMessage) {
    
      /* TimeoutMessages are generated by the local node when a 
         timeout expires. */
    
      GlacierTimeoutMessage gtm = (GlacierTimeoutMessage) msg;
      timerExpired((char) gtm.getUID());
      syncState();
      return;
    } else if (msg instanceof GlacierResponseMessage) {
    
      /* ResponseMessages are sent in response to QueryMessages; they indicate
         whether the sender has a certain fragment or not. There is a number
         of reasons why we might have sent that QueryMessage; we can find
         the reason by looking up the key in our local lists. */
    
      GlacierResponseMessage grm = (GlacierResponseMessage) msg;
      log("Response for " + grm.getKey() + " (" + grm.getHaveIt() + ")");

      /* If the key is in the insertList, we are trying to find holders for
         an object that is being inserted. The sender of the ResponseMessage
         is one of these holders; we record its nodeHandle and check whether
         there are any other holders missing. */

      if (insertList.containsKey(grm.getKey().getVersionKey())) {
        InsertListEntry ile = (InsertListEntry) insertList.get(grm.getKey().getVersionKey());
        if (grm.getKey().getFragmentID() < numInitialFragments) {
          ile.holderKnown[grm.getKey().getFragmentID()] = true;
          ile.holder[grm.getKey().getFragmentID()] = grm.getSource();
          if (grm.getHaveIt()) {
            ile.receiptReceived[grm.getKey().getFragmentID()] = true;
          }

          insertStep(ile);
          syncState();
        } else {
          warn("Fragment ID too large in insert response");
        }
        
        return;
      }

      /* If the key is in the handoffList, we are trying to hand off the
         corresponding object to another node, either because we have 
         recovered it, or because the other node is closer to the fragment's
         final destination. */

      if (handoffList.containsKey(grm.getKey())) {
        HandoffListEntry hle = (HandoffListEntry) handoffList.get(grm.getKey());

        if (!grm.getHaveIt()) {
          FileInfo fileInfo = (FileInfo) state.fileList.get(grm.getKey().getVersionKey());

          /* The destination does not yet have the fragment... good! We send
             him an InsertMessage, along with everything we know about the
             other fragments. */

          int numKnownHolders = 0;
          for (int i = 0; i < numFragments; i++) {
            for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
              if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] &&
                ((fileInfo.holderId[i][j] != null) || (i != grm.getKey().getFragmentID()))) {
                numKnownHolders++;
              }
            }
          }

          Id[] knownHolders = new Id[numKnownHolders];
          int[] knownFragmentIDs = new int[numKnownHolders];
          boolean[] knownHolderCertain = new boolean[numKnownHolders];
          int index = 0;
          for (int i = 0; i < numFragments; i++) {
            for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
              if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] &&
                ((fileInfo.holderId[i][j] != null) || (i != grm.getKey().getFragmentID()))) {
                knownHolders[index] = fileInfo.holderId[i][j];
                knownHolderCertain[index] = fileInfo.holderCertain[i][j];
                if (knownHolders[index] == null) {
                  knownHolders[index] = getLocalNodeHandle().getId();
                  assume(knownHolders[index] != null);
                }
                knownFragmentIDs[index] = i;
                index++;
              }
            }
          }

          endpoint.route(
            null,
            new GlacierInsertMessage(
            getUID(), grm.getKey(),
            hle.manifest, hle.fragment,
            knownHolders, knownFragmentIDs, knownHolderCertain,
            getLocalNodeHandle(), grm.getSource().getId()
            ),
            grm.getSource()
            );
        } else {
          
          /* The destination already has the fragment we were trying to 
             send him. Our next step depends on the reason for the handoff. */

          handoffList.remove(grm.getKey());

          if (!hle.isRestoredFragment) {
            warn("Collision during handoff -- deleting my own copy");

            /* If this happened during migration, the fragment is already
               stored locally, so we must unstore it and delete the
               corresponding metadata. */

            FileInfo fileInfo = (FileInfo) state.fileList.get(grm.getKey().getVersionKey());
            for (int k = 0; k < FileInfo.maxHoldersPerFragment; k++) {
              if (fileInfo.holderKnown[hle.skey.getFragmentID()][k] &&
                (fileInfo.holderId[hle.skey.getFragmentID()][k] == null)) {
                fileInfo.holderDead[hle.skey.getFragmentID()][k] = true;
              }
            }

            final FragmentKey sk3 = grm.getKey();
            storage.unstore(sk3,
              new Continuation() {
                public void receiveResult(Object o) {
                  log("Successfully unstored " + sk3 + " after collision");
                }

                public void receiveException(Exception e) {
                  warn("receiveException(" + e + ") during collision -- unexpected, ignored (sk3=" + sk3 + ")");
                }
              });
          } else {
            warn("He already has what I was going to restore!!!");
            
            /* If this happened during recovery, the fragment is not yet
               stored locally. However, we must update our database of
               fragment holders; otherwise, we might try to recover the
               same fragment during the next recovery cycle. */
            
            addFragmentNews(
              grm.getKey(), grm.getSource().getId(),
              HistoryEvent.evtAcquired, null
              );
          }
        }
        return;
      }
    
      /* If the key was not found at all, the message was either misrouted
         or is a duplicate. */

      warn("Unexpected GlacierResponseMessage");
    } else if (msg instanceof GlacierInsertMessage) {
      final GlacierInsertMessage gim = (GlacierInsertMessage) msg;
      log("Insert request for " + gim.getKey());

      /* InsertMessages are sent when 
           a) the initial set of fragments for a new object is being inserted,
           b) a fragment is being handed off to a closer node, or
           c) a recovered fragment is stored */

      if (!storage.exists(gim.getKey())) {
        log("STORING " + gim.getKey());

        /* If we do not have a copy of this fragment yet, we store it
           locally. Also, we add the information that came with the
           fragment (about other holders) to our local database. */

        markNewFragmentStored(gim.getKey(), gim.getStorageManifest());
        assume(gim.knownHolderFragmentID != null);
        assume(gim.knownHolder != null);
        for (int i = 0; i < gim.knownHolder.length; i++) {
          assume(gim.knownHolder[i] != null);
          addFragmentNews(
            gim.getKey().getPeerKey(gim.knownHolderFragmentID[i]), gim.knownHolder[i],
            (gim.knownHolderCertain[i]) ? HistoryEvent.evtAcquired : HistoryEvent.evtNewHolder,
            null
            );
        }

        /* Since the sender is not allowed to retain a copy of the fragment,
           we can safely remove any information from the local database
           that says he has one. Even CONFIRMED entries must be deleted! */

        FileInfo fileInfo = (FileInfo) state.fileList.get(gim.getKey().getVersionKey());
        int fragmentID = gim.getKey().getFragmentID();
        if (fileInfo != null) {
          for (int i = 0; i < FileInfo.maxHoldersPerFragment; i++) {
            if (fileInfo.holderKnown[fragmentID][i] &&
              !fileInfo.holderDead[fragmentID][i] &&
              (fileInfo.holderId[fragmentID][i] != null)) {
              if (fileInfo.holderId[fragmentID][i].equals(gim.getSource().getId())) {
                log("KILLED PREVIOUS HOLDER");
                fileInfo.holderDead[fragmentID][i] = true;
              }
            }
          }
        }

        /* Only after the fragment has been successfully stored do we
           reply with a ReceiptMessage! */

        storage.store(gim.getKey(), null, gim.getFragment(),
          new Continuation() {
            public void receiveResult(Object o) {
              endpoint.route(
                null,
                new GlacierReceiptMessage(gim.getUID(), gim.getKey(), getLocalNodeHandle(), gim.getSource().getId()),
                gim.getSource()
                );
            }

            public void receiveException(Exception e) {
              warn("receiveException(" + e + ") during GlacierInsert -- unexpected, ignored (key=" + gim.getKey() + ")");
            }
          });

        syncState();
      } else {
        log("Collision on insert for " + gim.getKey() + ", sending receipt only");

        /* We already have a copy of this fragment, probably because there
           was a race condition (two nodes recovered the same fragment).
           We simply send a ReceiptMessage and discard the second copy. */

        endpoint.route(
          null,
          new GlacierReceiptMessage(gim.getUID(), gim.getKey(), getLocalNodeHandle(), gim.getSource().getId()),
          gim.getSource()
          );
      }

      return;
    } else if (msg instanceof GlacierReceiptMessage) {
      GlacierReceiptMessage grm = (GlacierReceiptMessage) msg;
      log("Receipt for " + grm.getKey());

      /* ReceiptMessages are sent in response to InsertMessages, so we must
         have tried to insert, recover or hand off a fragment to another node */

      if (insertList.containsKey(grm.getKey().getVersionKey())) {
        InsertListEntry ile = (InsertListEntry) insertList.get(grm.getKey().getVersionKey());
        if (grm.getKey().getFragmentID() >= numInitialFragments) {
          panic("Fragment ID too large in insert receipt");
        }

        /* If the fragment belongs to a new object that is being inserted,
           we update the entry in the insert list. Once we have receipts for
           all the fragments we tried to insert, we can remove the entry
           (in insertStep) */

        assume(ile.holderKnown[grm.getKey().getFragmentID()]);
        ile.receiptReceived[grm.getKey().getFragmentID()] = true;
        insertStep(ile);
        syncState();
        return;
      }

      if (handoffList.containsKey(grm.getKey())) {
        HandoffListEntry hle = (HandoffListEntry) handoffList.get(grm.getKey());

        /* If the fragment is being recovered or handed off, we must
           update our local database (the sender of the ReceiptMessage is
           the new holder). Also, if the fragment is being handed off,
           it is still stored locally, so we must remove it from the
           storage manager */

        if (!hle.isRestoredFragment) {
          int fragmentID = grm.getKey().getFragmentID();
          log("Handoff successful for " + grm.getKey());
          recordMigratedFragmentEvent(grm.getKey(), grm.getSource().getId());

          FileInfo fileInfo = (FileInfo) state.fileList.get(grm.getKey().getVersionKey());
          for (int i = 0; i < FileInfo.maxHoldersPerFragment; i++) {
            if (fileInfo.holderKnown[fragmentID][i] &&
              !fileInfo.holderDead[fragmentID][i] &&
              (fileInfo.holderId[fragmentID][i] == null)) {
              fileInfo.holderKnown[fragmentID][i] = false;
            }
          }

          handoffList.remove(grm.getKey());
          addFragmentNews(grm.getKey(), grm.getSource().getId(), HistoryEvent.evtAcquired, grm.getSource().getId());

          final FragmentKey sk3 = grm.getKey();
          storage.unstore(sk3,
            new Continuation() {
              public void receiveResult(Object o) {
                log("Successfully unstored " + sk3);
              }

              public void receiveException(Exception e) {
                warn("receiveException(" + e + ") during handoff -- unexpected, ignored (sk3=" + sk3 + ")");
              }
            });
        } else {
          log("Restore successful for " + grm.getKey());
          handoffList.remove(grm.getKey());
          recordMigratedFragmentEvent(grm.getKey(), grm.getSource().getId());
          addFragmentNews(grm.getKey(), grm.getSource().getId(), HistoryEvent.evtAcquired, grm.getSource().getId());
        }

        return;
      }

      warn("Unexpected GlacierReceiptMessage -- discarded");
      return;
    } else if (msg instanceof GlacierStickyMessage) {
      GlacierStickyMessage gsm = (GlacierStickyMessage) msg;
      log("STICKY Received " + gsm);

      /* StickyMessages encapsulate other messages that cannot be delivered
         at the moment because the destination is offline. We check our
         delivery queue to see if we already have this packet or an even
         newer one (packets are identified by source/destination). Older
         packets, i.e. ones with lower sequence numbers, are replaced. */

      GlacierStatusMessage esm = (GlacierStatusMessage) gsm.message;
      ListIterator sqi = stickyQueue.listIterator(0);
      esm.remainingLifetime = stickyPacketLifetime;

      while (sqi.hasNext()) {
        GlacierStatusMessage lsm = (GlacierStatusMessage) sqi.next();
        if (lsm.getSource().getId().equals(esm.getSource().getId()) && lsm.getDestination().equals(esm.getDestination())) {
          if (lsm.sequenceNo >= esm.sequenceNo) {
            log("STICKY Already got " + lsm);
            return;
          }

          log("STICKY Replacing " + lsm + " with " + esm);
          sqi.remove();
          stickyQueue.addLast(esm);
          return;
        }
      }

      log("STICKY First message of that type");
      stickyQueue.addLast(esm);
      return;
    } else if (msg instanceof GlacierStatusMessage) {
      GlacierStatusMessage gsm = (GlacierStatusMessage) msg;
      log("Received status report #" + gsm.sequenceNo + " from " + gsm.getSource().getId() + " (" + gsm.events.length + " events)");

      /* StatusMessages are sent during status casts. They are always
         addressed to specific nodes, i.e. their destinationID is equal
         to the nodeID of the target node. */

      if (!gsm.getDestination().equals(getLocalNodeHandle().getId())) {
        log("STICKY received status cast for " + gsm.getDestination() + ", forwarding...");

        /* The message was not intended for us, but still, it was delivered
           here, so the destination must be offline at the moment. 
           We wrap the message in a StickyMessage and send it to all our
           leaf set members. */

        LeafSet leafSet;
        if (node instanceof MultiringNode) {
          leafSet = ((DistPastryNode) ((MultiringNode) node).getNode()).getLeafSet();
        } else {
          leafSet = ((PastryNode) node).getLeafSet();
        }

        for (int k = 0; k < leafSet.size(); k++) {
          if (leafSet.get(k) != null) {
            Id nhId = leafSet.get(k).getId();
            if (node instanceof MultiringNode) {
              nhId = factory.buildRingId(((MultiringNode) node).getRingId(), nhId);
            }

            log("STICKY forwarding to " + nhId);
            endpoint.route(
              null,
              new GlacierStickyMessage(getUID(), gsm.getSource().getId(), gsm, getLocalNodeHandle(), nhId),
              leafSet.get(k)
              );
          }
        }

        return;
      }

      /* The message was intended for us */

      HolderInfo holderInfo = (HolderInfo) state.holderList.get(gsm.getSource().getId());
      if (holderInfo == null) {
        warn("I don't know this guy");
        return;
      }

      /*
       *  If it's a full report, we should probably make all references
       *  to this holder uncertain here, so that we resynch completely
       *  after a packet loss.
       */
      for (int i = 0; i < gsm.events.length; i++) {
        if (gsm.events[i].sequenceNo > holderInfo.lastReceivedSequenceNo) {
          addFragmentNews(gsm.events[i].key, gsm.events[i].holder, gsm.events[i].type, gsm.getSource().getId());
        }
      }

      /* All the CONFIRMED fragments that were not mentioned must be
         refreshed so that they do not expire */

      refreshConfirmedFragmentsFor(gsm.getSource().getId());

      /* Update the entry in our holderSet */

      holderInfo.lastAckedSequenceNo = gsm.ackSequenceNo;
      holderInfo.lastHeardOf = new Date();
      if (!gsm.isFullList && (gsm.sequenceNo > (holderInfo.lastReceivedSequenceNo + numIncludePreviousStatusCasts))) {
        holderInfo.lastReceivedSequenceNo = -1;
        warn("Lost too many status packets... resynching");
      } else {
        log("OK. Going from sequence no #" + holderInfo.lastReceivedSequenceNo + " to #" + gsm.sequenceNo);
        holderInfo.lastReceivedSequenceNo = gsm.sequenceNo;
      }
    } else if (msg instanceof GlacierFetchMessage) {
      final GlacierFetchMessage gfm = (GlacierFetchMessage) msg;
      final StorageManager pastStore = super.storage;
      log("Received fetch for " + gfm.getKey());

      /* FetchMessages are sent during recovery to retrieve a fragment from
         another node. They can be answered a) if the recipient has a copy
         of the fragment, or b) if the recipient has a full replica of
         the object. In the second case, the fragment is created on-the-fly */

      storage.getObject(gfm.getKey(),
        new Continuation() {
          public void receiveResult(Object o) {
            if (o != null) {
              log("Fragment found, returning...");
              endpoint.route(
                null,
                new GlacierDataMessage(
                getUID(), gfm.getKey(),
                (Fragment) o,
                getLocalNodeHandle(), gfm.getSource().getId()
                ),
                gfm.getSource()
                );
            } else {
              log("Fragment not found - but maybe we have the original?");
              pastStore.getObject(gfm.getKey().getVersionKey().getId(),
                new Continuation() {
                  public void receiveResult(Object o) {
                    if (o != null) {
                      log("Original found. Recoding...");
                      Fragment[] frags = codec.encodeObject((Serializable) o);
                      log("Fragments recoded ok. Returning fragment...");
                      endpoint.route(
                        null,
                        new GlacierDataMessage(
                        getUID(), gfm.getKey(),
                        frags[gfm.getKey().getFragmentID()],
                        getLocalNodeHandle(), gfm.getSource().getId()
                        ),
                        gfm.getSource()
                        );
                    } else {
                      log("Original not found either");
                    }
                  }

                  public void receiveException(Exception e) {
                    warn("storage.getObject(" + gfm.getKey() + ") returned exception " + e);
                    e.printStackTrace();
                  }
                });
            }
          }

          public void receiveException(Exception e) {
            warn("Fetch(" + gfm.getKey() + ") returned exception " + e);
          }
        });
    } else if (msg instanceof GlacierDataMessage) {
      final GlacierDataMessage gdm = (GlacierDataMessage) msg;
      RestoreListEntry rle = (RestoreListEntry) restoreList.get(gdm.getKey().getVersionKey());
      
      /* DataMessages are sent in response to FetchMessages; they contain
         the requested fragment. By itself, Glacier only requests fragments
         from other nodes during recovery. */
      
      if (rle != null) {
        if (rle.status == RestoreListEntry.stWaitingForPrimary) {
        
          /* If the primary replica sent us a copy of the fragment,
             we can reinsert it right away */
        
          log("Preparing for handoff: " + gdm.getKey());

          /*
           *  XXX check against manifest here! check correct fragment!
           */
          handoffList.put(rle.key,
            new HandoffListEntry(
            rle.key, getFragmentLocation(rle.key.getVersionKey().getId(), rle.key.getFragmentID()), true, gdm.getFragment(), rle.fileInfo.manifest
            )
            );
          restoreList.remove(gdm.getKey().getVersionKey());
          return;
        } else if (rle.status == RestoreListEntry.stCollectingFragments) {
        
          /* At this point, the primary has already been contacted, but
             there was no response, so we are collecting other fragments
             now. If we have enough fragments, we can recover the object,
             otherwise we must request more fragments */
        
          rle.haveFragment[gdm.getKey().getFragmentID()] = gdm.getFragment();
          log("RESTORE got another puzzle piece: " + rle.key + ", now have " + rle.numFragmentsAvailable() + "/" + numSurvivors);
          if (rle.numFragmentsAvailable() >= numSurvivors) {
            log("RESTORE enough puzzle pieces, ready to recode " + rle.key);
            rle.status = RestoreListEntry.stRecoding;
          }
          return;
        } else {
          warn("Unknown RLE status " + rle.status + " when receiving DataMessage");
        }
      }

      warn("Unexpected GlacierDataMessage -- discarded");
      return;
    } else {
      panic("GLACIER ERROR - Received message " + msg + " of unknown type.");
    }
  }

  /**
   * Determines the point in the ring where a particular fragment should
   * be stored. 
   *
   * @param objectKey Key of the original object (from PAST)
   * @param fragmentNr Fragment number (0..n-1)
   * @return The location of the fragment
   */
  private Id getFragmentLocation(Id objectKey, int fragmentNr) {
    if (objectKey instanceof rice.pastry.Id) {
      rice.pastry.Id realId = (rice.pastry.Id) objectKey;
      double f = 0;
      double d;

      /* Convert the objectID to a floating point value between 0 and 1 */

      d = 0.5;
      for (int i = 0; i < realId.IdBitLength; i++) {
        if (realId.getDigit((realId.IdBitLength - 1 - i), 1) > 0) {
          f += d;
        }
        d /= 2;
      }

      /* Apply the placement function */

      f += (1.0 + fragmentNr) / (numFragments + 1.0);
      while (f >= 1) {
        f -= 1;
      }

      /* Convert the floating point value back to an ID */

      rice.pastry.Id result = rice.pastry.Id.build();
      d = 0.5;
      for (int i = 0; i < realId.IdBitLength; i++) {
        if (f >= d) {
//          result.setBit((realId.IdBitLength - 1 - i), 1);
panic("setBit disabled");
          f -= d;
        }
        d /= 2;
      }

      return result;
    } else {
      RingId rok = (RingId) objectKey;
      return factory.buildRingId(rok.getRingId(), getFragmentLocation(rok.getId(), fragmentNr));
    }
  }

  /**
   * This method is used internally by Glacier to check assertions
   *
   * @param val The assertion fails if this value is false
   * @exception Error Thrown if the assertion fails
   */
  private void assume(boolean val) throws Error {
    if (!val) {
      try {
        throw new Error("assertion failed");
      } catch (Error e) {
        warn("Assertion failed: " + e);
      }
    }
  }

  /**
   * This method is called when Glacier encounters a fatal error
   *
   * @param s Message describing the error
   * @exception Error Terminates the program
   */
  private void panic(String s) throws Error {
    System.err.println("PANIC: " + s);
    throw new Error("Panic");
  }

  /**
   * Checks whether all the fragments mentioned in the database are still
   * stored locally. Used for debugging.
   */
  private void checkStorage() {
    Enumeration enu = state.fileList.elements();
    while (enu.hasMoreElements()) {
      FileInfo fi = (FileInfo) enu.nextElement();
      for (int i = 0; i < numFragments; i++) {
        for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
          if (fi.holderKnown[i][j] && !fi.holderDead[i][j] && (fi.holderId[i][j] == null)) {
            final FragmentKey sk = new FragmentKey(fi.key, i);

            storage.getObject(sk,
              new Continuation() {
                public void receiveResult(Object o) {
                  if (o != null) {
                    if (o instanceof Fragment) {
                      log("Fragment " + sk + " found in local storage");
                    } else {
                      log("Fragment " + sk + " has wrong type");
                    }
                  } else {
                    log("Fragment " + sk + " not found in local storage");
                  }
                }

                public void receiveException(Exception e) {
                  panic("Fetch returned exception " + e);
                }
              });
          }
        }
      }
    }
  }

  /**
   * Determine when the next status cast should happen, and set a timer 
   */
  private void scheduleNextStatusCast() {
    Calendar cal = Calendar.getInstance();
    Random rand = new Random();
    long nowMS;
    long lastStatusCastMS;
    long delayMS;

    /*
     *  Convert the Date values to milliseconds so that we can compute
     *  differences easily
     */
    cal.setTime(new Date());
    nowMS = cal.getTimeInMillis();
    cal.setTime(state.lastStatusCast);
    lastStatusCastMS = cal.getTimeInMillis();

    /*
     *  The next status cast should happen after statusCastInterval
     *  milliseconds, plus or minus a certain jitter term (to prevent
     *  synchronization effects)
     */
    delayMS = (lastStatusCastMS + statusCastInterval) - nowMS;
    delayMS += (rand.nextInt(2 * statusCastJitter) - statusCastJitter);

    /*
     *  There must be a minimum delay between two status casts
     */
    if (delayMS < statusCastMinDelay) {
      delayMS = statusCastMinDelay;
    }

    /*
     *  Schedule a TimeoutMessage as a reminder for the next status cast
     */
    addTimer((int) delayMS, tiStatusCast);
    cal.setTimeInMillis(nowMS + delayMS);
    nextStatusCastDate = cal.getTime();

    log("Scheduling next status cast at " + nextStatusCastDate + " (in " + delayMS + " msec)");
  }

  /**
   * Writes the database out to stable storage, so that it can be recovered
   * after a crash. Also writes a plain-text version for debugging purposes.
   */
  private void syncState() {
    /*
     *  Write the current state to the configuration file
     */
    String configFilePath = configDir + "/" + configFileName;
    try {
      FileOutputStream fos = new FileOutputStream(configFilePath);
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      oos.writeObject(state);
      oos.close();
    } catch (IOException ioe) {
      System.err.println("GlacerImpl cannot write to its configuration file: " + configFilePath + " (" + ioe + ")");
      ioe.printStackTrace();
      System.exit(1);
    }

    /*
     *  Write a plain-text version for debugging purposes
     */
    String dumpFileName = configDir + "/" + stateFileName;
    try {
      PrintStream dumpFile = new PrintStream(new FileOutputStream(dumpFileName));
      Enumeration enu = state.fileList.elements();

      dumpFile.println("FileList at " + getLocalNodeHandle().getId() + " (at " + (new Date()) + "):");
      while (enu.hasMoreElements()) {
        FileInfo fileInfo = (FileInfo) enu.nextElement();

        dumpFile.println(" - File " + fileInfo.key);
        for (int i = 0; i < numFragments; i++) {
          for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
            if (fileInfo.holderKnown[i][j]) {
              String holderInfo = "(this node)";
              if (fileInfo.holderId[i][j] != null) {
                holderInfo = "" + fileInfo.holderId[i][j];
              }

              dumpFile.println("    * Fragment " + i + " at " + holderInfo
                + (fileInfo.holderCertain[i][j] ? " certainly " : " probably ")
                + (fileInfo.holderDead[i][j] ? "dead" : "alive")
                + " (" + fileInfo.lastHeard[i][j] + ")"
                );
            }
          }
        }
      }
    } catch (IOException ioe) {
      System.err.println("GlacerImpl cannot write to its dump file: " + dumpFileName + " (" + ioe + ")");
      ioe.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Appends a normal entry to the log file
   *
   * @param str The entry to be appended
   */
  private void log(String str) {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h + ":" + m + ":" + s + " @" + node.getId() + " " + str);
  }

  /**
   * Appends a warning to the log file
   *
   * @param str The warning to be appended
   */
  private void warn(String str) {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h + ":" + m + ":" + s + " @" + node.getId() + " *** WARNING *** " + str);
  }

  /**
   * Appends an 'unusual' entry to the log file. These are marked with a
   * special keyword so that they can be found easily.
   *
   * @param str The entry to be appended
   */
  private void unusual(String str) {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h + ":" + m + ":" + s + " @" + node.getId() + " *** UNUSUAL *** " + str);
  }

  /**
   * Schedule a timer event
   *
   * @param timeoutMsec Length of the delay (in milliseconds)
   * @param timeoutID Identifier (to distinguish between multiple timers)
   */
  private void addTimer(int timeoutMsec, char timeoutID) {
    assume(timeoutID < 10);

    /*
     *  We schedule a GlacierTimeoutMessage with the ID of the
     *  requested timer. This message will be delivered if the
     *  pires and it has not been removed in the meantime.
     */
    TimerTask timer = endpoint.scheduleMessage(new GlacierTimeoutMessage(timeoutID, getLocalNodeHandle()), timeoutMsec);
    timers.put(new Integer(timeoutID), timer);
  }

  /**
   * Cancel a timer event that has not yet occurred
   *
   * @param timeoutID Identifier of the timer event to be cancelled
   */
  private void removeTimer(int timeoutID) {
    TimerTask timer = (TimerTask) timers.remove(new Integer(timeoutID));

    if (timer != null) {
      timer.cancel();
    }
  }

  /**
   * Called after the audit phase is over. Checks which nodes have not
   * responded to the audit, and adds the corresponding objects to the
   * list of restore jobs. Also checks the database and adds restore
   * jobs for missing fragments.
   */
  private void determineRestoreJobs() {
    assume(restoreList.isEmpty());

    /*
     *  If a file entry is still in the AuditList at this point,
     *  its primary replica has not responded to any of our
     *  queries (we retry several times if there is no response).
     *  In this case, the object is most likely lost, and we
     *  must recover it from its fragments.
     */
    Enumeration enu = auditList.elements();
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo) enu.nextElement();
      unusual("No audit response, must restore " + fileInfo.key);

      /*
       *  Add an entry to the RestoreList
       */
      final RestoreListEntry rle = new RestoreListEntry(
        new FragmentKey(fileInfo.key, 0), RestoreListEntry.stWaitingForPrimary,
        fileInfo, 0, numFragments
        );

      /*
       *  There must be at least one fragment available locally
       *  (otherwise Glacier would not know about this file).
       *  Add these fragments to the RestoreList entry.
       */
      for (int i = 0; i < numFragments; i++) {
        for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] && (fileInfo.holderId[i][j] == null)) {
            final int thisFragment = i;
            rle.markChecked(thisFragment);
            storage.getObject(new FragmentKey(fileInfo.key, i),
              new Continuation() {
                public void receiveResult(Object o) {
                  log("Read and added fragment " + rle.key + ":" + thisFragment);
                  rle.addFragment(thisFragment, (Fragment) o);
                }

                public void receiveException(Exception e) {
                  warn("Cannot read fragment: " + rle.key + ":" + thisFragment + ", exception " + e);
                  e.printStackTrace();
                }
              });
          }
        }
      }

      restoreList.put(rle.key.getVersionKey(), rle);
    }

    /*
     *  Clear the AuditList; every entry should have been dealt
     *  with now
     */
    auditList.clear();

    /*
     *  Check whether we need to restore any individual fragments.
     */
    if (restoreList.size() < maxConcurrentRestores) {
      Vector restoreCandidates = new Vector();
      Random rand = new Random();
      boolean missingFragment[] = new boolean[numFragments];
      Enumeration enu2 = state.fileList.elements();

      /*
       *  Build a list of missing fragments. The list should contain
       *  at most one missing fragment for each file. If more than
       *  one fragment is missing, we choose a random one to minimize
       *  collisions.
       */
      while (enu2.hasMoreElements()) {
        FileInfo fileInfo = (FileInfo) enu2.nextElement();
        int liveFragments = 0;

        /*
         *  Count the live fragments
         */
        for (int i = 0; i < numFragments; i++) {
          missingFragment[i] = !fileInfo.anyLiveHolder(i);
          if (!missingFragment[i]) {
            liveFragments++;
          }
        }

        /*
         *  Recovery only makes sense if we know enough live fragments
         */
        if ((liveFragments >= numSurvivors) && (liveFragments < numFragments)) {
          int fragmentID;
          do {
            fragmentID = rand.nextInt(numFragments);
          } while (!missingFragment[fragmentID]);

          RestoreListEntry rle = new RestoreListEntry(
            new FragmentKey(fileInfo.key, fragmentID), RestoreListEntry.stAskingPrimary,
            fileInfo, (int) (maxRestoreAttemptFactor * numFragments),
            numFragments
            );

          restoreCandidates.add(rle);
        }
      }

      /*
       *  Pick random entries from the list of missing fragments and add
       *  them to the RestoreList. Note that at this point, the RestoreList
       *  may already contain entries for lost replicas.
       */
      while ((restoreList.size() < maxConcurrentRestores) && (restoreCandidates.size() > 0)) {
        int index = rand.nextInt(restoreCandidates.size());
        final RestoreListEntry rle = (RestoreListEntry) restoreCandidates.elementAt(index);
        FileInfo fileInfo = rle.fileInfo;

        /*
         *  Get a random entry
         */
        restoreCandidates.removeElementAt(index);
        log("decides to restore " + rle.key);

        /*
         *  Load any fragments that may be available locally, and mark
         *  them as checked (so that they are not requested from other
         *  holders later).
         */
        for (int i = 0; i < numFragments; i++) {
          for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
            if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] && (fileInfo.holderId[i][j] == null)) {
              final int thisFragment = i;
              rle.markChecked(thisFragment);
              storage.getObject(new FragmentKey(fileInfo.key, i),
                new Continuation() {
                  public void receiveResult(Object o) {
                    log("Read and added fragment " + rle.key + ":" + thisFragment);
                    rle.addFragment(thisFragment, (Fragment) o);
                  }

                  public void receiveException(Exception e) {
                    warn("Cannot read fragment: " + rle.key + ":" + thisFragment + ", exception " + e);
                    e.printStackTrace();
                  }
                });
            }
          }
        }

        /*
         *  Add the entry to the RestoreList
         */
        restoreList.put(rle.key.getVersionKey(), rle);
      }

      restoreCandidates.removeAllElements();
    }
  }

  /**
   * Checks the restore list and attempts to make progress for each entry.
   *
   * @return True if there is more work left.
   */
  private boolean restoreStep() {
    log("RestoreStep");

    /*
     *  Try to make progress on each entry in the RestoreList
     */
    Enumeration enu = restoreList.elements();
    int idx = 0;
    while (enu.hasMoreElements()) {
      RestoreListEntry rle = (RestoreListEntry) enu.nextElement();
      boolean madeProgress = false;

      log("Processing key " + rle.key + " (#" + (idx++) + ")");

      /*
       *  If the entry is the AskingPrimary state, no request has been
       *  sent yet. We send a request for the fragment to the primary
       *  replica.
       */
      if (rle.status == RestoreListEntry.stAskingPrimary) {
        rle.status = RestoreListEntry.stWaitingForPrimary;
        rle.attemptsLeft = maxAuditAttempts;
        madeProgress = true;
      }

      /*
       *  If the entry is in the WaitingForPrimary state, at least one
       *  request has been sent, but no answer has been received so far.
       *  If the maximum number of requests has not yet been exceeded,
       *  we send another request; otherwise the primary replica is
       *  probably dead, and we recover it from the fragments.
       */
      if (rle.status == RestoreListEntry.stWaitingForPrimary) {

        /*
         *  If possible, retry
         */
        if (rle.attemptsLeft > 0) {
          log("Sending audit for " + rle.key + " (" + (rle.attemptsLeft - 1) + " attempts left)");
          endpoint.route(
            rle.key.getVersionKey().getId(),
            new GlacierFetchMessage(
            getUID(), rle.key,
            getLocalNodeHandle(), rle.key.getVersionKey().getId()
            ),
            null
            );
          rle.attemptsLeft--;
        } else {

          /*
           *  If the primary appears to be dead, count the number of
           *  live fragments
           */
          int numFragmentsKnown = 0;
          for (int i = 0; i < numFragments; i++) {
            if (rle.fileInfo.anyLiveHolder(i)) {
              numFragmentsKnown++;
            }
          }

          /*
           *  If we know enough live fragments, begin restoring the
           *  object; otherwise cancel this entry, since we cannot
           *  succeed at this time. In the rare event where enough
           *  fragments are available locally, we can recover the
           *  object directly.
           */
          if (numFragmentsKnown >= numSurvivors) {
            if (rle.numFragmentsAvailable() >= numSurvivors) {
              unusual("Primary seems to have failed, restoring " + rle.key + " from fragments available locally (" + rle.numFragmentsAvailable() + ")");
              rle.status = RestoreListEntry.stRecoding;
            } else {
              unusual("Primary seems to have failed, attempting to restore " + rle.key + " from fragments");
              rle.status = RestoreListEntry.stCollectingFragments;
              rle.attemptsLeft = (int) (maxRestoreFromFragmentFactor * numSurvivors);
            }
          } else {
            rle.status = RestoreListEntry.stCanceled;
          }
        }

        madeProgress = true;
      }

      /*
       *  If the entry is in the CollectingFragments state, the primary
       *  has failed, but there are not enough fragments available yet
       *  to restore the object. We pick a random fragment holder that
       *  has not been asked yet and send a request for its fragment.
       *  If we exceed the maximum number of requests, or if all fragment
       *  holders have been tried, we give up and set the entry to
       *  Cancelled.
       */
      if (rle.status == RestoreListEntry.stCollectingFragments) {
        if (rle.attemptsLeft > 0) {
          int[] candidateIndex = new int[numFragments * FileInfo.maxHoldersPerFragment];
          int[] candidateSubindex = new int[numFragments * FileInfo.maxHoldersPerFragment];
          int numCandidates = 0;

          /*
           *  Build a list of fragments we might request
           */
          for (int i = 0; i < numFragments; i++) {
            if ((rle.haveFragment[i] == null) && !rle.checkedFragment[i]) {
              for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
                if (rle.fileInfo.holderKnown[i][j] && !rle.fileInfo.holderDead[i][j] && (rle.fileInfo.holderId[i][j] != null)) {
                  candidateIndex[numCandidates] = i;
                  candidateSubindex[numCandidates] = j;
                  numCandidates++;
                }
              }
            }
          }

          /*
           *  If there is at least one such fragment, pick a random one...
           */
          if (numCandidates >= 1) {
            int index = (new Random()).nextInt(numCandidates);
            Id theHolder = rle.fileInfo.holderId[candidateIndex[index]][candidateSubindex[index]];

            rle.checkedFragment[candidateIndex[index]] = true;
            rle.attemptsLeft--;
            log("RESTORE fetching fragment #" + candidateIndex[index] + " for " + rle.key + " (attempts left: " + rle.attemptsLeft + ")");

            /*
             *  ... and send a request to its holder...
             */
            endpoint.route(
              theHolder,
              new GlacierFetchMessage(
              getUID(), new FragmentKey(rle.key.getVersionKey(), candidateIndex[index]),
              getLocalNodeHandle(), theHolder
              ),
              null
              );
          } else {

            /*
             *  ... otherwise give up and cancel this entry.
             */
            unusual("RESTORE giving up on " + rle.key + ", no more candidates");
            rle.status = RestoreListEntry.stCanceled;
          }
        } else {

          /*
           *  The entry is also canceled if we exceed the maximum number
           *  of requests.
           */
          unusual("RESTORE giving up on " + rle.key + ", attempt limit reached");
          rle.status = RestoreListEntry.stCanceled;
        }

        madeProgress = true;
      }

      /*
       *  If the entry is in the Recoding state, the primary replica is
       *  dead, but we have found enough fragments (locally or remotely)
       *  to recover the object. We invoke the erasure codec and then
       *  hand the restored object over to PAST.
       */
      if (rle.status == RestoreListEntry.stRecoding) {
        log("RESTORE Recoding " + rle.key);
        assume(rle.numFragmentsAvailable() >= numSurvivors);

        Fragment[] material = new Fragment[numSurvivors];
        int index = 0;

        for (int i = 0; i < numFragments; i++) {
          if ((rle.haveFragment[i] != null) && (index < numSurvivors)) {
            material[index++] = rle.haveFragment[i];
          }
        }

        log("Decode object: " + rle.key);
        Object o = codec.decode(material);
        log("Decode complete: " + rle.key);

        boolean proceed = true;
        if (o == null) {
          warn("Decoder failed to decode " + rle.key);
          proceed = false;
        } else if (!(o instanceof PastContent)) {
          warn("Decoder delivered something other than PastContent");
          proceed = false;
        }

        if (proceed) {
          final RestoreListEntry frle = rle;
          super.insert((PastContent) o,
            new Continuation() {
              public void receive(Object result) throws Exception {
                warn("receive() " + result + " -- unexpected, ignored (key=" + frle.key + ")");
              }

              public void receiveException(Exception e) {
                warn("receiveException() " + e + " -- unexpected, ignored (key=" + frle.key + ")");
              }

              public void receiveResult(Object o) {
                log("Primary reinserted ok: " + frle.key);
              }
            });
        }

        /*
         *  We do NOT re-insert the fragment we originally wanted to restore. If this is
         *  added, make sure that the case of missing audit responses is handled properly
         *  (currently these entries pretend to be restoring fragment #0)
         */
        rle.status = RestoreListEntry.stCanceled;
        madeProgress = true;
      }

      if (!madeProgress) {
        warn("Did NOT make progress on RLE entry with status " + rle.status);
        panic("NO PROGRESS");
      }
    }

    /*
     *  Finally, we remove all canceled entries from the RestoreList.
     *  This could have done earlier, but the Java spec is a bit unclear
     *  about removing entries while an Enumeration is being used.
     *  Better be on the safe side...
     */
    enu = restoreList.elements();
    while (enu.hasMoreElements()) {
      RestoreListEntry rle = (RestoreListEntry) enu.nextElement();
      if (rle.status == RestoreListEntry.stCanceled) {
        log("Entry " + rle.key + " canceled, removing...");
        restoreList.remove(rle.key.getVersionKey());
        enu = restoreList.elements();
      }
    }

    return !restoreList.isEmpty();
  }

  /**
   * Determines which of two nodes is closer to a given point in nodeID space.
   *
   * @param current The first node
   * @param candidate The second node
   * @param reference The point of reference
   * @return DESCRIBE True if the second node is closer to the point of 
   *         reference than the first.
   */
  private boolean betterThan(Id current, Id candidate, Id reference) {
    /*
     *  Determine if <candidate> is closer to <reference> than <current>.
     *  This is more complicated that it seems, because Id might actually
     *  be a pastry.Id (simulator) or a RingId (ePost deployment)
     */
    if (current instanceof rice.pastry.Id) {
      return (((rice.pastry.Id) current).distance((rice.pastry.Id) reference).compareTo(((rice.pastry.Id) candidate).distance((rice.pastry.Id) reference)) > 0);
    } else {
      rice.pastry.Id pCurrent = (rice.pastry.Id) ((RingId) current).getId();
      rice.pastry.Id pCandidate = (rice.pastry.Id) ((RingId) candidate).getId();
      rice.pastry.Id pReference = (rice.pastry.Id) ((RingId) reference).getId();
      return betterThan(pCurrent, pCandidate, pReference);
    }
  }

  /**
   * Determines which fragments need to be handed off to another node 
   * because a) they have been recovered, or b) they need to migrate.
   */
  private void determineHandoffJobs() {
    /*
     *  First, determine the leaf set of this node
     */
    LeafSet leafSet;
    if (node instanceof MultiringNode) {
      leafSet = ((DistPastryNode) ((MultiringNode) node).getNode()).getLeafSet();
    } else {
      leafSet = ((PastryNode) node).getLeafSet();
    }

    int leafSetSize = leafSet.size();

    /*
     *  Look at each local fragment and check whether the nodeID of a
     *  leaf set member is closer to its 'final destination' than ours.
     *  If so, we need to hand it over to that leaf set member.
     */
    Enumeration enu = state.fileList.elements();
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo) enu.nextElement();

      for (int i = 0; i < numFragments; i++) {
        boolean shouldMove = false;
        Id best = getLocalNodeHandle().getId();
        Id fkey = getFragmentLocation(fileInfo.key.getId(), i);

        if (node instanceof MultiringNode) {
          best = factory.buildRingId(((MultiringNode) node).getRingId(), ((RingId) (node.getId())).getId());
        }

        for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] && (fileInfo.holderId[i][j] == null)) {
            for (int k = 0; k < leafSetSize; k++) {
              NodeHandle nh = leafSet.get(k);
              if (nh != null) {
                Id nhId = nh.getId();
                if (node instanceof MultiringNode) {
                  nhId = factory.buildRingId(((MultiringNode) node).getRingId(), nh.getId());
                }

                if (betterThan(best, nhId, fkey)) {
                  best = nhId;
                  shouldMove = true;
                }
              }
            }
          }
        }

        /*
         *  If there is a better node, load the fragment from disk and
         *  add it to the HandoffList.
         */
        if (shouldMove) {
          log(fileInfo.key.getId() + ":" + i + " (" + fkey + ") should move from " + getLocalNodeHandle() + " to " + best);
          final FragmentKey skey = new FragmentKey(fileInfo.key, i);
          final Id nbest = best;
          final FileInfo nfileInfo = fileInfo;
          storage.getObject(skey,
            new Continuation() {
              public void receiveResult(Object o) {
                log("PUT for " + skey);
                handoffList.put(skey, new HandoffListEntry(skey, nbest, false, (Fragment) o, nfileInfo.manifest));
              }

              public void receiveException(Exception e) {
                warn("Cannot read fragment: " + skey + ", exception " + e);
                e.printStackTrace();
              }
            });
          break;
        }
      }
    }
  }

  /**
   * Starts the handoff process for each entry in the handoff list.
   */
  private void triggerHandoffs() {
    /*
     *  For each entry in the HandoffList, send a QueryMessage to the
     *  destination. When a negative response is received, the HandoffList
     *  will be checked, and the fragment will actually be handed over.
     */
    Enumeration enu = handoffList.elements();
    while (enu.hasMoreElements()) {
      HandoffListEntry hle = (HandoffListEntry) enu.nextElement();
      log("Attempting to handoff " + hle.skey + " to " + hle.destination);

      endpoint.route(
        hle.destination,
        new GlacierQueryMessage(
        getUID(), hle.skey, getLocalNodeHandle(),
        hle.destination
        ),
        null
        );
    }
  }

  /**
   * Remove old entries from the local history.
   */
  private void pruneHistory() {
    boolean reachedEnd = false;
    while (!reachedEnd) {
      try {
        HistoryEvent hev = (HistoryEvent) state.history.getFirst();
        if (hev.sequenceNo < (state.currentSequenceNo - numIncludePreviousStatusCasts)) {
          state.history.removeFirst();
        } else {
          reachedEnd = true;
        }
      } catch (NoSuchElementException nsee) {
        reachedEnd = true;
      }
    }
  }

  /**
   * Perform a status cast
   */
  private void statusCast() {
    log("StatusCast #" + state.currentSequenceNo);

    int numHolders = state.holderList.size();
    Id holder[] = new Id[numHolders];
    HolderInfo holderInfo[] = new HolderInfo[numHolders];
    Vector holderLog[] = new Vector[numHolders];
    boolean holderGetsFullList[] = new boolean[numHolders];

    /*
     *  Load the holder entries from the HolderList, and determine whether
     *  the holder gets a diff or the full list.
     */
    Enumeration enu = state.holderList.elements();
    boolean fullListNeeded = false;
    int index = 0;
    while (enu.hasMoreElements()) {
      HolderInfo hi = (HolderInfo) enu.nextElement();
      holder[index] = hi.nodeID;
      holderLog[index] = new Vector();
      holderInfo[index] = hi;
      holderGetsFullList[index] = (hi.lastAckedSequenceNo == -1) ||
        (hi.lastAckedSequenceNo < (state.currentSequenceNo - numIncludePreviousStatusCasts - 1));
      fullListNeeded |= holderGetsFullList[index];
      index++;
    }

    /*
     *  For each history entry, find out to which holders it is relevant,
     *  and add it to the corresponding holderLogs.
     */
    if (!state.history.isEmpty()) {
      ListIterator li = state.history.listIterator(0);
      while (li.hasNext()) {
        HistoryEvent event = (HistoryEvent) li.next();
        log("Parsing " + event);

        FileInfo fileInfo = (FileInfo) state.fileList.get(event.key.getVersionKey());
        if (fileInfo != null) {
          for (int i = 0; i < numHolders; i++) {
            if (!holderGetsFullList[i]) {
              boolean relevant = false;
              for (int j = 0; j < numFragments; j++) {
                for (int k = 0; k < FileInfo.maxHoldersPerFragment; k++) {
                  if (fileInfo.holderKnown[j][k] &&
                    !fileInfo.holderDead[j][k] &&
                    (fileInfo.holderId[j][k] != null)) {
                    if (holder[i].equals(fileInfo.holderId[j][k])) {
                      relevant = true;
                    }
                  }
                }
              }

              if (relevant) {
                holderLog[i].add(event);
              }
            }
          }
        } else {
          warn("File record disappeared?!?");
        }
      }
    }

    /*
     *  For those holders who get a full list, generate that list.
     *  These lists are specific to each holder because they only
     *  include relevant entries.
     */
    Enumeration enf = state.fileList.elements();
    while (enf.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo) enf.nextElement();
      for (int i = 0; i < numHolders; i++) {
        if (holderGetsFullList[i]) {
          boolean relevant = false;
          for (int j = 0; j < numFragments; j++) {
            for (int k = 0; k < FileInfo.maxHoldersPerFragment; k++) {
              if (fileInfo.holderKnown[j][k] &&
                !fileInfo.holderDead[j][k] &&
                (fileInfo.holderId[j][k] != null)) {
                if (holder[i].equals(fileInfo.holderId[j][k])) {
                  relevant = true;
                }
              }
            }
          }

          /*
           *  If the file is relevant, add an entry for EACH fragment!
           */
          if (relevant) {
            for (int j = 0; j < numFragments; j++) {
              for (int k = 0; k < FileInfo.maxHoldersPerFragment; k++) {
                if (fileInfo.holderKnown[j][k] && !fileInfo.holderDead[j][k]) {
                  holderLog[i].add(
                    new HistoryEvent(
                    (fileInfo.holderId[j][k] == null) ? HistoryEvent.evtAcquired : HistoryEvent.evtNewHolder,
                    new FragmentKey(fileInfo.key, j),
                    (fileInfo.holderId[j][k] == null) ? getLocalNodeHandle().getId() : fileInfo.holderId[j][k],
                    state.currentSequenceNo
                    )
                    );
                }
              }
            }
          }
        }
      }
    }

    /*
     *  Send a StatusMessage to each holder
     */
    for (int i = 0; i < numHolders; i++) {
      log("Sending to holder " + holder[i] + ": (full=" + holderGetsFullList[i] + ")");

      HistoryEvent[] evtList = new HistoryEvent[holderLog[i].size()];
      Enumeration enup = holderLog[i].elements();
      int idx = 0;

      while (enup.hasMoreElements()) {
        HistoryEvent evt = (HistoryEvent) enup.nextElement();
        log("tells " + holder[i] + " " + evt);
        evtList[idx++] = evt;
      }

      endpoint.route(
        holder[i],
        new GlacierStatusMessage(
        getUID(), state.currentSequenceNo,
        holderInfo[i].lastReceivedSequenceNo,
        0,
        holderGetsFullList[i],
        evtList,
        getLocalNodeHandle(),
        holder[i]
        ),
        null
        );
    }

    /*
     *  Increment our local sequence number, and remove old entries from
     *  the history
     */
    state.currentSequenceNo++;
    pruneHistory();
  }

  /**
   * Audits some primary replicas
   */
  private void auditPrimaryReplicas() {
    Random rand = new Random();

    /*
     *  Pick a few random entries from the fileList and them to the AuditList
     */
    if (!state.fileList.isEmpty()) {
      for (int i = 0; i < maxConcurrentAudits; i++) {
        int index = rand.nextInt(state.fileList.size());
        Enumeration enum = state.fileList.elements();
        while ((--index) > 0) {
          enum.nextElement();
        }

        FileInfo fileInfo = (FileInfo) enum.nextElement();
        if (!auditList.containsKey(fileInfo.key)) {
          log("decides to audit " + fileInfo.key);
          auditList.put(fileInfo.key, fileInfo);
        }
      }
    }

    /*
     *  Make PAST send LookupHandle messages to the primary replicas, and
     *  remove the entries from the AuditList when we get a response
     */
    Enumeration enum = auditList.keys();
    while (enum.hasMoreElements()) {
      final VersionKey key = (VersionKey) enum.nextElement();

      lookupHandles(key.getId(), 1,
        new Continuation() {
          public void receiveResult(Object o) {
            PastContentHandle[] handles = (PastContentHandle[]) o;
            if ((handles.length > 0) && (handles[0] != null)) {
              auditList.remove(key);
              log("Got audit response from " + key);
            }
          }

          public void receiveException(Exception e) {
            warn("receiveException(" + e + ") during audit -- unexpected, ignored (key=" + key + ")");
          }
        });
    }
  }

  /**
   * Removes old 'sticky messages' from the delivery queue.
   */
  private void expireOldPackets() {
    ListIterator sqi = stickyQueue.listIterator(0);

    while (sqi.hasNext()) {
      GlacierStatusMessage lsm = (GlacierStatusMessage) sqi.next();
      lsm.remainingLifetime--;
      if (lsm.remainingLifetime <= 0) {
        log("STICKY expired packet " + lsm);
        sqi.remove();
      }
    }
  }

  /**
   * Removes old entries from the database. 
   */
  private void expireOldHolders() {
    Vector expireList = new Vector();

    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    long tnow = cal.getTimeInMillis();

    /* Removes expired entries from the holder set */

    Enumeration e1 = state.holderList.elements();
    while (e1.hasMoreElements()) {
      HolderInfo hle = (HolderInfo) e1.nextElement();
      hle.numReferences = 0;
      hle.numLiveReferences = 0;

      cal.setTime(hle.lastHeardOf);
      long tdelay = tnow - cal.getTimeInMillis();
      if (tdelay > certainHolderEntryTimeout) {
        log("Expiring HOLDER " + hle.nodeID + " (last heard of " + hle.lastHeardOf + ")");
        expireList.add(hle.nodeID);
      }
    }

    /* Removes expired holders from the fragment set. Note that the lifetime
       of an entry depends on its status; UNCERTAIN entries live longer
       than DEAD entries. */

    Enumeration e2 = state.fileList.elements();
    while (e2.hasMoreElements()) {
      FileInfo fi = (FileInfo) e2.nextElement();
      for (int i = 0; i < numFragments; i++) {
        for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
          if (fi.holderKnown[i][j]) {
            if ((fi.holderId[i][j] != null) && (expireList.contains(fi.holderId[i][j]))) {
              log("Expiring OLD HOLDER entry for " + fi.key + ":" + i + " at " + fi.holderId[i][j]);
              fi.holderKnown[i][j] = false;
            } else {
              cal.setTime(fi.lastHeard[i][j]);
              long tdelay = tnow - cal.getTimeInMillis();
              if (fi.holderDead[i][j]) {
                if (tdelay > deadHolderEntryTimeout) {
                  log("Expiring DEAD entry for " + fi.key + ":" + i + " at " + fi.holderId[i][j]);
                  fi.holderKnown[i][j] = false;
                }
              } else if (!fi.holderCertain[i][j]) {
                if (tdelay > uncertainHolderEntryTimeout) {
                  log("Expiring UNCERTAIN entry for " + fi.key + ":" + i + " at " + fi.holderId[i][j]);
                  fi.holderKnown[i][j] = false;
                }
              }
            }
          }

          if (fi.holderKnown[i][j] && (fi.holderId[i][j] != null)) {
            HolderInfo hle = (HolderInfo) state.holderList.get(fi.holderId[i][j]);
            if (hle != null) {
              hle.numReferences++;
              if (!fi.holderDead[i][j]) {
                hle.numLiveReferences++;
              }
            }
          }
        }
      }
    }

    /* If a holder is no longer mentioned anywhere in the fragment set, it
       can be removed even though it may not yet have expired */

    Enumeration e3 = state.holderList.elements();
    while (e3.hasMoreElements()) {
      HolderInfo hle = (HolderInfo) e3.nextElement();
      log("HOLDER " + hle.nodeID + ": " + hle.numReferences + " total, " + hle.numLiveReferences + " live");
      if ((hle.numReferences == 0) && (!expireList.contains(hle.nodeID))) {
        expireList.add(hle.nodeID);
      }
    }

    Enumeration e4 = expireList.elements();
    while (e4.hasMoreElements()) {
      Object key = e4.nextElement();
      state.holderList.remove(key);
      log("Expiring holder entry for " + key);
    }
  }

  /**
   * Called when a timer expires
   *
   * @param timerID An identifier for the timer
   */
  private void timerExpired(char timerID) {
    log("TIMER EXPIRED: #" + (int) timerID);

    switch (timerID) {
      case tiInsert:
      {
        /* This timer should never expire under normal operation because
         * it is canceled once the initial set of fragments has been
         * inserted completely. When it does expire, we must attempt to
         * insert other fragments. */
      
        Enumeration e = insertList.elements();
        boolean moreWork = false;
        while (e.hasMoreElements())
            moreWork |= insertStep((InsertListEntry)e.nextElement());

        if (moreWork)
            addTimer(insertTimeout, tiInsert);
        else
            insertTimeoutActive = false;

        break;
      }
      case tiStatusCast:
      {
        /* This timer expires when a status cast is due */
      
        log("Timeout: StatusCast - auditing...");
        auditPrimaryReplicas();
        addTimer(auditTimeout, tiAudit);
        break;
      }
      case tiAudit:
      {
        /* This timer expires some time after audits have been sent to
         * the primary replicas. At this point, all live replicas should
         * have replied. */
      
        log("Timeout: Audit - determining restore jobs...");
        expireOldHolders();
        expireOldPackets();
        determineRestoreJobs();
        /*
         *  NO break here! Fall through to tiRestore!
         */
      }
      case tiRestore:
      {
        /* If there is more recovery work to do, do it. Otherwise, begin
           handing off fragments to their new holders. */
      
        if (restoreStep()) {
          addTimer(restoreCycle, tiRestore);
        } else {
          determineHandoffJobs();
          triggerHandoffs();
          addTimer(handoffTimeout, tiHandoff);
        }
        break;
      }
      case tiHandoff:
      {
        /* This timer expires some time after the handoff phase. All 
         * successful handoffs have already been removed from the list. */
      
        if (!handoffList.isEmpty()) {
          HandoffListEntry hle = (HandoffListEntry) (handoffList.elements().nextElement());
          warn("Handoff not successful: " + hle.skey + " to " + hle.destination);
        }
        statusCast();
        state.lastStatusCast = new Date();
        syncState();
        scheduleNextStatusCast();
        break;
      }
      default:
      {
        System.err.println("Unknown timer expired: " + (int) timerID);
        System.exit(1);
      }
    }
  }

  private void sendInsertsFor(InsertListEntry ile) {
    Id[] knownHolders = new Id[numInitialFragments];
    int[] knownFragmentIDs = new int[numInitialFragments];
    boolean[] knownHolderCertain = new boolean[numInitialFragments];
    for (int i = 0; i < numInitialFragments; i++) {
      knownHolders[i] = ile.holder[i].getId();
      knownFragmentIDs[i] = i;
      knownHolderCertain[i] = true;
    }

    for (int i = 0; i < numInitialFragments; i++) {
      if (!ile.receiptReceived[i]) {
        log("Sending insert request for " + ile.key + ":" + i + " to " + ile.holder[i].getId());
        endpoint.route(
          null,
          new GlacierInsertMessage(
            getUID(), new FragmentKey(ile.key, i), ile.manifest, ile.fragments[i],
            knownHolders, knownFragmentIDs, knownHolderCertain,
            getLocalNodeHandle(), ile.holder[i].getId()
          ),
          ile.holder[i]
        );
      }
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param ile DESCRIBE THE PARAMETER
   */
  private synchronized boolean insertStep(InsertListEntry ile) {
    System.out.print(ile.key + " knows ");
    for (int i = 0; i < numInitialFragments; i++) {
      if (ile.holderKnown[i]) {
        System.out.print(i);

        if (ile.receiptReceived[i])
          System.out.print("R");

        System.out.print(" ");
      }
    }
    System.out.println();

    if (System.currentTimeMillis() < ile.timeout) {
      boolean knowsAllHolders = true;
      for (int i = 0; i < numInitialFragments; i++) {
        if (!ile.holderKnown[i])
          knowsAllHolders = false;
      }

      if (knowsAllHolders) {
        ile.timeout = System.currentTimeMillis() + insertTimeout - 100;
        if (!ile.insertsSent) {
          ile.insertsSent = true;
          sendInsertsFor(ile);
        }

        boolean hasAllReceipts = true;
        for (int i = 0; i < numInitialFragments; i++) {
          if (!ile.receiptReceived[i]) {
            hasAllReceipts = false;
          }
        }

        if (hasAllReceipts) {
          insertList.remove(ile.key);
          log("Finished inserting " + ile.key);
          return false;
        }
      }
    } else {
      /* A timeout has occurred */

      log("Timeout... "+ile.attemptsLeft+" attempts left");
     
      if (ile.attemptsLeft > 0) {
        ile.timeout = System.currentTimeMillis() + insertTimeout - 100;
        ile.attemptsLeft --;
        
        if (!ile.insertsSent) {
          unusual("Insert: Re-sending queries for " + ile.key);
          for (int i = 0; i < numInitialFragments; i++) {
            Id fragmentLoc = getFragmentLocation(ile.key.getId(), i);
            endpoint.route(
              fragmentLoc,
              new GlacierQueryMessage(getUID(), new FragmentKey(ile.key, i), getLocalNodeHandle(), fragmentLoc),
              null
            );
          }
        } else {
          unusual("Insert: Re-sending inserts for " + ile.key);
          sendInsertsFor(ile);
        }
      } else {
        insertList.remove(ile.key);
        warn("Giving up attempt to insert " + ile.key);
        return false;
      } 
    }
    
    return true;
  }

  /**
   * Adds a feature to the OrUpdateHolder attribute of the GlacierImpl object
   *
   * @param fileInfo The feature to be added to the OrUpdateHolder attribute
   * @param fragmentID The feature to be added to the OrUpdateHolder attribute
   * @param newHolder The feature to be added to the OrUpdateHolder attribute
   * @param alive The feature to be added to the OrUpdateHolder attribute
   * @param overrideDead The feature to be added to the OrUpdateHolder attribute
   * @param certain The feature to be added to the OrUpdateHolder attribute
   */
  private void addOrUpdateHolder(FileInfo fileInfo, int fragmentID, Id newHolder, boolean alive, boolean overrideDead, boolean certain) {
    log("AOUH " + fileInfo.key + ":" + fragmentID + " H " + newHolder + " alive " + alive + " certain " + certain + " override " + overrideDead);

    for (int k = 0; k < FileInfo.maxHoldersPerFragment; k++) {
      if (fileInfo.holderKnown[fragmentID][k] && (fileInfo.holderId[fragmentID][k] != null)) {
        if (fileInfo.holderId[fragmentID][k].equals(newHolder)) {
          HolderInfo currentHolder = (HolderInfo) state.holderList.get(fileInfo.holderId[fragmentID][k]);
          assume(currentHolder != null);

          /*
           *  The new holder is already there; all we need to do is to update it
           */
          if (fileInfo.holderDead[fragmentID][k]) {
            /*
             *  The holder is there, but marked as dead -- revive only if override
             */
            if (alive && overrideDead) {
              log("Reviving");
              fileInfo.holderDead[fragmentID][k] = false;
              fileInfo.holderCertain[fragmentID][k] = certain;
              currentHolder.numLiveReferences++;
              recordHolderUpdateEvent(new FragmentKey(fileInfo.key, fragmentID), newHolder);
            }
          } else {
            /*
             *  The holder is there and alive
             */
            if (!alive) {
              log("Killing");
              fileInfo.holderDead[fragmentID][k] = true;
              fileInfo.holderCertain[fragmentID][k] = certain;
              currentHolder.numLiveReferences--;
            } else {
              if (certain && !fileInfo.holderCertain[fragmentID][k]) {
                log("Confirming");
                fileInfo.holderCertain[fragmentID][k] = true;
              }

              if (certain == fileInfo.holderCertain[fragmentID][k]) {
                log("Updating timestamp");
                fileInfo.lastHeard[fragmentID][k] = new Date();
              }
            }
          }

          return;
        }
      }
    }

    /*
     *  The holder is not there yet -- we learned something new!
     */
    int slot = fileInfo.getNextAvailableSlotFor(fragmentID);
    if (slot < 0) {
      panic("No more holder slots in addOrUpdateHolder");
    }

    if (fileInfo.holderKnown[fragmentID][slot] && (fileInfo.holderId[fragmentID][slot] != null)) {
      removeHolderReference(fileInfo.holderId[fragmentID][slot]);
    }

    log("Adding as new holder");

    fileInfo.holderKnown[fragmentID][slot] = true;
    fileInfo.holderId[fragmentID][slot] = addHolderReference(newHolder);
    fileInfo.holderDead[fragmentID][slot] = !alive;
    fileInfo.holderCertain[fragmentID][slot] = certain;
    fileInfo.lastHeard[fragmentID][slot] = new Date();

    if (alive) {
      HolderInfo currentHolder = (HolderInfo) state.holderList.get(fileInfo.holderId[fragmentID][slot]);
      assume(currentHolder != null);
      currentHolder.numLiveReferences++;
    }
  }

  /**
   * Adds a feature to the FragmentNews attribute of the GlacierImpl object
   *
   * @param key The feature to be added to the FragmentNews attribute
   * @param newHolder The feature to be added to the FragmentNews attribute
   * @param eventType The feature to be added to the FragmentNews attribute
   * @param sender The feature to be added to the FragmentNews attribute
   */
  private void addFragmentNews(FragmentKey key, Id newHolder, int eventType, Id sender) {
    assume((0 <= key.getFragmentID()) && (key.getFragmentID() < numFragments));
    log("News on " + key + ": Sender " + sender + " says " + HistoryEvent.eventName(eventType) + " " + newHolder);

    if (newHolder.equals(getLocalNodeHandle().getId())) {
      return;
    }

    FileInfo fileInfo = (FileInfo) state.fileList.get(key.getVersionKey());
    if (fileInfo == null) {
      log("addFragmentNews cannot find the file in question -- ignoring");
      return;
    }

    if (fileInfo.haveFragment(key.getFragmentID())) {
      warn("Got told about my own fragment -- ignoring");
      return;
    }

    /*
     *  Update policy:
     *  - ACQUIRED: Always added, always live (unless already present)
     *  - MIGRATED: Source added dead, destination added live
     *  - NEW HOLDER: Added live if not present (dead or alive)
     */
    switch (eventType) {
      case HistoryEvent.evtAcquired:
        addOrUpdateHolder(fileInfo, key.getFragmentID(), newHolder, true, true, true);
        break;
      case HistoryEvent.evtHandedOff:
        addOrUpdateHolder(fileInfo, key.getFragmentID(), sender, false, false, true);
        addOrUpdateHolder(fileInfo, key.getFragmentID(), newHolder, true, true, false);
        break;
      case HistoryEvent.evtNewHolder:
        addOrUpdateHolder(fileInfo, key.getFragmentID(), newHolder, true, false, false);
        break;
      default:
        panic("Unknown event type in addFragmentNews: " + eventType);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   */
  private void recordNewFragmentEvent(FragmentKey key) {
    state.history.addLast(new HistoryEvent(HistoryEvent.evtAcquired, key, getLocalNodeHandle().getId(), state.currentSequenceNo));
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   * @param newHolder DESCRIBE THE PARAMETER
   */
  private void recordHolderUpdateEvent(FragmentKey key, Id newHolder) {
    state.history.addLast(new HistoryEvent(HistoryEvent.evtNewHolder, key, newHolder, state.currentSequenceNo));
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   * @param newHolder DESCRIBE THE PARAMETER
   */
  private void recordMigratedFragmentEvent(FragmentKey key, Id newHolder) {
    if (!newHolder.equals(getLocalNodeHandle().getId())) {
      state.history.addLast(new HistoryEvent(HistoryEvent.evtHandedOff, key, newHolder, state.currentSequenceNo));
    } else {
      log("MIGRATED TO MYSELF -- IGNORING");
    }
  }

  /**
   * Adds a feature to the HolderReference attribute of the GlacierImpl object
   *
   * @param nodeID The feature to be added to the HolderReference attribute
   * @return DESCRIBE THE RETURN VALUE
   */
  private Id addHolderReference(Id nodeID) {
    HolderInfo holderInfo = (HolderInfo) state.holderList.get(nodeID);
    if (holderInfo == null) {
      holderInfo = new HolderInfo(nodeID, new Date(), -1, -1);
      state.holderList.put(nodeID, holderInfo);
    }

    holderInfo.numReferences++;
    return nodeID;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param nodeID DESCRIBE THE PARAMETER
   */
  private void removeHolderReference(Id nodeID) {
    HolderInfo holderInfo = (HolderInfo) state.holderList.get(nodeID);
    assume(holderInfo != null);

    holderInfo.numReferences--;
    if (holderInfo.numReferences <= 0) {
      state.holderList.remove(nodeID);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param key DESCRIBE THE PARAMETER
   * @param manifest DESCRIBE THE PARAMETER
   */
  private synchronized void markNewFragmentStored(FragmentKey key, StorageManifest manifest) {
    assume((0 <= key.getFragmentID()) && (key.getFragmentID() < numFragments));

    FileInfo fileInfo = (FileInfo) state.fileList.get(key.getVersionKey());

    log("Marking new fragment as stored: " + key);

    if (fileInfo == null) {
      fileInfo = new FileInfo(key.getVersionKey(), manifest, numFragments);
      state.fileList.put(key.getVersionKey(), fileInfo);
    }

    assume(fileInfo.key.equals(key.getVersionKey()));

    int fragmentID = key.getFragmentID();
    int slot = fileInfo.getNextAvailableSlotFor(fragmentID);
    if (slot < 0) {
      panic("No more holder slots");
    }

    if (fileInfo.holderKnown[fragmentID][slot] && (fileInfo.holderId[fragmentID][slot] != null)) {
      removeHolderReference(fileInfo.holderId[fragmentID][slot]);
    }

    fileInfo.holderKnown[fragmentID][slot] = true;
    fileInfo.holderDead[fragmentID][slot] = false;
    fileInfo.holderCertain[fragmentID][slot] = true;
    fileInfo.lastHeard[fragmentID][slot] = new Date();
    fileInfo.holderId[fragmentID][slot] = null;

    recordNewFragmentEvent(key);
  }

  /**
   * DESCRIBE THE METHOD
   */
  private void dumpFileList() {
    Enumeration enu = state.fileList.elements();

    log("FileList at " + getLocalNodeHandle().getId() + ":");
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo) enu.nextElement();

      log(" - File " + fileInfo.key);
      for (int i = 0; i < numFragments; i++) {
        for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j]) {
            String holderInfo = "(this node)";
            if (fileInfo.holderId[i][j] != null) {
              holderInfo = "" + fileInfo.holderId[i][j];
            }

            log("    * Fragment " + i + " at " + holderInfo
              + (fileInfo.holderCertain[i][j] ? " certainly " : " probably ")
              + (fileInfo.holderDead[i][j] ? "dead" : "alive")
              + " (" + fileInfo.lastHeard[i][j] + ")"
              );
          }
        }
      }
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param holder DESCRIBE THE PARAMETER
   */
  private void refreshConfirmedFragmentsFor(Id holder) {
    Enumeration enu = state.fileList.elements();
    Date now = new Date();

    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo) enu.nextElement();
      for (int i = 0; i < numFragments; i++) {
        for (int j = 0; j < FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] && fileInfo.holderCertain[i][j] && (fileInfo.holderId[i][j] != null)) {
            if (fileInfo.holderId[i][j].equals(holder)) {
              fileInfo.lastHeard[i][j] = now;
            }
          }
        }
      }
    }
  }
}

/**
 * @(#) GlacierImpl.java This is an implementation of the Glacier interface.
 *
 * @version $Id$
 * @author Andreas Haeberlen
 */

class InsertListEntry {

  /**
   * DESCRIBE THE FIELD
   */
  public VersionKey key;
  /**
   * DESCRIBE THE FIELD
   */
  public Fragment[] fragments;
  /**
   * DESCRIBE THE FIELD
   */
  public StorageManifest manifest;
  /**
   * DESCRIBE THE FIELD
   */
  public boolean holderKnown[];
  /**
   * DESCRIBE THE FIELD
   */
  public boolean receiptReceived[];
  /**
   * DESCRIBE THE FIELD
   */
  public NodeHandle holder[];
  /**
   * DESCRIBE THE FIELD
   */
  public long timeout;
  /**
   * DESCRIBE THE FIELD
   */
  public boolean insertsSent;
  /**
   * DESCRIBE THE FIELD
   */
  public NodeHandle localNodeHandle;
  /**
   * DESCRIBE THE FIELD
   */
  public int attemptsLeft;

  /**
   * Constructor for InsertListEntry.
   *
   * @param key DESCRIBE THE PARAMETER
   * @param fragments DESCRIBE THE PARAMETER
   * @param manifest DESCRIBE THE PARAMETER
   * @param timeout DESCRIBE THE PARAMETER
   * @param numInitialFragments DESCRIBE THE PARAMETER
   * @param localNodeHandle DESCRIBE THE PARAMETER
   */
  public InsertListEntry(VersionKey key, Fragment[] fragments, StorageManifest manifest, long timeout, int numInitialFragments, NodeHandle localNodeHandle, int attemptsLeft) {
    this.key = key;
    this.fragments = fragments;
    this.manifest = manifest;
    this.timeout = timeout;
    this.insertsSent = false;
    this.localNodeHandle = localNodeHandle;
    this.attemptsLeft = attemptsLeft;
    this.holderKnown = new boolean[numInitialFragments];
    Arrays.fill(this.holderKnown, false);
    this.receiptReceived = new boolean[numInitialFragments];
    Arrays.fill(this.receiptReceived, false);
    this.holder = new NodeHandle[numInitialFragments];
    for (int i = 0; i < numInitialFragments; i++) {
      this.holder[i] = null;
    }
  }
}

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
class HandoffListEntry {

  /**
   * DESCRIBE THE FIELD
   */
  public FragmentKey skey;
  /**
   * DESCRIBE THE FIELD
   */
  public Id destination;
  /**
   * DESCRIBE THE FIELD
   */
  public StorageManifest manifest;
  /**
   * DESCRIBE THE FIELD
   */
  public Fragment fragment;
  /**
   * DESCRIBE THE FIELD
   */
  public boolean isRestoredFragment;

  /**
   * Constructor for HandoffListEntry.
   *
   * @param skey DESCRIBE THE PARAMETER
   * @param destination DESCRIBE THE PARAMETER
   * @param isRestoredFragment DESCRIBE THE PARAMETER
   * @param fragment DESCRIBE THE PARAMETER
   * @param manifest DESCRIBE THE PARAMETER
   */
  public HandoffListEntry(FragmentKey skey, Id destination, boolean isRestoredFragment, Fragment fragment, StorageManifest manifest) {
    this.skey = skey;
    this.destination = destination;
    this.isRestoredFragment = isRestoredFragment;
    this.fragment = fragment;
    this.manifest = manifest;
  }
}

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
class RestoreListEntry {

  /**
   * DESCRIBE THE FIELD
   */
  public FragmentKey key;
  int status;
  FileInfo fileInfo;
  int attemptsLeft;
  boolean checkedFragment[];
  Fragment haveFragment[];
  final static int stAskingPrimary = 1;
  final static int stWaitingForPrimary = 2;
  final static int stCollectingFragments = 3;
  final static int stRecoding = 4;
  final static int stCanceled = 5;

  /**
   * Constructor for RestoreListEntry.
   *
   * @param key DESCRIBE THE PARAMETER
   * @param status DESCRIBE THE PARAMETER
   * @param fileInfo DESCRIBE THE PARAMETER
   * @param attemptsLeft DESCRIBE THE PARAMETER
   * @param numFragments DESCRIBE THE PARAMETER
   */
  public RestoreListEntry(FragmentKey key, int status, FileInfo fileInfo, int attemptsLeft, int numFragments) {
    this.key = key;
    this.status = status;
    this.fileInfo = fileInfo;
    this.attemptsLeft = attemptsLeft;
    this.checkedFragment = new boolean[numFragments];
    this.haveFragment = new Fragment[numFragments];
    for (int i = 0; i < numFragments; i++) {
      this.checkedFragment[i] = false;
      this.haveFragment[i] = null;
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param fid DESCRIBE THE PARAMETER
   */
  public void markChecked(int fid) {
    this.checkedFragment[fid] = true;
  }

  /**
   * Adds a feature to the Fragment attribute of the RestoreListEntry object
   *
   * @param fid The feature to be added to the Fragment attribute
   * @param frag The feature to be added to the Fragment attribute
   */
  public void addFragment(int fid, Fragment frag) {
    this.haveFragment[fid] = frag;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public int numFragmentsAvailable() {
    int result = 0;

    for (int i = 0; i < haveFragment.length; i++) {
      if (haveFragment[i] != null) {
        result++;
      }
    }

    return result;
  }
}
