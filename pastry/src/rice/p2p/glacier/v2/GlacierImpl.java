package rice.p2p.glacier.v2;

// o Handoff
// o Multiple fragments on one node
// o Speed up returns (when enough receipts have been received)!

import java.util.*;
import java.security.*;
import java.io.*;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v2.*;
import rice.p2p.glacier.v2.messaging.*;
import rice.p2p.multiring.*;
import rice.pastry.leafset.*;
import rice.visualization.server.DebugCommandHandler;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.dist.DistPastryNode;
import rice.pastry.NodeId;
import rice.post.storage.SignedData;

public class GlacierImpl implements Glacier, Past, GCPast, VersioningPast, Application, DebugCommandHandler {

  protected final FragmentStorage fragmentStorage;
  protected final StorageManager neighborStorage;
  protected final GlacierPolicy policy;
  protected final Node node;
  protected final int numFragments;
  protected String instance;
  private int id;
  protected int numSurvivors;
  protected Endpoint endpoint;
  private MultiringIdFactory factory;
  private PastryIdFactory pastryIdFactory;
  protected IdRange responsibleRange;

  private final char tiContinuation = 4;

  protected Hashtable continuations;
  
  protected Hashtable timers;
  protected long nextContinuationTimeout;

  private int SECONDS = 1000;
  private int MINUTES = 60 * SECONDS;
  private int HOURS = 60 * MINUTES;

  private final int insertTimeout = 10 * SECONDS;
  private final double minFragmentsAfterInsert = 2.0;

  private final int expireNeighborsDelayAfterJoin = 5 * SECONDS;
  private final int expireNeighborsInterval = 20 * SECONDS;
  private final int neighborTimeout = 60 * SECONDS;
  
  private final int syncDelayAfterJoin = 15 * SECONDS;
  private final int syncInterval = 60 * SECONDS;
  private final int syncPartnersPerTrial = 1;

  private final int manifestAggregationFactor = 5;
  
  private final int fragmentRequestMaxAttempts = 3;
  private final int fragmentRequestTimeout = 20 * SECONDS;

  private final int overallRestoreTimeout = 2 * MINUTES;
  
  private final int handoffDelayAfterJoin = 45 * SECONDS;
  private final int handoffInterval = 60 * SECONDS;
  private final int handoffMaxFragments = 100;

  private final double restoreMaxRequestFactor = 4.0;
  private final int restoreMaxBoosts = 2;

  public GlacierImpl(Node theNode, StorageManager fragmentStorageArg, StorageManager theNeighborStorage, int numFragmentsArg, int numSurvivors, MultiringIdFactory factory, String instanceArg, GlacierPolicy policyArg) {
    this.fragmentStorage = new FragmentStorage(fragmentStorageArg);
    this.neighborStorage = theNeighborStorage;
    this.policy = policyArg;
    this.node = theNode;
    this.instance = instanceArg;
    this.endpoint = node.registerApplication(this, instance);
    this.numFragments = numFragmentsArg;
    this.numSurvivors = numSurvivors;
    this.factory = factory;
    this.responsibleRange = null;
    this.id = 0;

    this.continuations = new Hashtable();
    this.timers = new Hashtable();
    this.pastryIdFactory = new PastryIdFactory();
    this.nextContinuationTimeout = -1;

    determineResponsibleRange();

    /* Neighbor requests */

    addContinuation(new GlacierContinuation() {
      long nextTimeout;
      
      public String toString() {
        return "Neighbor continuation";
      }
      public void init() {
        nextTimeout = System.currentTimeMillis() + expireNeighborsDelayAfterJoin;

        LeafSet leafSet = ((DistPastryNode) ((MultiringNode) node).getNode()).getLeafSet();
        NodeHandle cwExtreme = leafSet.get(leafSet.cwSize());
        NodeHandle ccwExtreme = leafSet.get(-leafSet.ccwSize());
        IdRange leafRange = pastryIdFactory.buildIdRange(ccwExtreme.getId(), cwExtreme.getId());
    
        for (int k = 0; k < leafSet.size(); k++) {
          if ((leafSet.get(k) != null) && !leafSet.get(k).getId().equals(((RingId)getLocalNodeHandle().getId()).getId())) {
            neighborSeen(leafSet.get(k).getId(), System.currentTimeMillis());
            log("Asking "+leafSet.get(k).getId()+" about neighbors in "+leafRange);
            endpoint.route(
              null,
              new GlacierNeighborRequestMessage(getMyUID(), leafRange, getLocalNodeHandle(), leafSet.get(k).getId()),
              leafSet.get(k)
            );
          }
        }
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierNeighborResponseMessage) {
          final GlacierNeighborResponseMessage gnrm = (GlacierNeighborResponseMessage) o;
          log("NeighborResponse from "+gnrm.getSource()+" with "+gnrm.numNeighbors()+" neighbors");
          for (int i=0; i<gnrm.numNeighbors(); i++)
            neighborSeen(gnrm.getNeighbor(i), gnrm.getLastSeen(i));
        } else {
          warn("Unknown response in neighbor continuation: "+o+" -- discarded");
        }
      }
      public void receiveException(Exception e) {
        warn("Exception in neighbor continuation: "+e);
        e.printStackTrace();
        terminate();
      }
      public void timeoutExpired() {
        nextTimeout += expireNeighborsInterval;

        final long earliestAcceptableDate = System.currentTimeMillis() - neighborTimeout;
        IdSet allNeighbors = neighborStorage.scan();
        Iterator iter = allNeighbors.getIterator();
        LeafSet leafSet = ((DistPastryNode) ((MultiringNode) node).getNode()).getLeafSet();

        log("Checking neighborhood for expired certificates...");
        
        while (iter.hasNext()) {
          final Id thisNeighbor = (Id) iter.next();

          if (leafSet.get((NodeId)thisNeighbor) != null) {
            log("CNE: Refreshing current neighbor: "+thisNeighbor);
            neighborSeen(thisNeighbor, System.currentTimeMillis());
          } else {
            log("CNE: Retrieving "+thisNeighbor);
            neighborStorage.getObject(thisNeighbor, new Continuation() {
              public void receiveResult(Object o) {
                if (o==null) {
                  warn("CNE: Cannot retrieve neighbor "+thisNeighbor);
                  return;
                }
              
                long lastSeen = ((Long)o).longValue();
                if (lastSeen < earliestAcceptableDate) {
                  log("CNE: Removing expired neighbor "+thisNeighbor+" ("+lastSeen+"<"+earliestAcceptableDate+")");
                  neighborStorage.unstore(thisNeighbor, new Continuation() {
                    public void receiveResult(Object o) {
                      log("CNE unstore successful: "+thisNeighbor+", returned "+o);
                    }
                    public void receiveException(Exception e) {
                      warn("CNE unstore failed: "+thisNeighbor+", returned "+e);
                    }
                  });
                } else {
                  log("CNE: Neighbor "+thisNeighbor+" still active, last seen "+lastSeen);
                }
              }
              public void receiveException(Exception e) {
                log("CNE: Exception while retrieving neighbor "+thisNeighbor+", e="+e);
              }
            });
          }
        }
        
        determineResponsibleRange();
      }
      public long getTimeout() {
        return nextTimeout;
      }
    });

    /* Sync */

