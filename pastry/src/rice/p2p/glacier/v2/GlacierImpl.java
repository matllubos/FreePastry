package rice.p2p.glacier.v2;

// o Multiple fragments on one node?
// o Check manifest when doing a direct lookup()

import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import rice.Continuation;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.IdFactory;
import rice.p2p.commonapi.IdRange;
import rice.p2p.commonapi.IdSet;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.Node;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.NodeHandleSet;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.glacier.Fragment;
import rice.p2p.glacier.FragmentKey;
import rice.p2p.glacier.FragmentKeySet;
import rice.p2p.glacier.Glacier;
import rice.p2p.glacier.GlacierException;
import rice.p2p.glacier.VersionKey;
import rice.p2p.glacier.VersioningPast;
import rice.p2p.glacier.v2.messaging.GlacierDataMessage;
import rice.p2p.glacier.v2.messaging.GlacierFetchMessage;
import rice.p2p.glacier.v2.messaging.GlacierMessage;
import rice.p2p.glacier.v2.messaging.GlacierNeighborRequestMessage;
import rice.p2p.glacier.v2.messaging.GlacierNeighborResponseMessage;
import rice.p2p.glacier.v2.messaging.GlacierQueryMessage;
import rice.p2p.glacier.v2.messaging.GlacierRangeForwardMessage;
import rice.p2p.glacier.v2.messaging.GlacierRangeQueryMessage;
import rice.p2p.glacier.v2.messaging.GlacierRangeResponseMessage;
import rice.p2p.glacier.v2.messaging.GlacierResponseMessage;
import rice.p2p.glacier.v2.messaging.GlacierSyncMessage;
import rice.p2p.glacier.v2.messaging.GlacierTimeoutMessage;
import rice.p2p.past.Past;
import rice.p2p.past.PastContent;
import rice.p2p.past.PastContentHandle;
import rice.p2p.past.gc.GCPast;
import rice.p2p.past.gc.GCPastContent;
import rice.persistence.StorageManager;
import rice.visualization.server.DebugCommandHandler;

public class GlacierImpl implements Glacier, Past, GCPast, VersioningPast, Application, DebugCommandHandler {

  protected final StorageManager fragmentStorage;
  protected final StorageManager neighborStorage;
  protected final GlacierPolicy policy;
  protected final Node node;
  protected final int numFragments;
  protected final String instance;
  protected final int numSurvivors;
  protected final Endpoint endpoint;
  protected final IdFactory factory;
  protected final Hashtable continuations;
  protected final String debugID;
  protected StorageManager trashStorage;
  protected long nextContinuationTimeout;
  protected IdRange responsibleRange;
  protected int nextUID;
  protected CancellableTask timer;  

  private final int loglevel = 3;

  private final long SECONDS = 1000;
  private final long MINUTES = 60 * SECONDS;
  private final long HOURS = 60 * MINUTES;

  private final long insertTimeout = 20 * SECONDS;
  private final double minFragmentsAfterInsert = 3.0;

  private final long refreshTimeout = 20 * SECONDS;

  private final long expireNeighborsDelayAfterJoin = 5 * SECONDS;
  private final long expireNeighborsInterval = 20 * SECONDS;
  private final long neighborTimeout = 60 * SECONDS;
  
  private final long syncDelayAfterJoin = 15 * SECONDS;
  private final long syncInterval = 60 * SECONDS;
  private final long syncMinRemainingLifetime = 60 * SECONDS;
  private final int syncBloomFilterNumHashes = 3;
  private final int syncBloomFilterBitsPerKey = 4;
  private final int syncPartnersPerTrial = 1;
  private final int syncMaxFragments = 100;

  private final int manifestAggregationFactor = 5;
  
  private final int fragmentRequestMaxAttempts = 3;
  private final long fragmentRequestTimeout = 20 * SECONDS;

  private final long manifestRequestTimeout = 10 * SECONDS;
  private final long manifestRequestInitialBurst = 3;
  private final long manifestRequestRetryBurst = 5;

  private final long overallRestoreTimeout = 2 * MINUTES;
  
  private final long handoffDelayAfterJoin = 45 * SECONDS;
  private final long handoffInterval = 60 * SECONDS;
  private final int handoffMaxFragments = 100;

  private final long garbageCollectionInterval = 60 * SECONDS;
  private final int garbageCollectionMaxFragmentsPerRun = 100;

  private final double restoreMaxRequestFactor = 4.0;
  private final int restoreMaxBoosts = 2;