    addContinuation(new GlacierContinuation() {
      long nextTimeout;
      Random rand;
      int offset;
      
      public String toString() {
        return "Sync continuation";
      }
      public void init() {
        nextTimeout = System.currentTimeMillis() + syncDelayAfterJoin;
        rand = new Random();
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierRangeResponseMessage) {
          final GlacierRangeResponseMessage grrm = (GlacierRangeResponseMessage) o;

          Id ccwId = getFragmentLocation(grrm.getCommonRange().getCCWId(), numFragments-offset, 0);
          Id cwId = getFragmentLocation(grrm.getCommonRange().getCWId(), numFragments-offset, 0);
          IdRange originalRange = pastryIdFactory.buildIdRange(ccwId, cwId);
        
          log("Range response (offset: "+offset+"): "+grrm.getCommonRange()+", original="+originalRange);
        
          IdSet keySet = fragmentStorage.scan();
          BloomFilter bv = new BloomFilter((keySet.numElements()+5)*4, 3);
          Iterator iter = keySet.getIterator();

          while (iter.hasNext()) {
            FragmentKey fkey = (FragmentKey)iter.next();
            Id thisPos = ((RingId)getFragmentLocation(fkey)).getId();
            if (originalRange.containsId(thisPos)) {
              log(" - Adding "+fkey+" as "+fkey.getVersionKey().getId());
/***********************MUST HASH SOMETHING ELSE: OBJHASH + EXPIRATION DATE?******/
              bv.add(fkey.getVersionKey().toByteArray());
            }
          }
        
          log(keySet.numElements()+" keys added, sending sync request...");
        
          endpoint.route(
            null,
            new GlacierSyncMessage(getUID(), grrm.getCommonRange(), offset, bv, getLocalNodeHandle(), grrm.getSource().getId()),
            grrm.getSource()
          );
        } else {
          warn("Unknown result in sync continuation: "+o+" -- discarded");
        }
      }
      public void receiveException(Exception e) {
        warn("Exception in neighbor continuation: "+e);
        e.printStackTrace();
        terminate();
      }
      public void timeoutExpired() {
        offset = 1+rand.nextInt(numFragments-1);
        nextTimeout += syncInterval;

        Id dest = getFragmentLocation(getLocalNodeHandle().getId(), offset, 0);
        Id ccwId = getFragmentLocation(responsibleRange.getCCWId(), offset, 0);
        Id cwId = getFragmentLocation(responsibleRange.getCWId(), offset, 0);
        IdRange requestedRange = pastryIdFactory.buildIdRange(ccwId, cwId);
            
        log("Sending range query for ("+requestedRange+") to "+dest);
        endpoint.route(
          dest,
          new GlacierRangeQueryMessage(getMyUID(), requestedRange, getLocalNodeHandle(), dest),
          null
        );
      }
      public long getTimeout() {
        return nextTimeout;
      }
    });
    
    /* Handoff */
    
    addContinuation(new GlacierContinuation() {
      long nextTimeout;

      public String toString() {
        return "Handoff continuation";
      }
      public void init() {
        nextTimeout = System.currentTimeMillis() + handoffDelayAfterJoin;
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierResponseMessage) {
          GlacierResponseMessage grm = (GlacierResponseMessage) o;
          log("Received handoff response from "+grm.getSource().getId()+" with "+grm.numKeys()+" keys");
          for (int i=0; i<grm.numKeys(); i++) {
            final FragmentKey thisKey = grm.getKey(i);
            if (grm.getHaveIt(i) && grm.getAuthoritative(i)) {
              Id thisPos = ((RingId)getFragmentLocation(thisKey)).getId();
              if (!responsibleRange.containsId(thisPos)) {
                log("Deleting fragment "+thisKey);
                fragmentStorage.unstore(thisKey, new Continuation() {
                  public void receiveResult(Object o) {
                    log("Handed off fragment deleted: "+thisKey+" (o="+o+")");
                  }
                  public void receiveException(Exception e) {
                    warn("Delete failed during handoff: "+thisKey+", returned "+e);
                    e.printStackTrace();
                  }
                });
              } else {
                warn("Handoff response for "+thisKey+", for which I am still responsible (attack?) -- ignored");
              }
            } else {
              log("Ignoring fragment "+thisKey+" (haveIt="+grm.getHaveIt(i)+", authoritative="+grm.getAuthoritative(i)+")");
            }
          }
        } else {
          warn("Unexpected response in handoff continuation: "+o+" -- ignored");
        }  
      }
      public void receiveException(Exception e) {
        warn("Exception in handoff continuation: "+e);
        e.printStackTrace();
      }
      public void timeoutExpired() {
        nextTimeout += handoffInterval;
        log("Checking fragment storage for fragments to hand off...");
        log("Currently responsible for: "+responsibleRange);
        Iterator iter = fragmentStorage.scan().getIterator();
        Vector handoffs = new Vector();
        Id destination = null;
  
        while (iter.hasNext()) {
          FragmentKey fkey = (FragmentKey) iter.next();
          RingId thisRingId = (RingId)getFragmentLocation(fkey);
          Id thisPos = thisRingId.getId();
          if (!responsibleRange.containsId(thisPos)) {
            log("Must hand off "+fkey+" @"+thisPos);
            handoffs.add(fkey);
            if (destination == null)
              destination = thisRingId;
          }
        }
        
        if (destination == null) {
          log("Nothing to hand off -- returning");
          return;
        }
        
        int numHandoffs = Math.min(handoffs.size(), handoffMaxFragments);
        log("Handing off "+numHandoffs+" fragments (out of "+handoffs.size()+")");
        FragmentKey[] keys = new FragmentKey[handoffs.size()];
        for (int i=0; i<handoffs.size(); i++)
          keys[i] = (FragmentKey) handoffs.elementAt(i);

        endpoint.route(
          destination,
          new GlacierQueryMessage(getMyUID(), keys, getLocalNodeHandle(), destination),
          null
        );
      }
      public long getTimeout() {
        return nextTimeout;
      }
    });
  }

  private void addContinuation(GlacierContinuation gc) {
    int thisUID = getUID();
    gc.setup(thisUID);
    continuations.put(new Integer(thisUID), gc);
    gc.init();
    
    long thisTimeout = gc.getTimeout();
    long now = System.currentTimeMillis();
    
    if ((nextContinuationTimeout == -1) || (thisTimeout < nextContinuationTimeout)) {
      if (nextContinuationTimeout != -1)
        removeTimer(tiContinuation);
      
      nextContinuationTimeout = thisTimeout;
      if (nextContinuationTimeout > now)
        addTimer((int)(nextContinuationTimeout - now), tiContinuation);
      else
        timerExpired(tiContinuation);
    }
  }

  private void determineResponsibleRange() {
    Id cwPeer = null, ccwPeer = null, myNodeId = ((RingId)getLocalNodeHandle().getId()).getId();
    
    log("Determining responsible range");
    
    Iterator iter = neighborStorage.scan().getIterator();
    while (iter.hasNext()) {
      Id thisNeighbor = (Id)iter.next();
      log("Considering neighbor: "+thisNeighbor);
      if (myNodeId.clockwise(thisNeighbor)) {
        if ((cwPeer == null) || thisNeighbor.isBetween(myNodeId, cwPeer))
          cwPeer = thisNeighbor;
      } else {
        if ((ccwPeer == null) || thisNeighbor.isBetween(ccwPeer, myNodeId))
          ccwPeer = thisNeighbor;
      }
    }
          
    if (ccwPeer == null)
      ccwPeer = cwPeer;
    if (cwPeer == null)
      cwPeer = ccwPeer;
      
    log("CCW: "+ccwPeer+" CW: "+cwPeer+" ME: "+myNodeId);
      
    if ((ccwPeer == null) || (cwPeer == null)) {
      responsibleRange = pastryIdFactory.buildIdRange(myNodeId, myNodeId);
      return;
    }
    
    Id.Distance ccwHalfDistance;
    if (!myNodeId.clockwise(ccwPeer))
      ccwHalfDistance = ccwPeer.distanceFromId(myNodeId).shiftDistance(1,0);
    else
      ccwHalfDistance = ccwPeer.longDistanceFromId(myNodeId).shiftDistance(1,0);

    Id.Distance cwHalfDistance;
    if (myNodeId.clockwise(cwPeer))
      cwHalfDistance = cwPeer.distanceFromId(myNodeId).shiftDistance(1,0);
    else
      cwHalfDistance = cwPeer.longDistanceFromId(myNodeId).shiftDistance(1,0);
    
    responsibleRange = pastryIdFactory.buildIdRange(
      ccwPeer.addToId(ccwHalfDistance),
      myNodeId.addToId(cwHalfDistance)
    );
    
    log("New range: "+responsibleRange);
  }

  private void log(String str) {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h + ":" + m + ":" + s + " @" + node.getId() + " " + str);
  }

  private void warn(String str) {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h + ":" + m + ":" + s + " @" + node.getId() + " *** WARNING *** " + str);
  }

  private void unusual(String str) {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h + ":" + m + ":" + s + " @" + node.getId() + " *** UNUSUAL *** " + str);
  }

  protected synchronized int getUID() {
    return id++;
  }

  /**
   * Schedule a timer event
   *
   * @param timeoutMsec Length of the delay (in milliseconds)
   * @param timeoutID Identifier (to distinguish between multiple timers)
   */
  private void addTimer(int timeoutMsec, char timeoutID) {
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
   * Determines the point in the ring where a particular fragment should
   * be stored. 
   *
   * @param objectKey Key of the original object (from PAST)
   * @param fragmentNr Fragment number (0..n-1)
   * @return The location of the fragment
   */
  private Id getFragmentLocation(Id objectKey, int fragmentNr, long version) {
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

      double vOffset = version * (1.0/2.7182821);
      vOffset -= (long)vOffset;
      
      f += (((float)fragmentNr) / ((float)numFragments)) + vOffset;
      while (f>=1)
        f -= 1;

      /* Convert the floating point value back to an ID */

      rice.pastry.Id result = rice.pastry.Id.build();
      d = 0.5;
      for (int i = 0; i < realId.IdBitLength; i++) {
        if (f >= d) {
          result.setBit((realId.IdBitLength - 1 - i), 1);
          f -= d;
        }
        d /= 2;
      }

      return result;
    } else {
      RingId rok = (RingId) objectKey;
      return factory.buildRingId(rok.getRingId(), getFragmentLocation(rok.getId(), fragmentNr, version));
    }
  }
  
  private Id getFragmentLocation(FragmentKey fkey) {
    return getFragmentLocation(
      fkey.getVersionKey().getId(),
      fkey.getFragmentID(),
      fkey.getVersionKey().getVersion()
    );
  }
  
  protected boolean amResponsibleFor(Id id) {
    if (id instanceof RingId) {
      return responsibleRange.containsId(((RingId)id).getId());
    } else return responsibleRange.containsId(id);
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

  public String handleDebugCommand(String command)
  {
    String myInstance = "glacier."+instance.substring(instance.lastIndexOf("-") + 1);
    String requestedInstance = command.substring(0, command.indexOf(" "));
    
    System.out.println("MINE |"+myInstance+"| REQ |"+requestedInstance+"|");
  
    if (!requestedInstance.equals(myInstance))
      return null;
  
    String cmd = command.substring(myInstance.length() + 1);
    System.out.println("CMD |"+cmd+"|");
  
    if ((cmd.length() >= 2) && cmd.substring(0, 2).equals("ls")) {
      FragmentKeySet keyset = (FragmentKeySet) fragmentStorage.scan();
      Iterator iter = keyset.getIterator();
      String result = "";
      
      result = result + keyset.numElements()+ " objects\n";
      
      while (iter.hasNext()) {
        FragmentKey thisKey = (FragmentKey) iter.next();
        boolean isMine = amResponsibleFor(getFragmentLocation(thisKey));
        result = result + ((Id)thisKey).toStringFull()+" "+(isMine ? "OK" : "MIGRATE")+"\n";
      }
      
      return result;
    }

    if ((cmd.length() >= 5) && cmd.substring(0, 5).equals("flush")) {
      FragmentKeySet keyset = (FragmentKeySet) fragmentStorage.scan();
      Iterator iter = keyset.getIterator();
      
      while (iter.hasNext()) {
        FragmentKey thisKey = (FragmentKey) iter.next();
        fragmentStorage.unstore(thisKey, new Continuation() {
          public void receiveResult(Object o) {}
          public void receiveException(Exception e) {}
        });
      }

      return keyset.numElements()+ " objects deleted\n";
    }

    if ((cmd.length() >= 9) && cmd.substring(0, 9).equals("neighbors")) {
      Iterator iter = neighborStorage.scan().getIterator();
      String result = "";

      result = result + neighborStorage.scan().numElements()+ " neighbors\n";
      
      while (iter.hasNext())
        result = result + ((Id)iter.next()).toString()+"\n";
        
      return result;
    }
    
    if ((cmd.length() >= 6) && cmd.substring(0, 6).equals("status")) {
      String result = "";
      result = result + "Responsible for: "+responsibleRange + "\n";
      result = result + "Local time: "+(new Date()) + "\n\n";
      result = result + fragmentStorage.scan().numElements() + " fragments\n";
      result = result + neighborStorage.scan().numElements() + " neighbors\n";
      return result;
    }

    if ((cmd.length() >= 6) && cmd.substring(0, 6).equals("insert")) {
      int numObjects = Integer.parseInt(cmd.substring(7));
      String result = "";
      
      for (int i=0; i<numObjects; i++) {
        Id randomID = rice.pastry.Id.makeRandomId(new Random());
        result = result + randomID.toStringFull() + "\n";
        insert(
          new DebugContent(factory.buildRingId(((RingId)getLocalNodeHandle().getId()).getRingId(), randomID), false, 0),
          System.currentTimeMillis() + 30*SECONDS,
          new Continuation() {
            public void receiveResult(Object o) {
            }
            public void receiveException(Exception e) {
            }
          }
        );
      }
      
      return result + numObjects + " object(s) created\n";
    }
    
    return null;
  }

  public void insert(final PastContent obj, final Continuation command) {
    insert(obj, System.currentTimeMillis() + 30*SECONDS, command);
  }

  public long extractVersion(Object o) {
    if (o instanceof GCPastContent) {
      return ((GCPastContent)o).getVersion();
    } else if (o instanceof SignedData) {
      SignedData sd = (SignedData) o;
      byte[] timestamp = sd.getTimestamp();
      if (timestamp.length > 8) 
        throw new RuntimeException("Timestamp in SignedData exceeds 8 bytes!");
      
      long ts = 0;
      for (int i=0; i<timestamp.length; i++)
        ts = (ts<<8) + ((timestamp[i]<0) ? (256+timestamp[i]) : timestamp[i]);
      return ts;
    } else if (o instanceof DebugContent) {
      return ((DebugContent) o).getVersion();
    } else {
      return 0;
    }
  }

  public void refresh(final Id[] id, final long expiration, final Continuation command) {
    command.receiveResult(new Object[] { new Boolean(true) });
    /* Implement me */
  }

  public void insert(final PastContent obj, long expiration, final Continuation command) {
    log("Insert " + obj + " " + command + " (mutable=" + obj.isMutable() + ")");

    long theVersion = extractVersion(obj);
    final VersionKey vkey = new VersionKey(obj.getId(), theVersion);

    final Fragment[] fragments = policy.encodeObject(obj);
    if (fragments == null) {
      command.receiveException(new GlacierException("Cannot encode object"));
      return;
    }

    final StorageManifest[] manifests = policy.createManifests(vkey, obj, fragments, expiration);
    if (manifests == null) {
      command.receiveException(new GlacierException("Cannot create manifests"));
      return;
    }

    final long tStart = System.currentTimeMillis();
    addContinuation(new GlacierContinuation() {
      NodeHandle[] holder;
      boolean[] receiptReceived;

      public String toString() {
        return "Insert continuation for "+vkey;
      }
      public int numReceiptsReceived() {
        int result = 0;
        for (int i=0; i<receiptReceived.length; i++)
          if (receiptReceived[i])
            result ++;
        return result;
      }
      public void init() {
        log("Initializing insert continuation for " + vkey);
        holder = new NodeHandle[numFragments];
        receiptReceived = new boolean[numFragments];

        /* Send queries */
        
        log("Sending queries for " + vkey);
        for (int i = 0; i < numFragments; i++) {
          Id fragmentLoc = getFragmentLocation(vkey.getId(), i, vkey.getVersion());
          FragmentKey keys[] = new FragmentKey[1];
          keys[0] = new FragmentKey(vkey, i);
      
          log("Query #"+i+" to "+fragmentLoc);
      
          endpoint.route(
            fragmentLoc,
            new GlacierQueryMessage(getMyUID(), keys, getLocalNodeHandle(), fragmentLoc),
            null
          );
        }
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierResponseMessage) {
          GlacierResponseMessage grm = (GlacierResponseMessage) o;
          if (!grm.getKey(0).getVersionKey().equals(vkey)) {
            warn("Insert response got routed to the wrong key: "+vkey);
            return;
          }

          int fragmentID = grm.getKey(0).getFragmentID();
          if (fragmentID < numFragments) {
            if (grm.getAuthoritative(0)) {
              if (!grm.getHaveIt(0)) {
                if (holder[fragmentID] == null) {
                  holder[fragmentID] = grm.getSource();
                  log("Got insert response, sending fragment "+grm.getKey(0));
                  endpoint.route(
                    null,
                    new GlacierDataMessage(getMyUID(), grm.getKey(0), fragments[fragmentID], manifests[fragmentID], getLocalNodeHandle(), grm.getSource().getId(), false),
                    grm.getSource()
                  );
                } else {
                  warn("Received two insert responses for the same fragment -- discarded");
                }
              } else {
                if ((holder[fragmentID] != null) && (holder[fragmentID].equals(grm.getSource()))) {
                  log("Receipt received after insert: "+grm.getKey(0));
                  receiptReceived[fragmentID] = true;
                  if (numReceiptsReceived() == numFragments)
                    timeoutExpired();
                } else { 
                  warn("Receipt received from another source (expecting "+holder[fragmentID]+")");
                }
              }
            } else {
              log("Insert response, but not authoritative -- ignoring");
            }
          } else {
            warn("Fragment ID too large in insert response -- discarded");
          }
          
          return;
        } else {
          warn("Unknown response to insert continuation: "+o+" -- discarded");
        }
      }
      public void receiveException(Exception e) {
        warn("Exception while inserting "+vkey+": "+e);
        e.printStackTrace();
        command.receiveException(new GlacierException("Exception while inserting: "+e));
        terminate();
      }
      public void timeoutExpired() {
        int minAcceptable = (int)(numSurvivors * minFragmentsAfterInsert);
        if (numReceiptsReceived() >= minAcceptable) {
          log("Insertion of "+vkey+" successful, "+numReceiptsReceived()+"/"+numFragments+" receipts received");
          Boolean[] result = new Boolean[1];
          result[0] = new Boolean(true);
          command.receiveResult(result);
        } else {
          warn("Insertion of "+vkey+" failed, only "+numReceiptsReceived()+"/"+numFragments+" receipts received");
          command.receiveException(new GlacierException("Insert failed, did not receive enough receipts"));
        }

        terminate();
      }
      public long getTimeout() {
        return tStart + insertTimeout;
      }
    });
  }

  private void timerExpired(char timerID) {
    log("TIMER EXPIRED: #" + (int) timerID);

    switch (timerID) {
      case tiContinuation:
      {
        boolean foundTerminated = false;
        long earliestTimeout;
        int numDelete;
        
        do {
          long now = System.currentTimeMillis();
          int[] deleteList = new int[100];
          numDelete = 0;
          earliestTimeout = -1;
        
          log("Timer run at "+now);
          
          Enumeration enu = continuations.elements();
          while (enu.hasMoreElements()) {
            GlacierContinuation gc = (GlacierContinuation) enu.nextElement();

            if (!gc.hasTerminated() && gc.getTimeout() < (now + 1*SECONDS)) {
              log("Timer: Resuming ["+gc+"]");
              gc.timeoutExpired();
              if (!gc.hasTerminated() && (gc.getTimeout() < (now + 1*SECONDS)))
                panic("Continuation does not set new timeout: "+gc);
            }
            
            if (!gc.hasTerminated()) {
              if ((earliestTimeout == -1) || (gc.getTimeout() < earliestTimeout))
                earliestTimeout = gc.getTimeout();
            } else {
              if (numDelete < 100)
                deleteList[numDelete++] = gc.getMyUID();
            }
          }

          if (numDelete > 0) {          
            log("Deleting "+numDelete+" expired continuations");
            for (int i=0; i<numDelete; i++)
              continuations.remove(new Integer(deleteList[i]));
          }
            
        } while ((numDelete == 100) || ((earliestTimeout >= 0) && (earliestTimeout < System.currentTimeMillis())));

        if (earliestTimeout >= 0) {
          log("Next timeout is at "+earliestTimeout);
          addTimer((int)Math.max(earliestTimeout - System.currentTimeMillis(), 1*SECONDS), tiContinuation);
        } else log("No more timeouts");
        
        break;
      }
      default:
      {
        panic("Unknown timer expired: " + (int) timerID);
      }
    }
  }

  public boolean isPlausibleSource(NodeHandle source, Id id) {
    /* perform density check to see if <source> might be responsible for <id> */
    return true;
  }

  public void neighborSeen(Id nodeId, long when) {

    if (nodeId.equals(((RingId)getLocalNodeHandle().getId()).getId()))
      return;

    log("Neighbor: "+nodeId+" was seen at "+when);

    if (when > System.currentTimeMillis()) {
      warn("Neighbor: "+when+" is in the future (now="+System.currentTimeMillis()+")");
      when = System.currentTimeMillis();
    }

    final Id fNodeId = nodeId;
    final long fWhen = when;

    neighborStorage.getObject(nodeId, 
      new Continuation() {
        public void receiveResult(Object o) {
          log("Continue: neighborSeen ("+fNodeId+", "+fWhen+") after getObject");

          final long previousWhen = (o!=null) ? ((Long)o).longValue() : 0;
          log("Neighbor: "+fNodeId+" previously seen at "+previousWhen);
          if (previousWhen >= fWhen) {
            log("Neighbor: No update needed (new TS="+fWhen+")");
            return;
          }
          
          neighborStorage.store(fNodeId, new Long(fWhen),
            new Continuation() {
              public void receiveResult(Object o) {
                log("Continue: neighborSeen ("+fNodeId+", "+fWhen+") after store");
                log("Neighbor: Updated "+fNodeId+" from "+previousWhen+" to "+fWhen);
                determineResponsibleRange();
              }
              public void receiveException(Exception e) {
                warn("receiveException(" + e + ") while storing a neighbor ("+fNodeId+")");
              }
            }
          );
        }
        public void receiveException(Exception e) {
          warn("receiveException(" + e + ") while retrieving a neighbor ("+fNodeId+")");
        }
      }
    );
  }

  public boolean forward(final RouteMessage message) {
    return true;
  }
  
  public void update(NodeHandle handle, boolean joined) {
    log("LEAFSET UPDATE: " + handle + " has " + (joined ? "joined" : "left"));

    if (!joined)
      return;

    neighborSeen(((RingId)handle.getId()).getId(), System.currentTimeMillis());
  }

  public void lookupHandles(Id id, int num, Continuation command) {
    panic("lookupHandles("+id+", "+num+") called on Glacier");
  }

  public void lookup(Id id, boolean cache, Continuation command) {
    VersionKey vkey = new VersionKey(id, 0);
    log("glacier.lookup("+id+", cache="+cache+")");
    retrieveObject(vkey, null, true, command);
  }

  public void lookup(Id id, long version, Continuation command) {
    VersionKey vkey = new VersionKey(id, version);
    log("glacier.lookup("+id+", version="+version+")");
    retrieveObject(vkey, null, true, command);
  }

  public void lookup(Id id, Continuation command) {
    lookup(id, true, command);
  }

  public void fetch(PastContentHandle handle, Continuation command) {
    panic("fetch("+handle+") called on Glacier");
  }

  public void retrieveObject(final VersionKey key, final StorageManifest manifest, final boolean beStrict, final Continuation c) {
    addContinuation(new GlacierContinuation() {
      protected boolean checkedFragment[];
      protected Fragment haveFragment[];
      protected int attemptsLeft;
      protected long timeout;

      public int numHaveFragments() {
        int result = 0;
        for (int i=0; i<haveFragment.length; i++)
          if (haveFragment[i] != null)
            result ++;
        return result;
      }
      public int numCheckedFragments() {
        int result = 0;
        for (int i=0; i<checkedFragment.length; i++)
          if (checkedFragment[i])
            result ++;
        return result;
      }
      public String toString() {
        return "retrieveObject("+key+")";
      }
      public void init() {
        checkedFragment = new boolean[numFragments];
        haveFragment = new Fragment[numFragments];
        for (int i = 0; i < numFragments; i++) {
          checkedFragment[i] = false;
          haveFragment[i] = null;
        }
        timeout = System.currentTimeMillis();
        attemptsLeft = restoreMaxBoosts;
        timeoutExpired();
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierDataMessage) {
          GlacierDataMessage gdm = (GlacierDataMessage) o;
          int fragmentID = gdm.getKey(0).getFragmentID();
          
          if (!gdm.getKey(0).getVersionKey().equals(key) || (fragmentID<0) || (fragmentID>=numFragments)) {
            warn("retrieveObject: Bad data message (contains "+gdm.getKey(0)+", expected "+key);
            return;
          }
          
          Fragment thisFragment = gdm.getFragment(0);
          if (thisFragment == null) {
            warn("retrieveObject: No fragment -- discarded");
            return;
          }
          
          if (!checkedFragment[fragmentID]) {
            warn("retrieveObject: Got fragment #"+fragmentID+", but we never requested it -- ignored");
            return;
          }
            
          if (haveFragment[fragmentID] != null) {
            warn("retrieveObject: Got duplicate fragment #"+fragmentID+" -- discarded");
            return;
          }
            
          if ((manifest!=null) && !manifest.validatesFragment(thisFragment, fragmentID)) {
            warn("Got invalid fragment #"+fragmentID+" -- discarded");
            return;
          }
            
          log("retrieveObject: Received fragment #"+fragmentID+" for "+gdm.getKey(0));
          haveFragment[fragmentID] = thisFragment;
          if (numHaveFragments() >= numSurvivors) {

            /* Restore the object */
            
            Fragment[] material = new Fragment[numFragments];
            int numAdded = 0;

            for (int j = 0; j < numFragments; j++) {
              if ((haveFragment[j] != null) && (numAdded < numSurvivors)) {
                material[j] = haveFragment[j];
                numAdded ++;
              } else {
                material[j] = null;
              }
            }

            log("Decode object: " + key);
            Serializable theObject = policy.decodeObject(material);
            log("Decode complete: " + key);

            if ((theObject == null) || !(theObject instanceof PastContent)) {
              warn("retrieveObject: Decoder delivered "+theObject+", unexpected -- failed");
              c.receiveException(new GlacierException("Decoder delivered "+theObject+", unexpected -- failed"));
            } else {
              c.receiveResult(theObject);
            }
            
            terminate();
          }
        } else if (o instanceof GlacierResponseMessage) {
          log("Fragment "+((GlacierResponseMessage)o).getKey(0)+" not available");
          if (numCheckedFragments() < numFragments)
            sendRandomRequest();
        } else {
          warn("retrieveObject: Unexpected result: "+o);
        }
        
        return;
      }
      public void receiveException(Exception e) {
        warn("retrieveObject: Exception "+e);
        e.printStackTrace();
        c.receiveException(e);
        terminate();
      }
      public void sendRandomRequest() {
        Random rand = new Random();
        int nextID;

        do {
          nextID = rand.nextInt(numFragments);
        } while (checkedFragment[nextID]);
     
        checkedFragment[nextID] = true;
        FragmentKey nextKey = new FragmentKey(key, nextID);
        Id nextLocation = getFragmentLocation(nextKey);
        log("retrieveObject: Asking "+nextLocation+" for "+nextKey);
        endpoint.route(
          nextLocation,
          new GlacierFetchMessage(getMyUID(), nextKey, getLocalNodeHandle(), nextLocation),
          null
        );
      }
      public void timeoutExpired() {
        if (attemptsLeft > 0) {
          log("retrieveObject: Retrying ("+attemptsLeft+" attempts left)");
          timeout = timeout + fragmentRequestTimeout;
          attemptsLeft --;

          int numRequests = numSurvivors - numHaveFragments();
          if ((attemptsLeft == 0) && beStrict)
            numRequests = numFragments - numCheckedFragments();
            
          for (int i=0; (i<numRequests) && (numCheckedFragments() < numFragments); i++) {
            sendRandomRequest();
          }
        } else {
          log("retrieveObject: Giving up on "+key+" ("+restoreMaxBoosts+" attempts, "+numCheckedFragments()+" checked, "+numHaveFragments()+" gotten)");
          c.receiveException(new GlacierException("Maximum number of attempts ("+restoreMaxBoosts+") reached for key "+key));
          terminate();
        }
      }
      public long getTimeout() {
        return timeout;
      }
    });
  }
          
  public void retrieveFragment(final FragmentKey key, final StorageManifest manifest, final GlacierContinuation c) {
    addContinuation(new GlacierContinuation() {
      protected int attemptsLeft;
      protected boolean inPhaseTwo;
      protected long timeout;
      
      public String toString() {
        return "retrieveFragment("+key+")";
      }
      public void init() {
        attemptsLeft = fragmentRequestMaxAttempts;
        timeout = System.currentTimeMillis();
        inPhaseTwo = false;
        timeoutExpired();
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierResponseMessage) {
          GlacierResponseMessage grm = (GlacierResponseMessage) o;
          if (!grm.getKey(0).equals(key)) {
            warn("retrieveFragment: Response does not match key "+key+" -- discarded");
            return;
          }
          
          if ((attemptsLeft > 0) && !grm.getHaveIt(0)) {
            attemptsLeft = 0;
            timeoutExpired();
          } else {
            warn("retrieveFragment: Unexpected GlacierResponseMessage: "+grm+" (key="+key+")");
          }
          
          return;
        } 
        
        if (o instanceof GlacierDataMessage) {
          GlacierDataMessage gdm = (GlacierDataMessage) o;
          if (!gdm.getKey(0).equals(key)) {
            warn("retrieveFragment: Data does not match key "+key+" -- discarded");
            return;
          }
          
          Fragment thisFragment = gdm.getFragment(0);
          if (thisFragment == null) {
            warn("retrieveFragment: Manifest only?!?");
            return;
          }
          
          if (!manifest.validatesFragment(thisFragment, gdm.getKey(0).getFragmentID())) {
            warn("Invalid fragment "+gdm.getKey(0)+" returned by primary -- ignored");
            return;
          }

          c.receiveResult(thisFragment);
          terminate();
          return;
        }
        
        warn("retrieveFragment: Unknown result "+o+" (key="+key+")");
      }
      public void receiveException(Exception e) {
        warn("retrieveFragment: Exception "+e);
        e.printStackTrace();
        c.receiveException(e);
        terminate();
      }
      public void timeoutExpired() {
        if (attemptsLeft > 0) {
          log("retrieveFragment: Retrying ("+attemptsLeft+" attempts left)");
          timeout = timeout + fragmentRequestTimeout;
          attemptsLeft --;
          endpoint.route(
            key.getVersionKey().getId(),
            new GlacierFetchMessage(getMyUID(), key, getLocalNodeHandle(), key.getVersionKey().getId()),
            null
          );
        } else {
          timeout = timeout + 3 * restoreMaxBoosts * fragmentRequestTimeout;
          if (inPhaseTwo) {
            warn("retrieveFragment: Already in phase two");
          }
          inPhaseTwo = true;
          
          retrieveObject(key.getVersionKey(), manifest, false, new Continuation() {
            public void receiveResult(Object o) {
              if (o == null) {
                warn("retrieveFragment: retrieveObject("+key.getVersionKey()+") failed, returns null");
                c.receiveException(new GlacierException("Cannot restore either the object or the fragment -- try again later!"));
                return;
              }
              
              log("Reencode object: " + key.getVersionKey());
              Fragment[] frag = policy.encodeObject((PastContent) o);
              log("Reencode complete: " + key.getVersionKey());
                
              if (!manifest.validatesFragment(frag[key.getFragmentID()], key.getFragmentID())) {
                warn("Reconstructed fragment #"+key.getFragmentID()+" does not match manifest ??!?");
                c.receiveException(new GlacierException("Recovered object, but cannot re-encode it (strange!) -- try again later!"));
                return;
              }
              
              c.receiveResult(frag[key.getFragmentID()]);
            }
            public void receiveException(Exception e) {
              c.receiveException(e);
            }
          });
          
          terminate();
        }
      }
      public long getTimeout() {
        return timeout;
      }
    });
  }

  public void deliver(Id id, Message message) {

    final GlacierMessage msg = (GlacierMessage) message;
    log("Received message " + msg + " with destination " + id + " from " + msg.getSource().getId());

    if (msg.isResponse()) {
      GlacierContinuation gc = (GlacierContinuation) continuations.get(new Integer(msg.getUID()));

      if (gc != null) {
        log("Resuming ["+gc+"]");
        gc.receiveResult(msg);
        log("---");
      } else {
        warn("Message UID#"+msg.getUID()+" is response, but continuation not found");
      }
       
      return;
    }

    if (msg instanceof GlacierQueryMessage) {

      /* When a QueryMessage arrives, we check whether we have the fragment
         with the corresponding key and then send back a ResponseMessage. */

      GlacierQueryMessage gqm = (GlacierQueryMessage) msg;
      FragmentKey[] keyA = new FragmentKey[gqm.numKeys()];
      boolean[] haveItA = new boolean[gqm.numKeys()];
      boolean[] authoritativeA = new boolean[gqm.numKeys()];
      
      for (int i=0; i<gqm.numKeys(); i++) {
        Id fragmentLocation = getFragmentLocation(gqm.getKey(i));
        log("Queried for " + gqm.getKey(i) + " (at "+fragmentLocation+")");
  
        keyA[i] = gqm.getKey(i);
        haveItA[i] = fragmentStorage.exists(gqm.getKey(i));
        log("My range is "+responsibleRange);
        log("Location is "+fragmentLocation);
        authoritativeA[i] = amResponsibleFor(fragmentLocation);
        log("Result: haveIt="+haveItA[i]+" amAuthority="+authoritativeA[i]);
      }
      
      endpoint.route(
        null,
        new GlacierResponseMessage(gqm.getUID(), keyA, haveItA, authoritativeA, getLocalNodeHandle(), gqm.getSource().getId(), true),
        gqm.getSource()
      );

    } else if (msg instanceof GlacierNeighborRequestMessage) {
      final GlacierNeighborRequestMessage gnrm = (GlacierNeighborRequestMessage) msg;
      
      neighborStorage.scan(gnrm.getRequestedRange(), new Continuation() {
        int numRequested;
        Id[] neighbors;
        long[] lastSeen;
        int currentLookup;
        
        public void receiveResult(Object o) {
          log("Continue: NeighborRequest from "+gnrm.getSource().getId()+" for range "+gnrm.getRequestedRange());
        
          if (o == null) {
            warn("Problem while retrieving neighbors -- canceled");
            return;
          }
          
          if (o instanceof IdSet) {
            IdSet requestedNeighbors = (IdSet)o;
            numRequested = requestedNeighbors.numElements();
            if (numRequested < 1) {
              log("No neighbors in that range -- canceled");
              return; 
            }
            
            log("Found "+numRequested+" neighbors in range "+gnrm.getRequestedRange()+", retrieving...");
              
            neighbors = new Id[numRequested];
            lastSeen = new long[numRequested];
            
            Iterator iter = requestedNeighbors.getIterator();
            for (int i=0; i<numRequested; i++)
              neighbors[i] = (Id)(iter.next());
              
            currentLookup = 0;
            neighborStorage.getObject(neighbors[currentLookup], this);
          }
          
          if (o instanceof Long) {
            log("Retr: Neighbor "+neighbors[currentLookup]+" was last seen at "+o);
            lastSeen[currentLookup] = ((Long)o).longValue();
            currentLookup ++;
            if (currentLookup < numRequested) {
              neighborStorage.getObject(neighbors[currentLookup], this);
            } else {
              log("Sending neighbor response...");
              endpoint.route(
                null,
                new GlacierNeighborResponseMessage(gnrm.getUID(), neighbors, lastSeen, getLocalNodeHandle(), gnrm.getSource().getId()),
                gnrm.getSource()
              );
            }
          }
        }
        
        public void receiveException(Exception e) {
          warn("Problem while retrieving neighbors in range "+gnrm.getRequestedRange()+" for "+gnrm.getSource()+" -- canceled");
        }
      });
          
      return;      
      
    } else if (msg instanceof GlacierSyncMessage) {
      
      final GlacierSyncMessage gsm = (GlacierSyncMessage) msg;

      log("SYN: Processing SyncRequest from "+gsm.getSource().getId()+" for "+gsm.getRange()+" offset "+gsm.getOffsetFID());
      
      Iterator iter = fragmentStorage.scan().getIterator();
      final IdRange range = gsm.getRange();
      final int offset = gsm.getOffsetFID();
      final BloomFilter bv = gsm.getBloomFilter();
      
      final Vector missing = new Vector();
      
      while (iter.hasNext()) {
        FragmentKey fkey = (FragmentKey)iter.next();
        Id thisPos = ((RingId)getFragmentLocation(fkey)).getId();
        if (range.containsId(thisPos)) {
          if (!bv.contains(fkey.getVersionKey().toByteArray())) {
            log(fkey+" @"+thisPos+" - MISSING");
            missing.add(fkey);
          } else {
            log(fkey+" @"+thisPos+" - OK");
          }
        } else log(fkey+" @"+thisPos+" - OUT OF RANGE");
      }

      if (missing.isEmpty()) {
        log("SYN: No fragments missing. OK. ");
        return;
      }
      
      log("SYN2: Sending "+missing.size()+" fragments to "+gsm.getSource().getId());
      
      fragmentStorage.getObject((FragmentKey) missing.elementAt(0), new Continuation() {
        int currentLookup = 0;
        int manifestIndex = 0;
        final int numLookups = missing.size();
        StorageManifest[] manifests = new StorageManifest[Math.min(numLookups, manifestAggregationFactor)];
        Fragment[] fragments = new Fragment[Math.min(numLookups, manifestAggregationFactor)];
        FragmentKey[] keys = new FragmentKey[Math.min(numLookups, manifestAggregationFactor)];
        
        public void receiveResult(Object o) {
          final FragmentKey thisKey = (FragmentKey) missing.elementAt(currentLookup);

          if (o == null) {
            warn("SYN2: Fragment "+thisKey+" not found -- canceled SYN");
            return;
          }
      
          log("SYN2: Retrieved manifest "+thisKey + " (dest="+gsm.getSource().getId()+", offset="+offset+")");
          
          FragmentAndManifest fam = (FragmentAndManifest) o;
          
          if (!fam.key.equals(thisKey))
            panic("Key mismatch -- "+thisKey+" requested, but "+fam.key+" received");
          
          if (!policy.checkSignature(fam.manifest, thisKey.getVersionKey()))
            panic("Signature mismatch!!");

          fragments[manifestIndex] = null;
          manifests[manifestIndex] = fam.manifest;
          int hisFID = thisKey.getFragmentID() - offset;
          if (hisFID < 0)
            hisFID += numFragments;
          if (hisFID >= numFragments)
            panic("Assertion failed: L938");
          keys[manifestIndex] = new FragmentKey(thisKey.getVersionKey(), hisFID);
          log("SYN2: He should have key "+keys[manifestIndex]+" @"+((RingId)getFragmentLocation(keys[manifestIndex])).getId());
          manifestIndex ++;
          currentLookup ++;
          if ((manifestIndex == manifestAggregationFactor) || (currentLookup == numLookups)) {
            log("SYN2: Sending a packet with "+keys.length+" manifests to "+gsm.getSource().getId());
            
            endpoint.route(
              null,
              new GlacierDataMessage(getUID(), keys, fragments, manifests, getLocalNodeHandle(), gsm.getSource().getId(), false),
              gsm.getSource()
            );

            manifestIndex = 0;
            manifests = new StorageManifest[Math.min(numLookups - currentLookup, manifestAggregationFactor)];
            keys = new FragmentKey[Math.min(numLookups - currentLookup, manifestAggregationFactor)];
            fragments = new Fragment[Math.min(numLookups - currentLookup, manifestAggregationFactor)];
          }
          
          if (currentLookup < numLookups)
            fragmentStorage.getObject((FragmentKey) missing.elementAt(currentLookup), this);
        }
        public void receiveException(Exception e) {
          warn("SYN2: Exception while retrieving fragment "+missing.elementAt(currentLookup)+", e="+e+" -- canceled SYN");
        }
      });
          
      return;

    } else if (msg instanceof GlacierRangeQueryMessage) {
      final GlacierRangeQueryMessage grqm = (GlacierRangeQueryMessage) msg;
      IdRange requestedRange = grqm.getRequestedRange();
      
      log("Range query for "+requestedRange);

      Iterator iter = neighborStorage.scan().getIterator();
      Vector ccwIDs = new Vector();
      Vector cwIDs = new Vector();
      Id myID = ((RingId)getLocalNodeHandle().getId()).getId();
      
      while (iter.hasNext()) {
        Id thisNeighbor = (Id)iter.next();
        if (myID.clockwise(thisNeighbor))
          cwIDs.add(thisNeighbor);
        else
          ccwIDs.add(thisNeighbor);
      }

      for (int j=0; j<2; j++) {
        Vector v = (j==0) ? cwIDs : ccwIDs;
        boolean madeProgress = true;
        while (madeProgress) {
          madeProgress = false;
          for (int i=0; i<(v.size()-1); i++) {
            if (((Id)v.elementAt(i+1)).clockwise((Id)v.elementAt(i))) {
              Object h = v.elementAt(i);
              v.setElementAt(v.elementAt(i+1), i);
              v.setElementAt(h, i+1);
              madeProgress = true;
            }
          }
        }
      }
      
      Vector allIDs = new Vector();
      allIDs.addAll(ccwIDs);
      allIDs.add(myID);
      allIDs.addAll(cwIDs);
      
      for (int i=0; i<allIDs.size(); i++) {
        Id currentElement = (Id) allIDs.elementAt(i);
        Id cwId, ccwId;
        if (i>0) {
          Id previousElement = (Id) allIDs.elementAt(i-1);
          ccwId = previousElement.addToId(previousElement.distanceFromId(currentElement).shiftDistance(1,0));
        } else {
          ccwId = currentElement;
        }
        if (i<(allIDs.size()-1)) {
          Id nextElement = (Id) allIDs.elementAt(i+1);
          cwId = currentElement.addToId(currentElement.distanceFromId(nextElement).shiftDistance(1,0));
        } else {
          cwId = currentElement;
        }

        log(" - #"+i+" "+currentElement+": "+ccwId+"-"+cwId);
        
        IdRange thisRange = pastryIdFactory.buildIdRange(ccwId, cwId);
        IdRange intersectRange = requestedRange.intersectRange(thisRange);
        if (!intersectRange.isEmpty()) {
          log("     - Intersects: "+intersectRange+", sending RangeForward");
          RingId currentElementRID = factory.buildRingId(((RingId)getLocalNodeHandle().getId()).getRingId(), currentElement);
          endpoint.route(
            currentElementRID,
            new GlacierRangeForwardMessage(grqm.getUID(), requestedRange, grqm.getSource(), getLocalNodeHandle(), currentElementRID),
            null
          );
        }
      }
      
      log("Finished processing range query");
      
      return;
      
    } else if (msg instanceof GlacierRangeForwardMessage) {
      GlacierRangeForwardMessage grfm = (GlacierRangeForwardMessage) msg;
      
      if (!grfm.getDestination().equals(getLocalNodeHandle().getId())) {
        log("GRFM: Not for us (dest="+grfm.getDestination()+", we="+getLocalNodeHandle().getId());
        return;
      }
      
      IdRange commonRange = responsibleRange.intersectRange(grfm.getRequestedRange());
      if (!commonRange.isEmpty()) {
        log("GRFM: Returning common range "+commonRange+" to requestor "+grfm.getRequestor());
        endpoint.route(
          null,
          new GlacierRangeResponseMessage(grfm.getUID(), commonRange, getLocalNodeHandle(), grfm.getRequestor().getId()),
          grfm.getRequestor()
        );
      } else {
        warn("Received GRFM by "+grfm.getRequestor()+", but no common range??!? -- ignored");
      }

      return;
      
    } else if (msg instanceof GlacierFetchMessage) {
      final GlacierFetchMessage gfm = (GlacierFetchMessage) msg;
      log("Received fetch for " + gfm.getKey());

      /* FetchMessages are sent during recovery to retrieve a fragment from
         another node. They can be answered a) if the recipient has a copy
         of the fragment, or b) if the recipient has a full replica of
         the object. In the second case, the fragment is created on-the-fly */

      fragmentStorage.getObject(gfm.getKey(),
        new Continuation() {
          public void receiveResult(Object o) {
            if (o != null) {
              log("Fragment "+gfm.getKey()+" found ("+o+"), returning...");
              FragmentAndManifest fam = (FragmentAndManifest) o;
              log("FAM: "+fam.fragment+" / "+fam.manifest);
              endpoint.route(
                null,
                new GlacierDataMessage(gfm.getUID(), gfm.getKey(), fam.fragment, null, getLocalNodeHandle(), gfm.getSource().getId(), true),
                gfm.getSource()
              );
            } else {
              log("Fragment "+gfm.getKey()+" not found - but maybe we have the original? - "+gfm.getKey().getVersionKey().getId());
              policy.prefetchLocalObject(gfm.getKey().getVersionKey(),
                new Continuation() {
                  public void receiveResult(Object o) {
                    if (o != null) {
                      long theVersion = extractVersion(o);
                      if (theVersion == gfm.getKey().getVersionKey().getVersion()) {
                        log("Original of "+gfm.getKey()+" found ("+o+", ts="+theVersion+", expected="+gfm.getKey().getVersionKey().getVersion()+") Recoding...");
                        Fragment[] frags = policy.encodeObject((Serializable) o);
                        
                        log("Fragments recoded ok. Returning fragment "+gfm.getKey()+"...");
                        FragmentKey[] keys = new FragmentKey[1];
                        keys[0] = gfm.getKey();
                        Fragment[] fragments = new Fragment[1];
                        fragments[0] = frags[gfm.getKey().getFragmentID()];
                        StorageManifest[] manifests = new StorageManifest[1];
                        manifests[0] = null;

                        endpoint.route(
                          null,
                          new GlacierDataMessage(gfm.getUID(), keys, fragments, manifests, getLocalNodeHandle(), gfm.getSource().getId(), true),
                          gfm.getSource()
                        );
                      } else {
                        log("Original of "+gfm.getKey()+" not found; have different version: "+theVersion);
                        endpoint.route(
                          null,
                          new GlacierResponseMessage(gfm.getUID(), gfm.getKey(), false, true, getLocalNodeHandle(), gfm.getSource().getId(), true),
                          gfm.getSource()
                        );
                      }
                    } else {
                      log("Original of "+gfm.getKey()+" not found either");
                      endpoint.route(
                        null,
                        new GlacierResponseMessage(gfm.getUID(), gfm.getKey(), false, true, getLocalNodeHandle(), gfm.getSource().getId(), true),
                        gfm.getSource()
                      );
                    }
                  }

                  public void receiveException(Exception e) {
                    warn("storage.getObject(" + gfm.getKey() + ") returned exception " + e);
                    e.printStackTrace();
                  }
                }
              );
            }
          }

          public void receiveException(Exception e) {
            warn("Fetch(" + gfm.getKey() + ") returned exception " + e);
          }
        });
      
    } else if (msg instanceof GlacierDataMessage) {
      final GlacierDataMessage gdm = (GlacierDataMessage) msg;
      for (int i=0; i<gdm.numKeys(); i++) {
        final FragmentKey thisKey = gdm.getKey(i);
        final Fragment thisFragment = gdm.getFragment(i);
        final StorageManifest thisManifest = gdm.getManifest(i);
        
        if ((thisFragment != null) && (thisManifest != null)) {
          log("Got Fragment+Manifest for "+thisKey);

          if (!amResponsibleFor(getFragmentLocation(thisKey))) {
            warn("Not responsible for "+thisKey+" (at "+getFragmentLocation(thisKey)+") -- discarding");
            continue;
          }
          
          if (!policy.checkSignature(thisManifest, thisKey.getVersionKey())) {
            warn("Manifest is not signed properly");
            continue;
          }
          
          if (!thisManifest.validatesFragment(thisFragment, thisKey.getFragmentID())) {
            warn("Manifest does not validate this fragment");
            continue;
          }
            
          if (!fragmentStorage.exists(thisKey)) {
            log("Verified ok. Storing locally.");
            
            FragmentAndManifest fam = new FragmentAndManifest(thisKey, thisFragment, thisManifest, 
              new FragmentMetadata(thisManifest.getExpirationDate(), -1)
            );

            fragmentStorage.store(thisKey, fam,
              new Continuation() {
                public void receiveResult(Object o) {
                  log("Stored OK, sending receipt: "+thisKey);

                  FragmentKey fkeyA[] = new FragmentKey[1];
                  fkeyA[0] = thisKey;
                  boolean haveItA[] = new boolean[1];
                  haveItA[0] = true;
                  boolean authoritativeA[] = new boolean[1];
                  authoritativeA[0] = amResponsibleFor(getFragmentLocation(thisKey));
                  
                  endpoint.route(
                    null,
                    new GlacierResponseMessage(gdm.getUID(), fkeyA, haveItA, authoritativeA, getLocalNodeHandle(), gdm.getSource().getId(), true),
                    gdm.getSource()
                  );
                }

                public void receiveException(Exception e) {
                  warn("receiveException(" + e + ") while storing a fragment -- unexpected, ignored (key=" + thisKey + ")");
                }
              }
            );
          } else {
            warn("We already have a fragment with this key! -- discarding");
            continue;
          }
          
          continue;
        }
        
        if ((thisFragment == null) && (thisManifest != null)) {

          if (!amResponsibleFor(getFragmentLocation(thisKey))) {
            warn("Not responsible for "+thisKey+" (at "+getFragmentLocation(thisKey)+") -- discarding");
            continue;
          }

          /* We are being informed of a fragment that 
               a) we should have, but currently don't, or
               b) we already have, but whose manifest will soon expire */

          if (fragmentStorage.exists(thisKey))
            panic("Not implemented -- must replace existing manifest");

          log("MUST FETCH DATA: "+thisKey);

          final long tStart = System.currentTimeMillis();
          retrieveFragment(thisKey, thisManifest, new GlacierContinuation() {
            public String toString() {
              return "Fetch synced fragment: "+thisKey;
            }
            public void receiveResult(Object o) {
              if (o instanceof Fragment) {
                if (!fragmentStorage.exists(thisKey)) {
                  log("Received fragment "+thisKey+" (from primary) matches existing manifest, storing...");
              
                  FragmentAndManifest fam = new FragmentAndManifest(thisKey, (Fragment) o, thisManifest,
                    new FragmentMetadata(thisManifest.getExpirationDate(), -1)
                  );

                  fragmentStorage.store(thisKey, fam,
                    new Continuation() {
                      public void receiveResult(Object o) {
                        log("Recovered fragment stored OK");
                      }
                      public void receiveException(Exception e) {
                        warn("receiveException(" + e + ") while storing a fragment with existing manifest (key=" + thisKey + ")");
                      }
                    }
                  );
                } else {
                  warn("Received fragment "+thisKey+", but it already exists in the fragment store");
                }
              } else {
                warn("FS received something other than a fragment: "+o);
              }
            }
            public void receiveException(Exception e) {
              warn("Exception while inserting "+thisKey+": "+e);
              e.printStackTrace();
              terminate();
            }
            public void timeoutExpired() {
              warn("Timeout while fetching synced fragment "+thisKey+" -- aborted");
              terminate();              
            }
            public long getTimeout() {
              return tStart + overallRestoreTimeout;
            }
          });
          
          continue;
        }
      
        panic("Case not implemented! -- GDM");
      }
          
      return;

    } else if (msg instanceof GlacierTimeoutMessage) {
    
      /* TimeoutMessages are generated by the local node when a 
         timeout expires. */
    
      GlacierTimeoutMessage gtm = (GlacierTimeoutMessage) msg;
      timerExpired((char) gtm.getUID());
      return;
    } else {
      panic("GLACIER ERROR - Received message " + msg + " of unknown type.");
    }
  }
  
  public int getReplicationFactor() {
    return 1;
  }

  public NodeHandle getLocalNodeHandle() {
    return endpoint.getLocalNodeHandle();
  }
}