  public GlacierImpl(Node nodeArg, StorageManager fragmentStorageArg, StorageManager neighborStorageArg, int numFragmentsArg, int numSurvivorsArg, IdFactory factoryArg, String instanceArg, GlacierPolicy policyArg) {
    this.fragmentStorage = fragmentStorageArg;
    this.neighborStorage = neighborStorageArg;
    this.trashStorage = null;
    this.policy = policyArg;
    this.node = nodeArg;
    this.instance = instanceArg;
    this.endpoint = node.registerApplication(this, instance);
    this.numFragments = numFragmentsArg;
    this.numSurvivors = numSurvivorsArg;
    this.factory = factoryArg;
    this.responsibleRange = null;
    this.nextUID = 0;
    this.continuations = new Hashtable();
    this.timer = null;
    this.nextContinuationTimeout = -1;
    this.debugID = "G" + Character.toUpperCase(instance.charAt(instance.lastIndexOf('-')+1));
    determineResponsibleRange();

    /* Neighbor requests */

    addContinuation(new GlacierContinuation() {
      long nextTimeout;
      
      public String toString() {
        return "Neighbor continuation";
      }
      public void init() {
        nextTimeout = System.currentTimeMillis() + expireNeighborsDelayAfterJoin;

        NodeHandleSet leafSet = endpoint.neighborSet(999);
        NodeHandle localHandle = getLocalNodeHandle();
        NodeHandle cwExtreme = localHandle;
        NodeHandle ccwExtreme = localHandle;

        for (int i=0; i<leafSet.size(); i++) {
          NodeHandle thisHandle = leafSet.getHandle(i);
          if (localHandle.getId().clockwise(thisHandle.getId())) {
            if (cwExtreme.getId().clockwise(thisHandle.getId()))
              cwExtreme = thisHandle;
          } else {
            if (ccwExtreme.getId().clockwise(thisHandle.getId()))
              ccwExtreme = thisHandle;
          }
        }

        IdRange leafRange = factory.buildIdRange(ccwExtreme.getId(), cwExtreme.getId());
    
        for (int k=0; k<leafSet.size(); k++) {
          if (!leafSet.getHandle(k).getId().equals(getLocalNodeHandle().getId())) {
            neighborSeen(leafSet.getHandle(k).getId(), System.currentTimeMillis());
            log(2, "Asking "+leafSet.getHandle(k).getId()+" about neighbors in "+leafRange);
            endpoint.route(
              null,
              new GlacierNeighborRequestMessage(getMyUID(), leafRange, getLocalNodeHandle(), leafSet.getHandle(k).getId()),
              leafSet.getHandle(k)
            );
          }
        }
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierNeighborResponseMessage) {
          final GlacierNeighborResponseMessage gnrm = (GlacierNeighborResponseMessage) o;
          log(3, "NeighborResponse from "+gnrm.getSource()+" with "+gnrm.numNeighbors()+" neighbors");
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
        NodeHandleSet leafSet = endpoint.neighborSet(999);

        log(2, "Checking neighborhood for expired certificates...");
        
        while (iter.hasNext()) {
          final Id thisNeighbor = (Id) iter.next();
          if (leafSet.memberHandle(thisNeighbor)) {
            log(3, "CNE: Refreshing current neighbor: "+thisNeighbor);
            neighborSeen(thisNeighbor, System.currentTimeMillis());
          } else {
            log(3, "CNE: Retrieving "+thisNeighbor);
            neighborStorage.getObject(thisNeighbor, new Continuation() {
              public void receiveResult(Object o) {
                if (o==null) {
                  warn("CNE: Cannot retrieve neighbor "+thisNeighbor);
                  return;
                }
              
                long lastSeen = ((Long)o).longValue();
                if (lastSeen < earliestAcceptableDate) {
                  log(2, "CNE: Removing expired neighbor "+thisNeighbor+" ("+lastSeen+"<"+earliestAcceptableDate+")");
                  neighborStorage.unstore(thisNeighbor, new Continuation() {
                    public void receiveResult(Object o) {
                      log(3, "CNE unstore successful: "+thisNeighbor+", returned "+o);
                    }
                    public void receiveException(Exception e) {
                      warn("CNE unstore failed: "+thisNeighbor+", returned "+e);
                    }
                  });
                } else {
                  log(2, "CNE: Neighbor "+thisNeighbor+" still active, last seen "+lastSeen);
                }
              }
              public void receiveException(Exception e) {
                log(1, "CNE: Exception while retrieving neighbor "+thisNeighbor+", e="+e);
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
          IdRange originalRange = factory.buildIdRange(ccwId, cwId);
        
          log(2, "Range response (offset: "+offset+"): "+grrm.getCommonRange()+", original="+originalRange);
        
          IdSet keySet = fragmentStorage.scan();
          BloomFilter bv = new BloomFilter((2*keySet.numElements()+5)*syncBloomFilterBitsPerKey, syncBloomFilterNumHashes);
          Iterator iter = keySet.getIterator();

          while (iter.hasNext()) {
            FragmentKey fkey = (FragmentKey)iter.next();
            Id thisPos = getFragmentLocation(fkey);
            if (originalRange.containsId(thisPos)) {
              FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(fkey);
              long currentExp = metadata.getCurrentExpiration();
              long prevExp = metadata.getPreviousExpiration();
              log(4, " - Adding "+fkey+" as "+fkey.getVersionKey().getId()+", ecur="+currentExp+", eprev="+prevExp);
              bv.add(getHashInput(fkey.getVersionKey(), currentExp));
              bv.add(getHashInput(fkey.getVersionKey(), prevExp));
            }
          }

          log(3, "Got "+bv);        
          log(2, keySet.numElements()+" keys added, sending sync request...");
        
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
        IdRange requestedRange = factory.buildIdRange(ccwId, cwId);
            
        log(2, "Sending range query for ("+requestedRange+") to "+dest);
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
          final GlacierResponseMessage grm = (GlacierResponseMessage) o;
          log(3, "Received handoff response from "+grm.getSource().getId()+" with "+grm.numKeys()+" keys");
          for (int i=0; i<grm.numKeys(); i++) {
            final FragmentKey thisKey = grm.getKey(i);
            if (grm.getAuthoritative(i)) {
              if (grm.getHaveIt(i)) {
                Id thisPos = getFragmentLocation(thisKey);
                if (!responsibleRange.containsId(thisPos)) {
                  log(3, "Deleting fragment "+thisKey);
                  deleteFragment(thisKey, new Continuation() {
                    public void receiveResult(Object o) {
                      log(3, "Handed off fragment deleted: "+thisKey+" (o="+o+")");
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
                fragmentStorage.getObject(thisKey, new Continuation() {
                  public void receiveResult(Object o) {
                    if (o != null) {
                      log(2, "Fragment "+thisKey+" found ("+o+"), handing off...");
                      FragmentAndManifest fam = (FragmentAndManifest) o;
                      endpoint.route(
                        null,
                        new GlacierDataMessage(grm.getUID(), thisKey, fam.fragment, fam.manifest, getLocalNodeHandle(), grm.getSource().getId(), true),
                        grm.getSource()
                      );
                    } else {
                      warn("Handoff failed; fragment "+thisKey+" not found in fragment store");
                    }
                  }
                  public void receiveException(Exception e) {
                    warn("Handoff failed; exception while fetching "+thisKey+", e="+e);
                    e.printStackTrace();
                  }
                });
              }
            } else {
              log(3, "Ignoring fragment "+thisKey+" (haveIt="+grm.getHaveIt(i)+", authoritative="+grm.getAuthoritative(i)+")");
            }
          }
        } else if (o instanceof GlacierDataMessage) {
          final GlacierDataMessage gdm = (GlacierDataMessage) o;
          for (int i=0; i<gdm.numKeys(); i++) {
            final FragmentKey thisKey = gdm.getKey(i);
            final Fragment thisFragment = gdm.getFragment(i);
            final Manifest thisManifest = gdm.getManifest(i);
        
            if ((thisFragment != null) && (thisManifest != null)) {
              log(2, "Handoff: Received Fragment+Manifest for "+thisKey);

              if (!responsibleRange.containsId(getFragmentLocation(thisKey))) {
                warn("Handoff: Not responsible for "+thisKey+" (at "+getFragmentLocation(thisKey)+") -- discarding");
                continue;
              }
          
              if (!policy.checkSignature(thisManifest, thisKey.getVersionKey())) {
                warn("Handoff: Manifest is not signed properly");
                continue;
              }   
          
              if (!thisManifest.validatesFragment(thisFragment, thisKey.getFragmentID())) {
                warn("Handoff: Manifest does not validate this fragment");
                continue;
              }
            
              if (!fragmentStorage.exists(thisKey)) {
                log(3, "Handoff: Verified ok. Storing locally.");
            
                FragmentAndManifest fam = new FragmentAndManifest(thisFragment, thisManifest);
  
                fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), 0), fam,
                  new Continuation() {
                    public void receiveResult(Object o) {
                      log(2, "Handoff: Stored OK, sending receipt: "+thisKey);

                      endpoint.route(
                        null,
                        new GlacierResponseMessage(gdm.getUID(), thisKey, true, thisManifest.getExpiration(), responsibleRange.containsId(getFragmentLocation(thisKey)), getLocalNodeHandle(), gdm.getSource().getId(), true),
                        gdm.getSource()
                      );
                    }

                    public void receiveException(Exception e) {
                      warn("Handoff: receiveException(" + e + ") while storing a fragment -- unexpected, ignored (key=" + thisKey + ")");
                    }
                  }
                );
              } else {
                warn("Handoff: We already have a fragment with this key! -- sending response");
                endpoint.route(
                  null,
                  new GlacierResponseMessage(gdm.getUID(), thisKey, true, thisManifest.getExpiration(), true, getLocalNodeHandle(), gdm.getSource().getId(), true),
                  gdm.getSource()
                );
                
                continue;
              }
          
              continue;
            } else {
              warn("Handoff: Either fragment or manifest are missing!");
              continue;
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
        log(2, "Checking fragment storage for fragments to hand off...");
        log(3, "Currently responsible for: "+responsibleRange);
        Iterator iter = fragmentStorage.scan().getIterator();
        Vector handoffs = new Vector();
        Id destination = null;
  
        while (iter.hasNext()) {
          FragmentKey fkey = (FragmentKey) iter.next();
          Id thisPos = getFragmentLocation(fkey);
          if (!responsibleRange.containsId(thisPos)) {
            log(3, "Must hand off "+fkey+" @"+thisPos);
            handoffs.add(fkey);

            if (handoffs.size() >= handoffMaxFragments) {
              log(2, "Limit of "+handoffMaxFragments+" reached for handoff");
              break;
            }
            
            if (destination == null)
              destination = thisPos;
          }
        }
        
        if (destination == null) {
          log(3, "Nothing to hand off -- returning");
          return;
        }
        
        int numHandoffs = Math.min(handoffs.size(), handoffMaxFragments);
        log(2, "Handing off "+numHandoffs+" fragments (out of "+handoffs.size()+")");
        FragmentKey[] keys = new FragmentKey[numHandoffs];
        for (int i=0; i<numHandoffs; i++)
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
    
    /* Garbage collection */
    
    addContinuation(new GlacierContinuation() {
      long nextTimeout;
      
      public String toString() {
        return "Garbage collector";
      }
      public void init() {
        nextTimeout = System.currentTimeMillis() + garbageCollectionInterval;
      }
      public void receiveResult(Object o) {
        warn("GC received object: "+o);
      }
      public void receiveException(Exception e) {
        warn("GC received exception: "+e);
        e.printStackTrace();
      }
      public long getTimeout() {
        return nextTimeout;
      }
      public void timeoutExpired() {
        final long now = System.currentTimeMillis();
        IdSet fragments = fragmentStorage.scan();
        int doneSoFar = 0, candidates = 0;

        log(2, "Garbage collection started at "+now+", scanning "+fragments.numElements()+" fragment(s)...");
        Iterator iter = fragments.getIterator();
        while (iter.hasNext()) {
          final Id thisKey = (Id) iter.next();
          final FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(thisKey);
          if (metadata.getCurrentExpiration() < now) {
            candidates ++;
            if (doneSoFar < garbageCollectionMaxFragmentsPerRun) {
              doneSoFar ++;
              deleteFragment(thisKey, new Continuation() {
                public void receiveResult(Object o) {
                  log(3, "GC collected "+thisKey.toStringFull()+", expired "+(now-metadata.getCurrentExpiration())+" msec ago");
                }
                public void receiveException(Exception e) {
                  log(3, "GC cannot collect "+thisKey.toStringFull());
                }
              });
            }
          }
        }
        
        log(2, "Garbage collection completed at "+System.currentTimeMillis());
        log(2, "Found "+candidates+" candidate(s), collected "+doneSoFar);
        nextTimeout += garbageCollectionInterval;
      }
    });
  }

  private void deleteFragment(final Id fkey, final Continuation command) {
    if (trashStorage != null) {
      log(2, "Moving fragment "+fkey.toStringFull()+" to trash");
      fragmentStorage.getObject(fkey, new Continuation() {
        public void receiveResult(Object o) {
          log(3, "Fragment "+fkey.toStringFull()+" retrieved, storing in trash");
          if (o != null) {
            trashStorage.store(fkey, null, (Serializable) o, new Continuation() {
              public void receiveResult(Object o) {
                log(3, "Deleting fragment "+fkey.toStringFull());
                fragmentStorage.unstore(fkey, command);
              }
              public void receiveException(Exception e) {
                warn("Cannot store in trash: "+fkey.toStringFull()+", e="+e);
                e.printStackTrace();
                command.receiveException(e);
              }
            });
          } else {
            receiveException(new GlacierException("Move to trash: Fragment "+fkey+" does not exist?!?"));
          }
        }
        public void receiveException(Exception e) {
          warn("Cannot retrieve fragment "+fkey+" for deletion: e="+e);
          e.printStackTrace();
          command.receiveException(new GlacierException("Cannot retrieve fragment "+fkey+" for deletion"));
        }
      });
    } else {
      log(2, "Deleting fragment "+fkey.toStringFull());
      fragmentStorage.unstore(fkey, command);
    }
  }

  public void setTrashcan(StorageManager trashStorage) {
    this.trashStorage = trashStorage;
  }

  private byte[] getHashInput(VersionKey vkey, long expiration) {
    byte[] a = vkey.toByteArray();
    byte[] b = new byte[a.length + 8];
    for (int i=0; i<a.length; i++)
      b[i] = a[i];
      
    b[a.length + 0] = (byte)(0xFF & (expiration>>56));
    b[a.length + 1] = (byte)(0xFF & (expiration>>48));
    b[a.length + 2] = (byte)(0xFF & (expiration>>40));
    b[a.length + 3] = (byte)(0xFF & (expiration>>32));
    b[a.length + 4] = (byte)(0xFF & (expiration>>24));
    b[a.length + 5] = (byte)(0xFF & (expiration>>16));
    b[a.length + 6] = (byte)(0xFF & (expiration>>8));
    b[a.length + 7] = (byte)(0xFF & (expiration));

    return b;
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
        cancelTimer();
      
      nextContinuationTimeout = thisTimeout;
      if (nextContinuationTimeout > now)
        setTimer((int)(nextContinuationTimeout - now));
      else
        timerExpired();
    }
  }

  private void determineResponsibleRange() {
    Id cwPeer = null, ccwPeer = null, xcwPeer = null, xccwPeer = null, myNodeId = getLocalNodeHandle().getId();
    
    log(3, "Determining responsible range");
    
    Iterator iter = neighborStorage.scan().getIterator();
    while (iter.hasNext()) {
      Id thisNeighbor = (Id) iter.next();
      log(3, "Considering neighbor: "+thisNeighbor);
      if (myNodeId.clockwise(thisNeighbor)) {
        if ((cwPeer == null) || thisNeighbor.isBetween(myNodeId, cwPeer))
          cwPeer = thisNeighbor;
        if ((xcwPeer == null) || xcwPeer.clockwise(thisNeighbor))
          xcwPeer = thisNeighbor;
      } else {
        if ((ccwPeer == null) || thisNeighbor.isBetween(ccwPeer, myNodeId))
          ccwPeer = thisNeighbor;
        if ((xccwPeer == null) || !xccwPeer.clockwise(thisNeighbor))
          xccwPeer = thisNeighbor;
      }
    }
          
    if (ccwPeer == null)
      ccwPeer = xcwPeer;
    if (cwPeer == null)
      cwPeer = xccwPeer;
      
    log(3, "XCCW: "+xccwPeer+" CCW: "+ccwPeer+" ME: "+myNodeId+" CW: "+cwPeer+" XCW: "+xcwPeer);
      
    if ((ccwPeer == null) || (cwPeer == null)) {
      responsibleRange = factory.buildIdRange(myNodeId, myNodeId);
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
    
    responsibleRange = factory.buildIdRange(
      ccwPeer.addToId(ccwHalfDistance),
      myNodeId.addToId(cwHalfDistance)
    );
    
    log(2, "New range: "+responsibleRange);
  }

  private String getLogPrefix() {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);

    return ((h<10) ? "0" : "") + Integer.toString(h) + ":" +
           ((m<10) ? "0" : "") + Integer.toString(m) + ":" +
           ((s<10) ? "0" : "") + Integer.toString(s) + " @" +
           node.getId() + " " + debugID;
  }

  private void log(int level, String str) {
    if (level <= loglevel)
      System.out.println(getLogPrefix() + level + " " + str);
  }

  private void warn(String str) {
    System.out.println(getLogPrefix() + "0 *** WARNING *** " + str);
  }

  protected int getUID() {
    return nextUID++;
  }

  /**
   * Schedule a timer event
   *
   * @param timeoutMsec Length of the delay (in milliseconds)
   */
  private void setTimer(int timeoutMsec) {
    timer = endpoint.scheduleMessage(new GlacierTimeoutMessage(0, getLocalNodeHandle()), timeoutMsec);
  }

  /**
   * Cancel a timer event that has not yet occurred
   */
  private void cancelTimer() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  private byte[] getDistance(double d) {
    byte[] result = new byte[20];
      
    double c = 0.5;
    for (int i=19; i>=0; i--) {
      result[i] = 0;
      for (int j=7; j>=0; j--) {
        if (d >= c) {
          result[i] |= (1<<j);
          d -= c;
        }
        c /= 2;
      }
    }
    
    return result;
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
    double vOffset = version * (1.0/2.7182821);
    vOffset -= (long)vOffset;
    double totalOffset = (((float)fragmentNr) / ((float)numFragments)) + vOffset;
    return objectKey.addToId(factory.buildIdDistance(getDistance(totalOffset)));
  }
  
  private Id getFragmentLocation(FragmentKey fkey) {
    return getFragmentLocation(
      fkey.getVersionKey().getId(),
      fkey.getFragmentID(),
      fkey.getVersionKey().getVersion()
    );
  }
  
  /**
   * This method is called when Glacier encounters a fatal error
   *
   * @param s Message describing the error
   * @exception Error Terminates the program
   */
  private void panic(String s) throws Error {
    System.out.println("PANIC: " + s);
    throw new Error("Panic");
  }

  public String handleDebugCommand(String command)
  {
    String myInstance = "glacier."+instance.substring(instance.lastIndexOf("-") + 1);
    String requestedInstance = command.substring(0, command.indexOf(" "));
    String cmd = command.substring(requestedInstance.length() + 1);
    
    if (!requestedInstance.equals(myInstance) && !requestedInstance.equals("g"))
      return null;
  
    log(2, "Debug command: "+cmd);
  
    if ((cmd.length() >= 2) && cmd.substring(0, 2).equals("ls")) {
      FragmentKeySet keyset = (FragmentKeySet) fragmentStorage.scan();
      Iterator iter = keyset.getIterator();
      StringBuffer result = new StringBuffer();
  
      long now = System.currentTimeMillis();
      if (cmd.indexOf("-r") < 0)
        now = 0;
    
      result.append(keyset.numElements()+ " fragment(s)\n");
      
      while (iter.hasNext()) {
        FragmentKey thisKey = (FragmentKey) iter.next();
        boolean isMine = responsibleRange.containsId(getFragmentLocation(thisKey));
        FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(thisKey);
        result.append(((Id)thisKey).toStringFull()+" "+(isMine ? "OK" : "MI")+" "+
            (metadata.getCurrentExpiration()-now)+" "+(metadata.getPreviousExpiration()-now)+"\n");
      }
      
      return result.toString();
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

    if ((cmd.length() >= 7) && cmd.substring(0, 7).equals("refresh")) {
      String args = cmd.substring(8);
      String expirationArg = args.substring(args.lastIndexOf(' ') + 1);
      String keyArg = args.substring(0, args.lastIndexOf(' '));

      Id id = factory.buildIdFromToString(keyArg);
      long expiration = System.currentTimeMillis() + Long.parseLong(expirationArg);

      final String[] ret = new String[] { null };
      refresh(new Id[] { id }, expiration, new Continuation() {
        public void receiveResult(Object o) {
          ret[0] = "result("+o+")";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
      
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return "refresh("+id+", "+expiration+")="+ret[0];
    }

    if ((cmd.length() >= 9) && cmd.substring(0, 9).equals("neighbors")) {
      Iterator iter = neighborStorage.scan().getIterator();
      String result = "";

      result = result + neighborStorage.scan().numElements()+ " neighbor(s)\n";
      
      while (iter.hasNext())
        result = result + ((Id)iter.next()).toStringFull()+"\n";
        
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
        Id randomID = factory.buildRandomId(new Random());
        result = result + randomID.toStringFull() + "\n";
        insert(
          new DebugContent(randomID, false, 0, new byte[] {}),
          System.currentTimeMillis() + 120*SECONDS,
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

    if ((cmd.length() >= 8) && cmd.substring(0, 8).equals("manifest")) {
      String[] vkeyS = cmd.substring(9).split("v");
      Id key = factory.buildIdFromToString(vkeyS[0]);
      long version = Long.parseLong(vkeyS[1]);
      VersionKey vkey = new VersionKey(key, version);

      final String[] ret = new String[] { null };
      retrieveManifest(vkey, new Continuation() {
        public void receiveResult(Object o) {
          if (o instanceof Manifest)
            ret[0] = ((Manifest)o).toStringFull();
          else 
            ret[0] = "result("+o+")";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
      
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return "manifest("+vkey+")="+ret[0];
    }

    return null;
  }

  public void insert(final PastContent obj, final Continuation command) {
    insert(obj, GCPast.INFINITY_EXPIRATION, command);
  }

  public void refresh(Id[] ids, long expiration, final Continuation command) {
    long[] versions = new long[ids.length];
    Arrays.fill(versions, 0);
    refresh(ids, versions, expiration, command);
  }

  public void refresh(Id[] ids, long[] versions, final long expiration, final Continuation command) {
    final Continuation.MultiContinuation mc = new Continuation.MultiContinuation(command, ids.length);
    
    for (int i=0; i<ids.length; i++) {
      final Continuation thisContinuation = mc.getSubContinuation(i);
      final Id thisId = ids[i];
      final long thisVersion = versions[i];
      
      log(2, "refresh("+thisId.toStringFull()+"v"+thisVersion+", exp="+expiration+")");
      
      retrieveManifest(new VersionKey(thisId, thisVersion), new Continuation() {
        public void receiveResult(Object o) {
          if (o instanceof Manifest) {
            Manifest manifest = (Manifest) o;

            log(3, "refresh("+thisId.toStringFull()+"v"+thisVersion+"): Got manifest");
            manifest = policy.updateManifest(new VersionKey(thisId, thisVersion), manifest, expiration);
            Manifest[] manifests = new Manifest[numFragments];
            for (int i=0; i<numFragments; i++)
              manifests[i] = manifest;
            distribute(new VersionKey(thisId, thisVersion), null, manifests, expiration, thisContinuation);
          } else {
            warn("refresh("+thisId+"v"+thisVersion+"): Cannot retrieve manifest");
            thisContinuation.receiveResult(new GlacierException("Cannot retrieve manifest -- retry later"));
          }
        }
        public void receiveException(Exception e) {
          warn("refresh("+thisId+"v"+thisVersion+"): Exception while retrieving manifest: "+e);
          e.printStackTrace();
          thisContinuation.receiveException(e);
        }
      });
    }
  }

  private void distribute(final VersionKey key, final Fragment[] fragments, final Manifest[] manifests, final long expiration, final Continuation command) {
    final long tStart = System.currentTimeMillis();
    addContinuation(new GlacierContinuation() {
      NodeHandle[] holder;
      boolean[] receiptReceived;
      boolean doInsert = (fragments != null);
      boolean doRefresh = !doInsert;
      boolean answered = false;
      int minAcceptable = (int)(numSurvivors * minFragmentsAfterInsert);
      
      public String toString() {
        return whoAmI() + " continuation for "+key;
      }
      private int numReceiptsReceived() {
        int result = 0;
        for (int i=0; i<receiptReceived.length; i++)
          if (receiptReceived[i])
            result ++;
        return result;
      }
      private String whoAmI() {
        return (doRefresh) ? "Refresh" : "Insert";
      }
      public void init() {
        log(2, "Initializing "+whoAmI()+" continuation for " + key);
        holder = new NodeHandle[numFragments];
        receiptReceived = new boolean[numFragments];

        /* Send queries */
        
        log(3, "Sending queries for " + key);
        for (int i = 0; i < numFragments; i++) {
          Id fragmentLoc = getFragmentLocation(key.getId(), i, key.getVersion());
          FragmentKey keys[] = new FragmentKey[1];
          keys[0] = new FragmentKey(key, i);
      
          log(3, "Query #"+i+" to "+fragmentLoc);
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
          if (!grm.getKey(0).getVersionKey().equals(key)) {
            warn(whoAmI()+" response got routed to the wrong key: "+key);
            return;
          }

          int fragmentID = grm.getKey(0).getFragmentID();
          if (fragmentID < numFragments) {
            if (grm.getAuthoritative(0)) {
              if (doInsert && !grm.getHaveIt(0)) {
                if (holder[fragmentID] == null) {
                  holder[fragmentID] = grm.getSource();
                  log(3, "Got insert response, sending fragment "+grm.getKey(0));
                  endpoint.route(
                    null,
                    new GlacierDataMessage(getMyUID(), grm.getKey(0), fragments[fragmentID], manifests[fragmentID], getLocalNodeHandle(), grm.getSource().getId(), false),
                    grm.getSource()
                  );
                } else {
                  warn("Received two insert responses for the same fragment -- discarded");
                }
              } else if (grm.getHaveIt(0) && (grm.getExpiration(0) < expiration)) {
                if (holder[fragmentID] == null) {
                  holder[fragmentID] = grm.getSource();
                  log(3, "Got refresh response (exp="+grm.getExpiration(0)+"<"+expiration+"), sending manifest "+grm.getKey(0));
                  endpoint.route(
                    null,
                    new GlacierDataMessage(getMyUID(), grm.getKey(0), null, manifests[fragmentID], getLocalNodeHandle(), grm.getSource().getId(), false),
                    grm.getSource()
                  );
                } else {
                  warn("Received two refresh responses for the same fragment -- discarded");
                }
              } else if (grm.getHaveIt(0) && (grm.getExpiration(0) >= expiration)) {
                if ((holder[fragmentID] != null) && (holder[fragmentID].equals(grm.getSource()))) {
                  log(3, "Receipt received after "+whoAmI()+": "+grm.getKey(0));
                  receiptReceived[fragmentID] = true;
                  if ((numReceiptsReceived() >= minAcceptable) && !answered) {
                    answered = true;
                    reportSuccess();
                  }
                } else { 
                  warn("Receipt received from another source (expecting "+holder[fragmentID]+")");
                }
              }
            } else {
              log(3, whoAmI() + " response, but not authoritative -- ignoring");
            }
          } else {
            warn("Fragment ID too large in " + whoAmI() + " response -- discarded");
          }
          
          return;
        } else {
          warn("Unknown response to "+whoAmI()+" continuation: "+o+" -- discarded");
        }
      }
      public void receiveException(Exception e) {
        warn("Exception during "+whoAmI()+"("+key+"): "+e);
        e.printStackTrace();
        if (!answered) {
          answered = true;
          command.receiveException(new GlacierException("Exception while inserting/refreshing: "+e));
        }
        terminate();
      }
      private void reportSuccess() {
        log(3, "Reporting success for "+key+", "+numReceiptsReceived()+"/"+numFragments+" receipts received so far");
        if (doInsert)
          command.receiveResult(new Boolean[] { new Boolean(true) });
        else
          command.receiveResult(new Boolean(true));
      }      
      public void timeoutExpired() {        
        if (numReceiptsReceived() >= minAcceptable) {
          log(2, whoAmI()+" of "+key+" successful, "+numReceiptsReceived()+"/"+numFragments+" receipts received");
          if (!answered) {
            answered = true;
            reportSuccess();
          }
        } else {
          warn("Insertion of "+key+" failed, only "+numReceiptsReceived()+"/"+numFragments+" receipts received");
          if (!answered) {
            answered = true;
            command.receiveException(new GlacierException("Insert failed, did not receive enough receipts"));
          }
        }

        terminate();
      }
      public long getTimeout() {
        return tStart + ((doRefresh) ? refreshTimeout : insertTimeout);
      }
    });
  }  

  public void insert(final PastContent obj, long expiration, final Continuation command) {
    long theVersion = (obj instanceof GCPastContent) ? ((GCPastContent)obj).getVersion() : 0;
    final VersionKey vkey = new VersionKey(obj.getId(), theVersion);

    log(2, "insert(" + obj + " (id=" + vkey.toStringFull() + ", mutable=" + obj.isMutable() + ")");

    final Fragment[] fragments = policy.encodeObject(obj);
    if (fragments == null) {
      command.receiveException(new GlacierException("Cannot encode object"));
      return;
    }

    final Manifest[] manifests = policy.createManifests(vkey, obj, fragments, expiration);
    if (manifests == null) {
      command.receiveException(new GlacierException("Cannot create manifests"));
      return;
    }

    distribute(vkey, fragments, manifests, expiration, command);
  }

  private void timerExpired() {
    log(2, "Timer expired");

    boolean foundTerminated = false;
    long earliestTimeout;
    int numDelete;
        
    do {
      long now = System.currentTimeMillis();
      int[] deleteList = new int[100];
      numDelete = 0;
      earliestTimeout = -1;
        
      log(3, "Timer run at "+now);
          
      Enumeration enu = continuations.elements();
      while (enu.hasMoreElements()) {
        GlacierContinuation gc = (GlacierContinuation) enu.nextElement();

        if (!gc.hasTerminated() && gc.getTimeout() < (now + 1*SECONDS)) {
          log(3, "Timer: Resuming ["+gc+"]");
          gc.syncTimeoutExpired();
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
        log(3, "Deleting "+numDelete+" expired continuations");
        for (int i=0; i<numDelete; i++)
          continuations.remove(new Integer(deleteList[i]));
      }
            
    } while ((numDelete == 100) || ((earliestTimeout >= 0) && (earliestTimeout < System.currentTimeMillis())));

    if (earliestTimeout >= 0) {
      log(3, "Next timeout is at "+earliestTimeout);
      setTimer((int)Math.max(earliestTimeout - System.currentTimeMillis(), 1*SECONDS));
    } else log(3, "No more timeouts");
  }

  public void neighborSeen(Id nodeId, long when) {

    if (nodeId.equals(getLocalNodeHandle().getId()))
      return;

    log(3, "Neighbor "+nodeId+" was seen at "+when);

    if (when > System.currentTimeMillis()) {
      warn("Neighbor: "+when+" is in the future (now="+System.currentTimeMillis()+")");
      when = System.currentTimeMillis();
    }

    final Id fNodeId = nodeId;
    final long fWhen = when;

    neighborStorage.getObject(nodeId, 
      new Continuation() {
        public void receiveResult(Object o) {
          log(3, "Continue: neighborSeen ("+fNodeId+", "+fWhen+") after getObject");

          final long previousWhen = (o!=null) ? ((Long)o).longValue() : 0;
          log(3, "Neighbor: "+fNodeId+" previously seen at "+previousWhen);
          if (previousWhen >= fWhen) {
            log(3, "Neighbor: No update needed (new TS="+fWhen+")");
            return;
          }
          
          neighborStorage.store(fNodeId, null, new Long(fWhen),
            new Continuation() {
              public void receiveResult(Object o) {
                log(3, "Continue: neighborSeen ("+fNodeId+", "+fWhen+") after store");
                log(3, "Neighbor: Updated "+fNodeId+" from "+previousWhen+" to "+fWhen);
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
    log(2, "Leafset update: " + handle + " has " + (joined ? "joined" : "left"));

    if (!joined)
      return;

    neighborSeen(handle.getId(), System.currentTimeMillis());
  }

  public void lookupHandles(Id id, int num, Continuation command) {
    lookupHandles(id, 0, num, command);
  }

  public void lookupHandles(final Id id, final long version, int num, final Continuation command) {
    log(2, "lookupHandles("+id+"v"+version+", n="+num+")");
    
    retrieveManifest(new VersionKey(id, version), new Continuation() {
      public void receiveResult(Object o) {
        if (o instanceof Manifest) {
          log(3, "lookupHandles("+id+"): received manifest "+o+", returning handle...");
          command.receiveResult(new PastContentHandle[] {
            new GlacierContentHandle(id, version, getLocalNodeHandle(), (Manifest) o)
          });
        } else {
          warn("lookupHandles("+id+"): Cannot retrieve manifest");
          command.receiveResult(new PastContentHandle[] { null });
        }
      }
      public void receiveException(Exception e) {
        warn("lookupHandles("+id+"): Exception "+e);
        e.printStackTrace();
        command.receiveException(e);
      }
    });
  }

  public void lookup(Id id, long version, Continuation command) {
    VersionKey vkey = new VersionKey(id, version);
    log(2, "lookup("+id+"v"+version+")");
    retrieveObject(vkey, null, true, command);
  }

  public void lookup(Id id, boolean cache, Continuation command) {
    lookup(id, 0, command);
  }

  public void lookup(Id id, Continuation command) {
    lookup(id, 0, command);
  }

  public void fetch(PastContentHandle handle, Continuation command) {
    log(2, "fetch("+handle.getId()+")");
    
    if (!(handle instanceof GlacierContentHandle)) {
      command.receiveException(new GlacierException("Unknown handle type"));
      return;
    }
    
    GlacierContentHandle gch = (GlacierContentHandle) handle;
    retrieveObject(new VersionKey(gch.getId(), gch.getVersion()), gch.getManifest(), true, command);
  }

  public void retrieveManifest(final VersionKey key, final Continuation command) {
    addContinuation(new GlacierContinuation() {
      protected boolean checkedFragment[];
      protected long timeout;
      
      public String toString() {
        return "retrieveManifest("+key+")";
      }
      public void init() {
        checkedFragment = new boolean[numFragments];
        Arrays.fill(checkedFragment, false);
        timeout = System.currentTimeMillis() + manifestRequestTimeout;
        for (int i=0; i<manifestRequestInitialBurst; i++)
          sendRandomRequest();
      }
      public int numCheckedFragments() {
        int result = 0;
        for (int i=0; i<checkedFragment.length; i++)
          if (checkedFragment[i])
            result ++;
        return result;
      }
      public void sendRandomRequest() {
        if (numCheckedFragments() >= numFragments)
          return;
      
        Random rand = new Random();
        int nextID;
        do {
          nextID = rand.nextInt(numFragments);
        } while (checkedFragment[nextID]);
     
        checkedFragment[nextID] = true;
        FragmentKey nextKey = new FragmentKey(key, nextID);
        Id nextLocation = getFragmentLocation(nextKey);
        log(3, "retrieveManifest: Asking "+nextLocation+" for "+nextKey);
        endpoint.route(
          nextLocation,
          new GlacierFetchMessage(getMyUID(), nextKey, GlacierFetchMessage.FETCH_MANIFEST, getLocalNodeHandle(), nextLocation),
          null
        );
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierDataMessage) {
          GlacierDataMessage gdm = (GlacierDataMessage) o;
          
          if ((gdm.numKeys() > 0) && (gdm.getManifest(0) != null)) {
            log(3, "retrieveManifest("+key+") received manifest");
            if (policy.checkSignature(gdm.getManifest(0), key)) {
              command.receiveResult(gdm.getManifest(0));
              terminate();
            } else {
              warn("retrieveManifest("+key+"): invalid signature in "+gdm.getKey(0));
            }
          } else warn("retrieveManifest("+key+") retrieved GDM without a manifest?!?");
        } else if (o instanceof GlacierResponseMessage) {
          log(3, "retrieveManifest("+key+"): Fragment not available:" + ((GlacierResponseMessage)o).getKey(0));
          if (numCheckedFragments() < numFragments) {
            sendRandomRequest();
          } else {
            warn("retrieveManifest("+key+"): giving up");
            command.receiveResult(null);
            terminate();
          }
        } else {
          warn("retrieveManifest("+key+") received unexpected object: "+o);
        }
      }
      public void receiveException(Exception e) {
        warn("retrieveManifest("+key+") received exception: "+e);
        e.printStackTrace();
      }
      public void timeoutExpired() {
        log(3, "retrieveManifest("+key+"): Timeout ("+numCheckedFragments()+" fragments checked)");
        if (numCheckedFragments() < numFragments) {
          log(3, "retrying...");
          for (int i=0; i<manifestRequestRetryBurst; i++)
            sendRandomRequest();
          timeout += manifestRequestTimeout;
        } else {
          warn("retrieveManifest("+key+"): giving up");
          terminate();
          command.receiveResult(null);
        }
      }
      public long getTimeout() {
        return timeout;
      }
    });
  }
        
  public void retrieveObject(final VersionKey key, final Manifest manifest, final boolean beStrict, final Continuation c) {
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
            
          log(3, "retrieveObject: Received fragment #"+fragmentID+" for "+gdm.getKey(0));
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

            log(3, "Decode object: " + key);
            Serializable theObject = policy.decodeObject(material);
            log(3, "Decode complete: " + key);

            if ((theObject == null) || !(theObject instanceof PastContent)) {
              warn("retrieveObject: Decoder delivered "+theObject+", unexpected -- failed");
              c.receiveException(new GlacierException("Decoder delivered "+theObject+", unexpected -- failed"));
            } else {
              c.receiveResult(theObject);
            }
            
            terminate();
          }
        } else if (o instanceof GlacierResponseMessage) {
          log(3, "Fragment "+((GlacierResponseMessage)o).getKey(0)+" not available");
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
        log(3, "retrieveObject: Asking "+nextLocation+" for "+nextKey);
        endpoint.route(
          nextLocation,
          new GlacierFetchMessage(getMyUID(), nextKey, GlacierFetchMessage.FETCH_FRAGMENT, getLocalNodeHandle(), nextLocation),
          null
        );
      }
      public void timeoutExpired() {
        if (attemptsLeft > 0) {
          log(3, "retrieveObject: Retrying ("+attemptsLeft+" attempts left)");
          timeout = timeout + fragmentRequestTimeout;
          attemptsLeft --;

          int numRequests = numSurvivors - numHaveFragments();
          if ((attemptsLeft == 0) && beStrict)
            numRequests = numFragments - numCheckedFragments();
            
          for (int i=0; (i<numRequests) && (numCheckedFragments() < numFragments); i++) {
            sendRandomRequest();
          }
        } else {
          log(2, "retrieveObject: Giving up on "+key+" ("+restoreMaxBoosts+" attempts, "+numCheckedFragments()+" checked, "+numHaveFragments()+" gotten)");
          c.receiveException(new GlacierException("Maximum number of attempts ("+restoreMaxBoosts+") reached for key "+key));
          terminate();
        }
      }
      public long getTimeout() {
        return timeout;
      }
    });
  }
          
  public void retrieveFragment(final FragmentKey key, final Manifest manifest, final GlacierContinuation c) {
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
          log(3, "retrieveFragment: Retrying ("+attemptsLeft+" attempts left)");
          timeout = timeout + fragmentRequestTimeout;
          attemptsLeft --;
          endpoint.route(
            key.getVersionKey().getId(),
            new GlacierFetchMessage(getMyUID(), key, GlacierFetchMessage.FETCH_FRAGMENT, getLocalNodeHandle(), key.getVersionKey().getId()),
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
              
              log(3, "Reencode object: " + key.getVersionKey());
              Fragment[] frag = policy.encodeObject((PastContent) o);
              log(3, "Reencode complete: " + key.getVersionKey());
                
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
    log(3, "Received message " + msg + " with destination " + id + " from " + msg.getSource().getId());

    if (msg.isResponse()) {
      GlacierContinuation gc = (GlacierContinuation) continuations.get(new Integer(msg.getUID()));

      if (gc != null) {
        if (!gc.terminated) {
          log(3, "Resuming ["+gc+"]");
          gc.syncReceiveResult(msg);
          log(3, "---");
        } else {
          log(3, "Message UID#"+msg.getUID()+" is response, but continuation has already terminated");
        }
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
      long[] expirationA = new long[gqm.numKeys()];
      boolean[] authoritativeA = new boolean[gqm.numKeys()];
      
      for (int i=0; i<gqm.numKeys(); i++) {
        Id fragmentLocation = getFragmentLocation(gqm.getKey(i));
        log(2, "Queried for " + gqm.getKey(i) + " (at "+fragmentLocation+")");
  
        keyA[i] = gqm.getKey(i);
        haveItA[i] = fragmentStorage.exists(gqm.getKey(i));
        if (haveItA[i]) {
          FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(gqm.getKey(i));
          expirationA[i] = metadata.getCurrentExpiration();
        } else {
          expirationA[i] = 0;
        }
        log(3, "My range is "+responsibleRange);
        log(3, "Location is "+fragmentLocation);
        authoritativeA[i] = responsibleRange.containsId(fragmentLocation);
        log(3, "Result: haveIt="+haveItA[i]+" amAuthority="+authoritativeA[i]+" expiration="+expirationA[i]);
      }
      
      endpoint.route(
        null,
        new GlacierResponseMessage(gqm.getUID(), keyA, haveItA, expirationA, authoritativeA, getLocalNodeHandle(), gqm.getSource().getId(), true),
        gqm.getSource()
      );

    } else if (msg instanceof GlacierNeighborRequestMessage) {
      final GlacierNeighborRequestMessage gnrm = (GlacierNeighborRequestMessage) msg;
      final IdSet requestedNeighbors = neighborStorage.scan(gnrm.getRequestedRange());
      final int numRequested = requestedNeighbors.numElements();

      if (numRequested < 1) {
        log(3, "No neighbors in that range -- canceled");
        return; 
      }
            
      log(2, "Neighbor request for "+gnrm.getRequestedRange()+", found "+numRequested+" neighbors");
              
      final Id[] neighbors = new Id[numRequested];
      final long[] lastSeen = new long[numRequested];
            
      Iterator iter = requestedNeighbors.getIterator();
      for (int i=0; i<numRequested; i++)
        neighbors[i] = (Id)(iter.next());
              
      neighborStorage.getObject(neighbors[0], new Continuation() {
        int currentLookup = 0;
        
        public void receiveResult(Object o) {
          log(3, "Continue: NeighborRequest from "+gnrm.getSource().getId()+" for range "+gnrm.getRequestedRange());
        
          if (o == null) {
            warn("Problem while retrieving neighbors -- canceled");
            return;
          }
          
          if (o instanceof Long) {
            log(3, "Retr: Neighbor "+neighbors[currentLookup]+" was last seen at "+o);
            lastSeen[currentLookup] = ((Long)o).longValue();
            currentLookup ++;
            if (currentLookup < numRequested) {
              neighborStorage.getObject(neighbors[currentLookup], this);
            } else {
              log(3, "Sending neighbor response...");
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
          e.printStackTrace();
        }
      });
          
      return;      
      
    } else if (msg instanceof GlacierSyncMessage) {
      
      final GlacierSyncMessage gsm = (GlacierSyncMessage) msg;

      log(2, "SyncRequest from "+gsm.getSource().getId()+" for "+gsm.getRange()+" offset "+gsm.getOffsetFID());
      log(3, "Contains "+gsm.getBloomFilter());
      
      Iterator iter = fragmentStorage.scan().getIterator();
      final IdRange range = gsm.getRange();
      final int offset = gsm.getOffsetFID();
      final BloomFilter bv = gsm.getBloomFilter();
      final long earliestAcceptableExpiration = System.currentTimeMillis() + syncMinRemainingLifetime;
      
      final Vector missing = new Vector();
      
      while (iter.hasNext()) {
        FragmentKey fkey = (FragmentKey)iter.next();
        Id thisPos = getFragmentLocation(fkey);
        if (range.containsId(thisPos)) {
          FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(fkey);
          if (!bv.contains(getHashInput(fkey.getVersionKey(), metadata.getCurrentExpiration()))) {
            if (metadata.getCurrentExpiration() >= earliestAcceptableExpiration) {
              log(4, fkey+" @"+thisPos+" - MISSING");
              missing.add(fkey);
              if (missing.size() >= syncMaxFragments) {
                log(2, "Limit of "+syncMaxFragments+" missing fragments reached");
                break;
              }
            } else {
              log(4, fkey+" @"+thisPos+" - EXPIRES SOON");
            }
          } else {
            log(4, fkey+" @"+thisPos+" - OK");
          }
        } else log(4, fkey+" @"+thisPos+" - OUT OF RANGE");
      }

      if (missing.isEmpty()) {
        log(2, "No fragments missing. OK. ");
        return;
      }
      
      log(2, "Sending "+missing.size()+" fragments to "+gsm.getSource().getId());
      
      fragmentStorage.getObject((FragmentKey) missing.elementAt(0), new Continuation() {
        int currentLookup = 0;
        int manifestIndex = 0;
        final int numLookups = missing.size();
        Manifest[] manifests = new Manifest[Math.min(numLookups, manifestAggregationFactor)];
        Fragment[] fragments = new Fragment[Math.min(numLookups, manifestAggregationFactor)];
        FragmentKey[] keys = new FragmentKey[Math.min(numLookups, manifestAggregationFactor)];
        
        public void receiveResult(Object o) {
          final FragmentKey thisKey = (FragmentKey) missing.elementAt(currentLookup);

          if (o == null) {
            warn("SYN2: Fragment "+thisKey+" not found -- canceled SYN");
            return;
          }
      
          log(3, "Retrieved manifest "+thisKey + " (dest="+gsm.getSource().getId()+", offset="+offset+")");
          
          FragmentAndManifest fam = (FragmentAndManifest) o;
          
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
          log(3, "He should have key "+keys[manifestIndex]+" @"+getFragmentLocation(keys[manifestIndex]));
          manifestIndex ++;
          currentLookup ++;
          if ((manifestIndex == manifestAggregationFactor) || (currentLookup == numLookups)) {
            log(3, "Sending a packet with "+keys.length+" manifests to "+gsm.getSource().getId());
            
            endpoint.route(
              null,
              new GlacierDataMessage(getUID(), keys, fragments, manifests, getLocalNodeHandle(), gsm.getSource().getId(), false),
              gsm.getSource()
            );

            manifestIndex = 0;
            manifests = new Manifest[Math.min(numLookups - currentLookup, manifestAggregationFactor)];
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
      
      log(2, "Range query for "+requestedRange);

      Iterator iter = neighborStorage.scan().getIterator();
      Vector ccwIDs = new Vector();
      Vector cwIDs = new Vector();
      Id myID = getLocalNodeHandle().getId();
      
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

        log(3, " - #"+i+" "+currentElement+": "+ccwId+"-"+cwId);
        
        IdRange thisRange = factory.buildIdRange(ccwId, cwId);
        IdRange intersectRange = requestedRange.intersectRange(thisRange);
        if (!intersectRange.isEmpty()) {
          log(3, "     - Intersects: "+intersectRange+", sending RangeForward");
          endpoint.route(
            currentElement,
            new GlacierRangeForwardMessage(grqm.getUID(), requestedRange, grqm.getSource(), getLocalNodeHandle(), currentElement),
            null
          );
        }
      }
      
      log(3, "Finished processing range query");
      
      return;
      
    } else if (msg instanceof GlacierRangeForwardMessage) {
      GlacierRangeForwardMessage grfm = (GlacierRangeForwardMessage) msg;
      
      if (!grfm.getDestination().equals(getLocalNodeHandle().getId())) {
        log(1, "GRFM: Not for us (dest="+grfm.getDestination()+", we="+getLocalNodeHandle().getId());
        return;
      }
      
      IdRange commonRange = responsibleRange.intersectRange(grfm.getRequestedRange());
      if (!commonRange.isEmpty()) {
        log(2, "Range forward: Returning common range "+commonRange+" to requestor "+grfm.getRequestor());
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
      log(2, "Fetch request for " + gfm.getKey());

      /* FetchMessages are sent during recovery to retrieve a fragment from
         another node. They can be answered a) if the recipient has a copy
         of the fragment, or b) if the recipient has a full replica of
         the object. In the second case, the fragment is created on-the-fly */

      fragmentStorage.getObject(gfm.getKey(),
        new Continuation() {
          public void receiveResult(Object o) {
            if (o != null) {
              log(2, "Fragment "+gfm.getKey()+" found ("+o+"), returning...");
              FragmentAndManifest fam = (FragmentAndManifest) o;
              Fragment fragment = ((gfm.getRequest() & GlacierFetchMessage.FETCH_FRAGMENT)!=0) ? fam.fragment : null;
              Manifest manifest = ((gfm.getRequest() & GlacierFetchMessage.FETCH_MANIFEST)!=0) ? fam.manifest : null;
              
              endpoint.route(
                null,
                new GlacierDataMessage(gfm.getUID(), gfm.getKey(), fragment, manifest, getLocalNodeHandle(), gfm.getSource().getId(), true),
                gfm.getSource()
              );
            } else {
              log(3, "Fragment "+gfm.getKey()+" not found - but maybe we have the original? - "+gfm.getKey().getVersionKey().getId());
              policy.prefetchLocalObject(gfm.getKey().getVersionKey(),
                new Continuation() {
                  public void receiveResult(Object o) {
                    if (o != null) {
                      long theVersion = (o instanceof GCPastContent) ? ((GCPastContent)o).getVersion() : 0;
                      if (theVersion == gfm.getKey().getVersionKey().getVersion()) {
                        log(2, "Original of "+gfm.getKey()+" found ("+o+", ts="+theVersion+", expected="+gfm.getKey().getVersionKey().getVersion()+") Recoding...");
                        
                        Fragment fragment = null;
                        if ((gfm.getRequest() & GlacierFetchMessage.FETCH_FRAGMENT)!=0) {
                          Fragment[] frags = policy.encodeObject((Serializable) o);
                          log(3, "Fragments recoded ok. Returning "+gfm.getKey()+"...");
                          fragment = frags[gfm.getKey().getFragmentID()];
                        }
                          
                        endpoint.route(
                          null,
                          new GlacierDataMessage(gfm.getUID(), gfm.getKey(), fragment, null, getLocalNodeHandle(), gfm.getSource().getId(), true),
                          gfm.getSource()
                        );
                      } else {
                        log(2, "Original of "+gfm.getKey()+" not found; have different version: "+theVersion);
                        endpoint.route(
                          null,
                          new GlacierResponseMessage(gfm.getUID(), gfm.getKey(), false, 0, true, getLocalNodeHandle(), gfm.getSource().getId(), true),
                          gfm.getSource()
                        );
                      }
                    } else {
                      log(2, "Original of "+gfm.getKey()+" not found either");
                      endpoint.route(
                        null,
                        new GlacierResponseMessage(gfm.getUID(), gfm.getKey(), false, 0, true, getLocalNodeHandle(), gfm.getSource().getId(), true),
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
        final Manifest thisManifest = gdm.getManifest(i);
        
        if ((thisFragment != null) && (thisManifest != null)) {
          log(2, "Data: Fragment+Manifest for "+thisKey);

          if (!responsibleRange.containsId(getFragmentLocation(thisKey))) {
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
            log(3, "Verified ok. Storing locally.");
            
            FragmentAndManifest fam = new FragmentAndManifest(thisFragment, thisManifest);

            fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), 0), fam,
              new Continuation() {
                public void receiveResult(Object o) {
                  log(2, "Stored OK, sending receipt: "+thisKey);

                  endpoint.route(
                    null,
                    new GlacierResponseMessage(gdm.getUID(), thisKey, true, thisManifest.getExpiration(), responsibleRange.containsId(getFragmentLocation(thisKey)), getLocalNodeHandle(), gdm.getSource().getId(), true),
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

          if (!responsibleRange.containsId(getFragmentLocation(thisKey))) {
            warn("Not responsible for "+thisKey+" (at "+getFragmentLocation(thisKey)+") -- discarding");
            continue;
          }

          /* We are being informed of a fragment that 
               a) we should have, but currently don't, or
               b) we already have, but whose manifest will soon expire */

          if (fragmentStorage.exists(thisKey)) {
            final FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(thisKey);
            if (metadata.getCurrentExpiration() < thisManifest.getExpiration()) {
              log(2, "Replacing old manifest for "+thisKey+" (expires "+metadata.getCurrentExpiration()+") by new one (expires "+thisManifest.getExpiration()+")");
              fragmentStorage.getObject(thisKey, new Continuation() {
                public void receiveResult(Object o) {
                  if (o instanceof FragmentAndManifest) {
                    FragmentAndManifest fam = (FragmentAndManifest) o;
                    fam.manifest = thisManifest;
                    log(3, "Got FAM for "+thisKey+", now replacing old manifest with new one...");
                    fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), metadata.getCurrentExpiration()), fam,
                      new Continuation() {
                        public void receiveResult(Object o) {
                          log(3, "Old manifest for "+thisKey+" replaced OK, sending receipt");
                          endpoint.route(
                            null,
                            new GlacierResponseMessage(gdm.getUID(), thisKey, true, thisManifest.getExpiration(), true, getLocalNodeHandle(), gdm.getSource().getId(), true),
                            gdm.getSource()
                          );
                        }
                        public void receiveException(Exception e) {
                          warn("Cannot store refreshed manifest: "+e);
                          e.printStackTrace();
                        }
                      }
                    );
                  } else {
                    warn("Fragment store returns something other than a FAM: "+o);
                  }
                }
                public void receiveException(Exception e) {
                  warn("Cannot retrieve FAM for "+thisKey+": "+e);
                  e.printStackTrace();
                }
              });
            } else {
              warn("We already have exp="+metadata.getCurrentExpiration()+", discarding manifest with exp="+thisManifest.getExpiration());
            }
            
            continue;
          }

          log(2, "Data: Manifest for: "+thisKey+", must fetch");

          final long tStart = System.currentTimeMillis();
          retrieveFragment(thisKey, thisManifest, new GlacierContinuation() {
            public String toString() {
              return "Fetch synced fragment: "+thisKey;
            }
            public void receiveResult(Object o) {
              if (o instanceof Fragment) {
                if (!fragmentStorage.exists(thisKey)) {
                  log(2, "Received fragment "+thisKey+" (from primary) matches existing manifest, storing...");
              
                  FragmentAndManifest fam = new FragmentAndManifest((Fragment) o, thisManifest);

                  fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), 0), fam,
                    new Continuation() {
                      public void receiveResult(Object o) {
                        log(3, "Recovered fragment stored OK");
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
              warn("Exception while recovering synced fragment "+thisKey+": "+e);
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
      
        warn("Case not implemented! -- GDM");
      }
          
      return;

    } else if (msg instanceof GlacierTimeoutMessage) {
    
      /* TimeoutMessages are generated by the local node when a 
         timeout expires. */
    
      timerExpired();
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
