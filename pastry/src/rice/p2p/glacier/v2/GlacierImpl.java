package rice.p2p.glacier.v2;

import java.io.Serializable;
import java.security.*;
import java.util.*;

import rice.Continuation;
import rice.Executable;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v2.messaging.*;
import rice.p2p.past.Past;
import rice.p2p.past.PastContent;
import rice.p2p.past.PastContentHandle;
import rice.p2p.past.gc.GCPast;
import rice.p2p.past.gc.GCPastContent;
import rice.p2p.replication.ReplicationImpl;
import rice.p2p.util.DebugCommandHandler;
import rice.persistence.Storage;
import rice.persistence.StorageManager;
import rice.persistence.PersistentStorage;

import rice.pastry.commonapi.PastryIdFactory;
import rice.p2p.multiring.*;

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
  protected final Hashtable pendingTraffic;
  protected StorageManager trashStorage;
  protected long nextContinuationTimeout;
  protected IdRange responsibleRange;
  protected int nextUID;
  protected CancellableTask timer; 
  protected GlacierStatistics statistics;
  protected Vector listeners;
  protected long currentFragmentRequestTimeout;
  protected long tokenBucket;
  protected long bucketLastUpdated;
  protected long bucketMin;
  protected long bucketMax;
  protected long bucketConsumed;

  private final long SECONDS = 1000;
  private final long MINUTES = 60 * SECONDS;
  private final long HOURS = 60 * MINUTES;
  private final long DAYS = 24 * HOURS;
  private final long WEEKS = 7 * DAYS;

  private final boolean logStatistics;
  private final boolean faultInjectionEnabled;

  private final long insertTimeout;
  private final double minFragmentsAfterInsert;

  private final long refreshTimeout;

  private final long expireNeighborsDelayAfterJoin;
  private final long expireNeighborsInterval;
  private long neighborTimeout;
  
  private final long syncDelayAfterJoin;
  private final long syncMinRemainingLifetime;
  private final long syncMinQuietTime;
  private final int syncBloomFilterNumHashes;
  private final int syncBloomFilterBitsPerKey;
  private final int syncPartnersPerTrial;
  private long syncInterval;
  private final long syncRetryInterval;
  private int syncMaxFragments;
  
//  private final int fragmentRequestMaxAttempts = 3;
  private final int fragmentRequestMaxAttempts;
  private final long fragmentRequestTimeoutDefault;
  private final long fragmentRequestTimeoutMin;
  private final long fragmentRequestTimeoutMax;
  private final long fragmentRequestTimeoutDecrement;

  private final long manifestRequestTimeout;
  private final long manifestRequestInitialBurst;
  private final long manifestRequestRetryBurst;
  private final int manifestAggregationFactor;

  private final long overallRestoreTimeout;
  
  private final long handoffDelayAfterJoin;
  private final long handoffInterval;
  private final int handoffMaxFragments;

  private final long garbageCollectionInterval;
  private final int garbageCollectionMaxFragmentsPerRun;

  private final long localScanInterval;
  private final int localScanMaxFragmentsPerRun;

  private final double restoreMaxRequestFactor;
  private final int restoreMaxBoosts;

  private final long rateLimitedCheckInterval;
  private int rateLimitedRequestsPerSecond;

  private final boolean enableBulkRefresh;
  private final long bulkRefreshProbeInterval;
  private final double bulkRefreshMaxProbeFactor;
  private final long bulkRefreshManifestInterval;
  private final int bulkRefreshManifestAggregationFactor;
  private final int bulkRefreshPatchAggregationFactor;
  private final long bulkRefreshPatchInterval;
  private final int bulkRefreshPatchRetries;

  private long bucketTokensPerSecond;
  private long bucketMaxBurstSize;

  private final double jitterRange;

  private final long statisticsReportInterval;

  private final int maxActiveRestores;
  private int[] numActiveRestores;

  private final char tagNeighbor = 1;
  private final char tagSync = 2;
  private final char tagSyncManifests = 3;
  private final char tagSyncFetch = 4;
  private final char tagHandoff = 5;
  private final char tagDebug = 6;
  private final char tagRefresh = 7;
  private final char tagInsert = 8;
  private final char tagLookupHandles = 9;
  private final char tagLookup = 10;
  private final char tagFetch = 11;
  private final char tagLocalScan = 12;
  private final char tagMax = 13;

  private Environment environment;
  
  public GlacierImpl(Node nodeArg, StorageManager fragmentStorageArg, StorageManager neighborStorageArg, int numFragmentsArg, int numSurvivorsArg, IdFactory factoryArg, String instanceArg, GlacierPolicy policyArg, Environment env) {
    this.environment = env;

    Parameters p = environment.getParameters();
    
    logStatistics = p.getBoolean("p2p_glacier_logStatistics");
    faultInjectionEnabled = p.getBoolean("p2p_glacier_faultInjectionEnabled");

    insertTimeout = p.getLong("p2p_glacier_faultInjectionEnabled");
    minFragmentsAfterInsert = p.getDouble("p2p_glacier_minFragmentsAfterInsert");

    refreshTimeout = p.getLong("p2p_glacier_refreshTimeout");

    expireNeighborsDelayAfterJoin = p.getLong("p2p_glacier_expireNeighborsDelayAfterJoin");
    expireNeighborsInterval = p.getLong("p2p_glacier_expireNeighborsInterval");
    neighborTimeout = p.getLong("p2p_glacier_neighborTimeout");
    
    syncDelayAfterJoin = p.getLong("p2p_glacier_syncDelayAfterJoin");
    syncMinRemainingLifetime = p.getLong("p2p_glacier_syncMinRemainingLifetime");
    syncMinQuietTime = p.getLong("p2p_glacier_syncMinQuietTime");
    syncBloomFilterNumHashes = p.getInt("p2p_glacier_syncBloomFilterNumHashes");
    syncBloomFilterBitsPerKey = p.getInt("p2p_glacier_syncBloomFilterBitsPerKey");
    syncPartnersPerTrial = p.getInt("p2p_glacier_syncPartnersPerTrial");
    syncInterval = p.getLong("p2p_glacier_syncInterval");
    syncRetryInterval = p.getLong("p2p_glacier_syncRetryInterval");
    syncMaxFragments = p.getInt("p2p_glacier_syncMaxFragments");
    
    fragmentRequestMaxAttempts = p.getInt("p2p_glacier_fragmentRequestMaxAttempts");
    fragmentRequestTimeoutDefault = p.getLong("p2p_glacier_fragmentRequestTimeoutDefault");
    fragmentRequestTimeoutMin = p.getLong("p2p_glacier_fragmentRequestTimeoutMin");
    fragmentRequestTimeoutMax = p.getLong("p2p_glacier_fragmentRequestTimeoutMax");
    fragmentRequestTimeoutDecrement = p.getLong("p2p_glacier_fragmentRequestTimeoutDecrement");

    manifestRequestTimeout = p.getLong("p2p_glacier_manifestRequestTimeout");
    manifestRequestInitialBurst = p.getLong("p2p_glacier_manifestRequestInitialBurst");
    manifestRequestRetryBurst = p.getLong("p2p_glacier_manifestRequestRetryBurst");
    manifestAggregationFactor = p.getInt("p2p_glacier_manifestAggregationFactor");

    overallRestoreTimeout = p.getLong("p2p_glacier_overallRestoreTimeout");
    
    handoffDelayAfterJoin = p.getLong("p2p_glacier_handoffDelayAfterJoin");
    handoffInterval = p.getLong("p2p_glacier_handoffInterval");
    handoffMaxFragments = p.getInt("p2p_glacier_handoffMaxFragments");

    garbageCollectionInterval = p.getLong("p2p_glacier_garbageCollectionInterval");
    garbageCollectionMaxFragmentsPerRun = p.getInt("p2p_glacier_garbageCollectionMaxFragmentsPerRun");

    localScanInterval = p.getLong("p2p_glacier_localScanInterval");
    localScanMaxFragmentsPerRun = p.getInt("p2p_glacier_localScanMaxFragmentsPerRun");

    restoreMaxRequestFactor = p.getDouble("p2p_glacier_restoreMaxRequestFactor");
    restoreMaxBoosts = p.getInt("p2p_glacier_restoreMaxBoosts");

    rateLimitedCheckInterval = p.getLong("p2p_glacier_rateLimitedCheckInterval");
    rateLimitedRequestsPerSecond = p.getInt("p2p_glacier_rateLimitedRequestsPerSecond");

    enableBulkRefresh = p.getBoolean("p2p_glacier_enableBulkRefresh");
    bulkRefreshProbeInterval = p.getLong("p2p_glacier_bulkRefreshProbeInterval");
    bulkRefreshMaxProbeFactor = p.getDouble("p2p_glacier_bulkRefreshMaxProbeFactor");
    bulkRefreshManifestInterval = p.getLong("p2p_glacier_bulkRefreshManifestInterval");
    bulkRefreshManifestAggregationFactor = p.getInt("p2p_glacier_bulkRefreshManifestAggregationFactor");
    bulkRefreshPatchAggregationFactor = p.getInt("p2p_glacier_bulkRefreshPatchAggregationFactor");
    bulkRefreshPatchInterval = p.getLong("p2p_glacier_bulkRefreshPatchInterval");
    bulkRefreshPatchRetries = p.getInt("p2p_glacier_bulkRefreshPatchRetries");

    bucketTokensPerSecond = p.getLong("p2p_glacier_bucketTokensPerSecond");
    bucketMaxBurstSize = p.getLong("p2p_glacier_bucketMaxBurstSize");

    jitterRange = p.getDouble("p2p_glacier_jitterRange");

    statisticsReportInterval = p.getLong("p2p_glacier_statisticsReportInterval");

    maxActiveRestores = p.getInt("p2p_glacier_maxActiveRestores");

    
    
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
    this.pendingTraffic = new Hashtable();
    this.timer = null;
    this.nextContinuationTimeout = -1;
    this.statistics = new GlacierStatistics(tagMax, environment);
    this.listeners = new Vector();
    this.numActiveRestores = new int[1];
    this.numActiveRestores[0] = 0;
    this.currentFragmentRequestTimeout = fragmentRequestTimeoutDefault;
    this.tokenBucket = 0;
    this.bucketLastUpdated = environment.getTimeSource().currentTimeMillis();
    determineResponsibleRange();
  }
  
  public void startup() {
  
    /* Neighbor requests */

    addContinuation(new GlacierContinuation() {
      long nextTimeout;
      
      public String toString() {
        return "Neighbor continuation";
      }
      public void init() {
        nextTimeout = environment.getTimeSource().currentTimeMillis() + expireNeighborsDelayAfterJoin;

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
            neighborSeen(leafSet.getHandle(k).getId(), environment.getTimeSource().currentTimeMillis());
            log(Logger.INFO, "Asking "+leafSet.getHandle(k).getId()+" about neighbors in "+leafRange);
            sendMessage(
              null,
              new GlacierNeighborRequestMessage(getMyUID(), leafRange, getLocalNodeHandle(), leafSet.getHandle(k).getId(), tagNeighbor),
              leafSet.getHandle(k)
            );
          }
        }
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierNeighborResponseMessage) {
          final GlacierNeighborResponseMessage gnrm = (GlacierNeighborResponseMessage) o;
          log(Logger.FINE, "NeighborResponse from "+gnrm.getSource()+" with "+gnrm.numNeighbors()+" neighbors");
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
        nextTimeout = environment.getTimeSource().currentTimeMillis() + expireNeighborsInterval;

        final long earliestAcceptableDate = environment.getTimeSource().currentTimeMillis() - neighborTimeout;
        IdSet allNeighbors = neighborStorage.scan();
        Iterator iter = allNeighbors.getIterator();
        NodeHandleSet leafSet = endpoint.neighborSet(999);

        log(Logger.INFO, "Checking neighborhood for expired certificates...");
        
        while (iter.hasNext()) {
          final Id thisNeighbor = (Id) iter.next();
          if (leafSet.memberHandle(thisNeighbor)) {
            log(Logger.FINE, "CNE: Refreshing current neighbor: "+thisNeighbor);
            neighborSeen(thisNeighbor, environment.getTimeSource().currentTimeMillis());
          } else {
            log(Logger.FINE, "CNE: Retrieving "+thisNeighbor);
            neighborStorage.getObject(thisNeighbor, new Continuation() {
              public void receiveResult(Object o) {
                if (o==null) {
                  warn("CNE: Cannot retrieve neighbor "+thisNeighbor);
                  return;
                }
              
                long lastSeen = ((Long)o).longValue();
                if (lastSeen < earliestAcceptableDate) {
                  log(Logger.INFO, "CNE: Removing expired neighbor "+thisNeighbor+" ("+lastSeen+"<"+earliestAcceptableDate+")");
                  neighborStorage.unstore(thisNeighbor, new Continuation() {
                    public void receiveResult(Object o) {
                      log(Logger.FINE, "CNE unstore successful: "+thisNeighbor+", returned "+o);
                    }
                    public void receiveException(Exception e) {
                      warn("CNE unstore failed: "+thisNeighbor+", returned "+e);
                    }
                  });
                } else {
                  log(Logger.INFO, "CNE: Neighbor "+thisNeighbor+" still active, last seen "+lastSeen);
                }
              }
              public void receiveException(Exception e) {
                log(Logger.WARNING, "CNE: Exception while retrieving neighbor "+thisNeighbor+", e="+e);
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
      int offset;
      
      public String toString() {
        return "Sync continuation";
      }
      public void init() {
        nextTimeout = environment.getTimeSource().currentTimeMillis() + syncDelayAfterJoin;
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierRangeResponseMessage) {
          final GlacierRangeResponseMessage grrm = (GlacierRangeResponseMessage) o;

          Id ccwId = getFragmentLocation(grrm.getCommonRange().getCCWId(), numFragments-offset, 0);
          Id cwId = getFragmentLocation(grrm.getCommonRange().getCWId(), numFragments-offset, 0);
          final IdRange originalRange = factory.buildIdRange(ccwId, cwId);
        
          log(Logger.INFO, "Range response (offset: "+offset+"): "+grrm.getCommonRange()+", original="+originalRange);
        
          final IdSet keySet = fragmentStorage.scan();
          endpoint.process(new Executable() {
            public Object execute() {
              BloomFilter bv = new BloomFilter((2*keySet.numElements()+5)*syncBloomFilterBitsPerKey, syncBloomFilterNumHashes, environment.getRandomSource());
              Iterator iter = keySet.getIterator();

              while (iter.hasNext()) {
                FragmentKey fkey = (FragmentKey)iter.next();
                Id thisPos = getFragmentLocation(fkey);
                if (originalRange.containsId(thisPos)) {
                  FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(fkey);
                  if (metadata != null) {
                    long currentExp = metadata.getCurrentExpiration();
                    long prevExp = metadata.getPreviousExpiration();
                    log(Logger.FINER, " - Adding "+fkey+" as "+fkey.getVersionKey().getId()+", ecur="+currentExp+", eprev="+prevExp);
                    bv.add(getHashInput(fkey.getVersionKey(), currentExp));
                    bv.add(getHashInput(fkey.getVersionKey(), prevExp));
                  } else {
                    warn("SYNC Cannot read metadata of object "+fkey.toStringFull()+", storage returned null");
                  }
                }
              }
              
              return bv;
            }
          }, new Continuation() {
            public void receiveResult(Object o) {
              if (o instanceof BloomFilter) {
                BloomFilter bv = (BloomFilter) o;
                log(Logger.FINE, "Got "+bv);        
                log(Logger.INFO, keySet.numElements()+" keys added, sending sync request...");

                sendMessage(
                  null,
                  new GlacierSyncMessage(getUID(), grrm.getCommonRange(), offset, bv, getLocalNodeHandle(), grrm.getSource().getId(), tagSync),
                  grrm.getSource()
                );
              } else {
                warn("While processing range response: Result is of unknown type: "+o+" -- discarding request");
              }
            }
            public void receiveException(Exception e) {
              warn("Exception while processing range response: "+e+" -- discarding request");
              e.printStackTrace();
            }
          });
        } else {
          warn("Unknown result in sync continuation: "+o+" -- discarded");
        }
      }
      public void receiveException(Exception e) {
        warn("Exception in sync continuation: "+e);
        e.printStackTrace();
        terminate();
      }
      public void timeoutExpired() {
        if (numActiveRestores[0] > 0) {
          log(Logger.INFO, "Sync postponed; "+numActiveRestores[0]+" fetches pending");
          nextTimeout = environment.getTimeSource().currentTimeMillis() + jitterTerm(syncRetryInterval);
        } else {
          nextTimeout = environment.getTimeSource().currentTimeMillis() + jitterTerm(syncInterval);
          offset = 1+environment.getRandomSource().nextInt(numFragments-1);

          Id dest = getFragmentLocation(getLocalNodeHandle().getId(), offset, 0);
          Id ccwId = getFragmentLocation(responsibleRange.getCCWId(), offset, 0);
          Id cwId = getFragmentLocation(responsibleRange.getCWId(), offset, 0);
          IdRange requestedRange = factory.buildIdRange(ccwId, cwId);
            
          log(Logger.INFO, "Sending range query for ("+requestedRange+") to "+dest);
          sendMessage(
            dest,
            new GlacierRangeQueryMessage(getMyUID(), requestedRange, getLocalNodeHandle(), dest, tagSync),
            null
          );
        }
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
        nextTimeout = environment.getTimeSource().currentTimeMillis() + handoffDelayAfterJoin;
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierResponseMessage) {
          final GlacierResponseMessage grm = (GlacierResponseMessage) o;
          log(Logger.FINE, "Received handoff response from "+grm.getSource().getId()+" with "+grm.numKeys()+" keys");
          for (int i=0; i<grm.numKeys(); i++) {
            final FragmentKey thisKey = grm.getKey(i);
            if (grm.getAuthoritative(i)) {
              if (grm.getHaveIt(i)) {
                Id thisPos = getFragmentLocation(thisKey);
                if (!responsibleRange.containsId(thisPos)) {
                  log(Logger.FINE, "Deleting fragment "+thisKey);
                  deleteFragment(thisKey, new Continuation() {
                    public void receiveResult(Object o) {
                      log(Logger.FINE, "Handed off fragment deleted: "+thisKey+" (o="+o+")");
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
                      log(Logger.INFO, "Fragment "+thisKey+" found ("+o+"), handing off...");
                      FragmentAndManifest fam = (FragmentAndManifest) o;
                      sendMessage(
                        null,
                        new GlacierDataMessage(grm.getUID(), thisKey, fam.fragment, fam.manifest, getLocalNodeHandle(), grm.getSource().getId(), true, tagHandoff),
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
              log(Logger.FINE, "Ignoring fragment "+thisKey+" (haveIt="+grm.getHaveIt(i)+", authoritative="+grm.getAuthoritative(i)+")");
            }
          }
        } else if (o instanceof GlacierDataMessage) {
          final GlacierDataMessage gdm = (GlacierDataMessage) o;
          for (int i=0; i<gdm.numKeys(); i++) {
            final FragmentKey thisKey = gdm.getKey(i);
            final Fragment thisFragment = gdm.getFragment(i);
            final Manifest thisManifest = gdm.getManifest(i);
        
            if ((thisFragment != null) && (thisManifest != null)) {
              log(Logger.INFO, "Handoff: Received Fragment+Manifest for "+thisKey);

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
                log(Logger.FINE, "Handoff: Verified ok. Storing locally.");
            
                FragmentAndManifest fam = new FragmentAndManifest(thisFragment, thisManifest);
  
                fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), 0, environment.getTimeSource().currentTimeMillis()), fam,
                  new Continuation() {
                    public void receiveResult(Object o) {
                      log(Logger.INFO, "Handoff: Stored OK, sending receipt: "+thisKey);

                      sendMessage(
                        null,
                        new GlacierResponseMessage(gdm.getUID(), thisKey, true, thisManifest.getExpiration(), responsibleRange.containsId(getFragmentLocation(thisKey)), getLocalNodeHandle(), gdm.getSource().getId(), true, tagHandoff),
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
                sendMessage(
                  null,
                  new GlacierResponseMessage(gdm.getUID(), thisKey, true, thisManifest.getExpiration(), true, getLocalNodeHandle(), gdm.getSource().getId(), true, tagHandoff),
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
        nextTimeout = environment.getTimeSource().currentTimeMillis() + jitterTerm(handoffInterval);
        log(Logger.INFO, "Checking fragment storage for fragments to hand off...");
        log(Logger.FINE, "Currently responsible for: "+responsibleRange);
        Iterator iter = fragmentStorage.scan().getIterator();
        Vector handoffs = new Vector();
        Id destination = null;
  
        while (iter.hasNext()) {
          FragmentKey fkey = (FragmentKey) iter.next();
          Id thisPos = getFragmentLocation(fkey);
          if (!responsibleRange.containsId(thisPos)) {
            log(Logger.FINE, "Must hand off "+fkey+" @"+thisPos);
            handoffs.add(fkey);

            if (handoffs.size() >= handoffMaxFragments) {
              log(Logger.FINE, "Limit of "+handoffMaxFragments+" reached for handoff");
              break;
            }
            
            if (destination == null)
              destination = thisPos;
          }
        }
        
        if (destination == null) {
          log(Logger.FINE, "Nothing to hand off -- returning");
          return;
        }
        
        int numHandoffs = Math.min(handoffs.size(), handoffMaxFragments);
        log(Logger.INFO, "Handing off "+numHandoffs+" fragments (out of "+handoffs.size()+")");
        FragmentKey[] keys = new FragmentKey[numHandoffs];
        for (int i=0; i<numHandoffs; i++)
          keys[i] = (FragmentKey) handoffs.elementAt(i);

        sendMessage(
          destination,
          new GlacierQueryMessage(getMyUID(), keys, getLocalNodeHandle(), destination, tagHandoff),
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
        nextTimeout = environment.getTimeSource().currentTimeMillis() + garbageCollectionInterval;
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
        nextTimeout = environment.getTimeSource().currentTimeMillis() + garbageCollectionInterval;

        final long now = environment.getTimeSource().currentTimeMillis();
        IdSet fragments = fragmentStorage.scan();
        int doneSoFar = 0, candidates = 0;

        log(Logger.INFO, "Garbage collection started at "+now+", scanning "+fragments.numElements()+" fragment(s)...");
        Iterator iter = fragments.getIterator();
        while (iter.hasNext()) {
          final Id thisKey = (Id) iter.next();
          final FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(thisKey);
          if (metadata != null) {
            if (metadata.getCurrentExpiration() < now) {
              candidates ++;
              if (doneSoFar < garbageCollectionMaxFragmentsPerRun) {
                doneSoFar ++;
                deleteFragment(thisKey, new Continuation() {
                  public void receiveResult(Object o) {
                    log(Logger.INFO, "GC collected "+thisKey.toStringFull()+", expired "+(now-metadata.getCurrentExpiration())+" msec ago");
                  }
                  public void receiveException(Exception e) {
                    log(Logger.FINE, "GC cannot collect "+thisKey.toStringFull());
                  }
                });
              }
            }
          } else {
            warn("GC cannot read metadata in object "+thisKey.toStringFull()+", storage returned null");
          }
        }
        
        log(Logger.INFO, "Garbage collection completed at "+environment.getTimeSource().currentTimeMillis());
        log(Logger.INFO, "Found "+candidates+" candidate(s), collected "+doneSoFar);
      }
    });
    
    /* Local scan */
    
    addContinuation(new GlacierContinuation() {
      long nextTimeout;
      
      public String toString() {
        return "Local scan";
      }
      public void init() {
        nextTimeout = environment.getTimeSource().currentTimeMillis() + localScanInterval;
      }
      public void receiveResult(Object o) {
        warn("Local scan received object: "+o);
      }
      public void receiveException(Exception e) {
        warn("Local scan received exception: "+e);
        e.printStackTrace();
      }
      public long getTimeout() {
        return nextTimeout;
      }
      public void timeoutExpired() {
        nextTimeout = environment.getTimeSource().currentTimeMillis() + jitterTerm(localScanInterval);

        final IdSet fragments = fragmentStorage.scan();
        final long now = environment.getTimeSource().currentTimeMillis();
        java.util.TreeSet queries = new java.util.TreeSet();

        log(Logger.INFO, "Performing local scan over "+fragments.numElements()+" fragment(s)...");
        Iterator iter = fragments.getIterator();
        while (iter.hasNext()) {
          final FragmentKey thisKey = (FragmentKey) iter.next();
          FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(thisKey);
          if ((metadata != null) && (metadata.currentExpirationDate >= now)) {
            final Id thisObjectKey = thisKey.getVersionKey().getId();
            final long thisVersion = thisKey.getVersionKey().getVersion();
            final int thisFragmentID = thisKey.getFragmentID();
            final int fidLeft = (thisFragmentID + numFragments - 1) % numFragments;
            final int fidRight = (thisFragmentID + 1) % numFragments;
          
            if (responsibleRange.containsId(getFragmentLocation(thisObjectKey, fidLeft, thisVersion))) {
              if (!fragments.isMemberId(thisKey.getPeerKey(fidLeft))) {
                log(Logger.FINER, "Missing: "+thisKey+" L="+fidLeft);
                queries.add(thisKey.getVersionKey());
              }
            }
          
            if (responsibleRange.containsId(getFragmentLocation(thisObjectKey, fidRight, thisVersion))) {
              if (!fragments.isMemberId(thisKey.getPeerKey(fidRight))) {
                log(Logger.FINER, "Missing: "+thisKey+" R="+fidRight);
                queries.add(thisKey.getVersionKey());
              }
            }
          } else {
            log(Logger.FINER, "Expired, ignoring in local scan: "+thisKey);
          }
        }
        
        if (!queries.isEmpty()) {
          log(Logger.INFO, "Local scan completed; "+queries.size()+" objects incomplete in local store");
          iter = queries.iterator();
          int queriesSent = 0;
          
          while (iter.hasNext() && (queriesSent < localScanMaxFragmentsPerRun)) {
            final VersionKey thisVKey = (VersionKey) iter.next();
            
            int localFragmentID = 0;
            int queriesHere = 0;
            for (int i=0; i<numFragments; i++) {
              FragmentKey keyHere = new FragmentKey(thisVKey, i);
              if (fragments.isMemberId(keyHere)) {
                localFragmentID = i;
                break;
              } else if (responsibleRange.containsId(getFragmentLocation(keyHere))) {
                queriesHere ++;
              }
            }
            
            log(Logger.FINE, "Local scan: Fetching manifest for "+thisVKey+" ("+queriesHere+" pending queries)");
            queriesSent += queriesHere;

            fragmentStorage.getObject(new FragmentKey(thisVKey, localFragmentID), new Continuation() {
              public void receiveResult(Object o) {
                if (o instanceof FragmentAndManifest) {
                  final Manifest thisManifest = ((FragmentAndManifest)o).manifest;
                  
                  for (int i=0; i<numFragments; i++) {
                    final FragmentKey thisKey = new FragmentKey(thisVKey, i);
                    if (responsibleRange.containsId(getFragmentLocation(thisKey))) {
                      if (!fragments.isMemberId(thisKey)) {
                        log(Logger.FINE, "Local scan: Sending query for "+thisKey);
                        final long tStart = environment.getTimeSource().currentTimeMillis();
                        rateLimitedRetrieveFragment(thisKey, thisManifest, tagLocalScan, new GlacierContinuation() {
                          public String toString() {
                            return "Local scan: Fetch fragment: "+thisKey;
                          }
                          public void receiveResult(Object o) {
                            if (o instanceof Fragment) {
                              log(Logger.INFO, "Local scan: Received fragment "+thisKey+" (from primary) matches existing manifest, storing...");
              
                              FragmentAndManifest fam = new FragmentAndManifest((Fragment) o, thisManifest);

                              fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), 0, environment.getTimeSource().currentTimeMillis()), fam,
                                new Continuation() {
                                  public void receiveResult(Object o) {
                                    log(Logger.FINE, "Local scan: Recovered fragment stored OK");
                                  }
                                  public void receiveException(Exception e) {
                                    warn("Local scan: receiveException(" + e + ") while storing a fragment with existing manifest (key=" + thisKey + ")");
                                  }
                                }
                              );
                            } else {
                              warn("Local scan: FS received something other than a fragment: "+o);
                            }
                          }
                          public void receiveException(Exception e) {
                            warn("Local scan: Exception while recovering synced fragment "+thisKey+": "+e);
                            e.printStackTrace();
                            terminate();
                          }
                          public void timeoutExpired() {
                            warn("Local scan: Timeout while fetching synced fragment "+thisKey+" -- aborted");
                            terminate();              
                          }
                          public long getTimeout() {
                            return tStart + overallRestoreTimeout;
                          }
                        });
                      }
                    }
                  }
                } else {
                  warn("Local scan: Cannot retrieve "+thisVKey+" from local store, received o="+o);
                }
              }
              public void receiveException(Exception e) {
                warn("Local scan: Cannot retrieve "+thisVKey+" from local store, exception e="+e);
                e.printStackTrace();
              }
            });
          }
          
          log(Logger.INFO, queriesSent + " queries sent after local scan");
        } else {
          log(Logger.INFO, "Local scan completed; no missing fragments");
        }
      }
    });

    /* Traffic shaper */
    
    addContinuation(new GlacierContinuation() {
      long nextTimeout;
      
      public String toString() {
        return "Traffic shaper";
      }
      public void init() {
        nextTimeout = environment.getTimeSource().currentTimeMillis() + rateLimitedCheckInterval;
      }
      public void receiveResult(Object o) {
        warn("TS received object: "+o);
      }
      public void receiveException(Exception e) {
        warn("TS received exception: "+e);
        e.printStackTrace();
      }
      public long getTimeout() {
        return nextTimeout;
      }
      public void timeoutExpired() {
        /* Use relative timeout to avoid backlog! */
        nextTimeout = environment.getTimeSource().currentTimeMillis() + (1 * SECONDS);

        if (pendingTraffic.isEmpty()) {
          log(Logger.FINE, "Traffic shaper: Idle");
          nextTimeout += rateLimitedCheckInterval;
          return;
        }
        
        int numCurrentRestores = 0;
        synchronized (numActiveRestores) {
          numCurrentRestores = numActiveRestores[0];
        }

        log(Logger.INFO, "Traffic shaper: "+pendingTraffic.size()+" jobs waiting ("+numCurrentRestores+" active jobs, "+tokenBucket+" tokens)");

        updateTokenBucket();
        if ((numCurrentRestores < maxActiveRestores) && (tokenBucket>0)) {
          for (int i=0; i<rateLimitedRequestsPerSecond; i++) {
            if (!pendingTraffic.isEmpty()) {
              Enumeration keys = pendingTraffic.keys();
              Object thisKey = (Object) keys.nextElement();
              log(Logger.FINE, "Sending request "+thisKey);
              Continuation c = (Continuation) pendingTraffic.remove(thisKey);
              c.receiveResult(new Boolean(true));
            }
          }
        }
      }
    });

    /* Statistics */
    
    addContinuation(new GlacierContinuation() {
      long nextTimeout;
      
      public String toString() {
        return "Statistics";
      }
      public void init() {
        nextTimeout = environment.getTimeSource().currentTimeMillis() + statisticsReportInterval;
      }
      public void receiveResult(Object o) {
        warn("STAT received object: "+o);
      }
      public void receiveException(Exception e) {
        warn("STAT received exception: "+e);
        e.printStackTrace();
      }
      public long getTimeout() {
        return nextTimeout;
      }
      public void timeoutExpired() {
        nextTimeout += statisticsReportInterval;

        if (!listeners.isEmpty()) {
          statistics.pendingRequests = pendingTraffic.size();
          statistics.numNeighbors = neighborStorage.scan().numElements();
          statistics.numFragments = fragmentStorage.scan().numElements();
          statistics.numContinuations = continuations.size();
//          statistics.numObjectsInTrash = (trashStorage == null) ? 0 : trashStorage.scan().numElements();
          statistics.responsibleRange = responsibleRange;
          statistics.activeFetches = numActiveRestores[0];
          statistics.bucketMin = bucketMin;
          statistics.bucketMax = bucketMax;
          statistics.bucketConsumed = bucketConsumed;
          statistics.bucketTokensPerSecond = bucketTokensPerSecond;
          statistics.bucketMaxBurstSize = bucketMaxBurstSize;
          bucketMin = tokenBucket;
          bucketMax = tokenBucket;
          bucketConsumed = 0;
          
          Storage storageF = fragmentStorage.getStorage();
          if (storageF instanceof PersistentStorage)
            statistics.fragmentStorageSize = ((PersistentStorage)storageF).getTotalSize();
          
          Storage storageT = (trashStorage == null) ? null : trashStorage.getStorage();
          if (storageT instanceof PersistentStorage)
            statistics.trashStorageSize = ((PersistentStorage)storageT).getTotalSize();

          if (logStatistics)
            statistics.dump();
          
          Enumeration enumeration = listeners.elements();
          while (enumeration.hasMoreElements()) {
            GlacierStatisticsListener gsl = (GlacierStatisticsListener) enumeration.nextElement();
            gsl.receiveStatistics(statistics);
          }
        }
        
        statistics = new GlacierStatistics(tagMax, environment);
      }
    });
  }

  protected void updateTokenBucket() {
    final long now = environment.getTimeSource().currentTimeMillis();
    final long contentsBefore = tokenBucket;
    
    while (bucketLastUpdated < now) {
      bucketLastUpdated += SECONDS/10;
      tokenBucket += bucketTokensPerSecond/10;
      if (tokenBucket > bucketMaxBurstSize)
        tokenBucket = bucketMaxBurstSize;
    }

    if (bucketMax < tokenBucket)
      bucketMax = tokenBucket;
      
    log(Logger.FINE, "Token bucket contains "+tokenBucket+" tokens (added "+(tokenBucket-contentsBefore)+")");
  }

  private long jitterTerm(long basis) {
    return (long)((1-jitterRange)*basis) + environment.getRandomSource().nextInt((int)(2*jitterRange*basis));
  }

  private void deleteFragment(final Id fkey, final Continuation command) {
    if (trashStorage != null) {
      log(Logger.INFO, "Moving fragment "+fkey.toStringFull()+" to trash");
      fragmentStorage.getObject(fkey, new Continuation() {
        public void receiveResult(Object o) {
          log(Logger.FINE, "Fragment "+fkey.toStringFull()+" retrieved, storing in trash");
          if (o != null) {
            trashStorage.store(fkey, null, (Serializable) o, new Continuation() {
              public void receiveResult(Object o) {
                log(Logger.FINE, "Deleting fragment "+fkey.toStringFull());
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
      log(Logger.INFO, "Deleting fragment "+fkey.toStringFull());
      fragmentStorage.unstore(fkey, command);
    }
  }

  public void sendMessage(Id id, GlacierMessage message, NodeHandle hint) {
    String className = message.getClass().getName();
    log(Logger.INFO, "Send " + ((hint == null) ? "OVR" : "DIR") + " T" + ((int)message.getTag()) + " " + className.substring(className.lastIndexOf('.') + 8));
    statistics.messagesSentByTag[message.getTag()] ++;
    endpoint.route(id, message, hint);
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

  private static String dump(byte[] data, boolean linebreak) {
    final String hex = "0123456789ABCDEF";
    String result = "";
    
    for (int i=0; i<data.length; i++) {
      int d = data[i];
      if (d<0)
        d+= 256;
      int hi = (d>>4);
      int lo = (d&15);
        
      result = result + hex.charAt(hi) + hex.charAt(lo);
      if (linebreak && (((i%16)==15) || (i==(data.length-1))))
        result = result + "\n";
      else if (i!=(data.length-1))
        result = result + " ";
    }
    
    return result;
  }

  private void addContinuation(GlacierContinuation gc) {
    int thisUID = getUID();
    gc.setup(thisUID);
    continuations.put(new Integer(thisUID), gc);
    gc.init();
    
    long thisTimeout = gc.getTimeout();
    long now = environment.getTimeSource().currentTimeMillis();
    
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
    
    log(Logger.FINE, "Determining responsible range");
    
    Iterator iter = neighborStorage.scan().getIterator();
    while (iter.hasNext()) {
      Id thisNeighbor = (Id) iter.next();
      log(Logger.FINER, "Considering neighbor: "+thisNeighbor);
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
      
    log(Logger.FINE, "XCCW: "+xccwPeer+" CCW: "+ccwPeer+" ME: "+myNodeId+" CW: "+cwPeer+" XCW: "+xcwPeer);
      
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
    
    log(Logger.INFO, "New range: "+responsibleRange);
  }

  private void log(int level, String str) {
    environment.getLogManager().getLogger(GlacierImpl.class, instance).log(level,str);
  }

  private void warn(String str) {
    log(Logger.WARNING,str);
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

  private static byte[] getDistance(double d) {
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
    double totalOffset = (((float)fragmentNr) / ((float)numFragments)) + version * (1.0/2.7182821);
    return objectKey.addToId(factory.buildIdDistance(getDistance(totalOffset - Math.floor(totalOffset))));
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
    if (command.indexOf(" ") < 0)
      return null;
  
    String myInstance = "glacier."+instance.substring(instance.lastIndexOf("-") + 1);
    String requestedInstance = command.substring(0, command.indexOf(" "));
    String cmd = command.substring(requestedInstance.length() + 1);
    
    if (!requestedInstance.equals(myInstance) && !requestedInstance.equals("g"))
      return null;
  
    log(Logger.INFO, "Debug command: "+cmd);
  
    if (cmd.startsWith("ls")) {
      FragmentKeySet keyset = (FragmentKeySet) fragmentStorage.scan();
      Iterator iter = keyset.getIterator();
      StringBuffer result = new StringBuffer();
  
      long now = environment.getTimeSource().currentTimeMillis();
      if (cmd.indexOf("-r") < 0)
        now = 0;
    
      result.append(keyset.numElements()+ " fragment(s)\n");
      
      while (iter.hasNext()) {
        FragmentKey thisKey = (FragmentKey) iter.next();
        boolean isMine = responsibleRange.containsId(getFragmentLocation(thisKey));
        FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(thisKey);
        if (metadata != null) {
          result.append(((Id)thisKey).toStringFull()+" "+(isMine ? "OK" : "MI")+" "+
              (metadata.getCurrentExpiration()-now)+" "+(metadata.getPreviousExpiration()-now)+"\n");
        }
      }
      
      return result.toString();
    }

    if (cmd.startsWith("show config")) {
      return 
        "numFragments = " + numFragments + "\n" +
        "numSurvivors = " + numSurvivors + "\n" +
        "insertTimeout = " + (int)(insertTimeout / SECONDS) + " sec\n" +
        "minFragmentsAfterInsert = "+ minFragmentsAfterInsert + "x" + numSurvivors + "\n" +
        "refreshTimeout = " + (int)(refreshTimeout / SECONDS) + " sec\n" +
        "expireNeighborsDelayAfterJoin = " + (int)(expireNeighborsDelayAfterJoin / SECONDS) + " sec\n" +
        "expireNeighborsInterval = " + (int)(expireNeighborsInterval / MINUTES) + " min\n" +
        "neighborTimeout = " + (int)(neighborTimeout / HOURS) + " hrs\n" +
        "syncDelayAfterJoin = " + (int)(syncDelayAfterJoin / SECONDS) + " sec\n" +
        "syncMinRemainingLifetime = " + (int)(syncMinRemainingLifetime / SECONDS) + " sec\n" +
        "syncMinQuietTime = " + (int)(syncMinQuietTime / SECONDS) + " sec\n" +
        "syncBloomFilter = " + syncBloomFilterNumHashes + " hashes, " + syncBloomFilterBitsPerKey + " bpk\n" +
        "syncPartnersPerTrial = " + syncPartnersPerTrial + "\n" +
        "syncInterval = " + (int)(syncInterval / MINUTES) + " min\n" +
        "syncMaxFragments = " + syncMaxFragments + "\n" +
        "fragmentRequestMaxAttempts = " + fragmentRequestMaxAttempts + "\n" +
        "fragmentRequestTimeoutDefault = " + (int)(fragmentRequestTimeoutDefault / SECONDS) + " sec\n" +
        "manifestRequestTimeout = " + (int)(manifestRequestTimeout / SECONDS) + " sec\n" +
        "manifestBurst = " + manifestRequestInitialBurst + " -> " + manifestRequestRetryBurst + "\n" +
        "manifestAggregationFactor = " + manifestAggregationFactor + "\n" +
        "overallRestoreTimeout = " + (int)(overallRestoreTimeout / SECONDS) + " sec\n" +
        "handoffDelayAfterJoin = " + (int)(handoffDelayAfterJoin / SECONDS) + " sec\n" +
        "handoffInterval = " + (int)(handoffInterval / SECONDS) + " sec\n" +
        "handoffMaxFragments = " + handoffMaxFragments + "\n" +
        "garbageCollectionInterval = " + (int)(garbageCollectionInterval / MINUTES) + " min\n" +
        "garbageCollectionMaxFragmentsPerRun = " + garbageCollectionMaxFragmentsPerRun + "\n" +
        "localScanInterval = " + (int)(localScanInterval / MINUTES) + " min\n" +
        "localScanMaxFragmentsPerRun = " + localScanMaxFragmentsPerRun + "\n" +
        "restoreMaxRequestFactor = " + restoreMaxRequestFactor + "\n" +
        "restoreMaxBoosts = " + restoreMaxBoosts + "\n" +
        "rateLimitedCheckInterval = " + (int)(rateLimitedCheckInterval / SECONDS) + " sec\n" +
        "rateLimitedRequestsPerSecond = " + rateLimitedRequestsPerSecond + "\n";
    }    

    if (cmd.startsWith("flush") && faultInjectionEnabled) {
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

    if (cmd.startsWith("refresh")) {
      String args = cmd.substring(8);
      String expirationArg = args.substring(args.lastIndexOf(' ') + 1);
      String keyArg = args.substring(0, args.lastIndexOf(' '));

      Id id = factory.buildIdFromToString(keyArg);
      long expiration = environment.getTimeSource().currentTimeMillis() + Long.parseLong(expirationArg);

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

    if (cmd.startsWith("neighbors")) {
      final Iterator iter = neighborStorage.scan().getIterator();
      final StringBuffer result = new StringBuffer();
      final long now = (cmd.indexOf("-r") < 0) ? 0 : environment.getTimeSource().currentTimeMillis();
      final String[] ret = new String[] { null };

      result.append(neighborStorage.scan().numElements()+ " neighbor(s)\n");

      Continuation c = new Continuation() {
        Id currentLookup;
        public void receiveResult(Object o) {
          if (o != null)
            result.append(currentLookup.toStringFull() + " " + (((Long)o).longValue() - now) + "\n");
          
          if (iter.hasNext()) {
            currentLookup = (Id) iter.next();
            neighborStorage.getObject(currentLookup, this);
          } else {
            ret[0] = "OK";
          }
        }
        public void receiveException(Exception e) {
          ret[0] = "Exception: "+e;
        }
      };
      
      c.receiveResult(null);
      while (ret[0] == null)
        Thread.currentThread().yield();

      result.append(ret[0]+"\n");              
      return result.toString();
    }
    
    if (cmd.startsWith("status")) {
      String result = "";
      result = result + "Responsible for: "+responsibleRange + "\n";
      result = result + "Local time: "+(new Date()) + "\n\n";
      result = result + fragmentStorage.scan().numElements() + " fragments\n";
      result = result + neighborStorage.scan().numElements() + " neighbors\n";
      result = result + continuations.size() + " active continuations\n";
      result = result + pendingTraffic.size() + " pending requests\n";
//      if (trashStorage != null) 
//        result = result + trashStorage.scan().numElements() + " fragments in trash\n";

      return result;
    }

    if (cmd.startsWith("insert") && faultInjectionEnabled) {
      String args = cmd.substring(7);
      String expirationArg = args.substring(args.lastIndexOf(' ') + 1);
      String numObjectsArg = args.substring(0, args.lastIndexOf(' '));

      final int numObjects = Integer.parseInt(numObjectsArg);
      final int lifetime = Integer.parseInt(expirationArg);
      String result = "";
      
      for (int i=0; i<numObjects; i++) {
        final Id randomID = factory.buildRandomId(environment.getRandomSource());
        result = result + randomID.toStringFull() + "\n";
        pendingTraffic.put(new VersionKey(randomID, 0), new Continuation.SimpleContinuation() {
          public void receiveResult(Object o) {
            insert(
              new DebugContent(randomID, false, 0, new byte[] {}),
              environment.getTimeSource().currentTimeMillis() + lifetime,
              new Continuation() {
                public void receiveResult(Object o) {
                }
                public void receiveException(Exception e) {
                }
              });
          }
        });
      }
      
      return result + numObjects + " object(s) with lifetime "+lifetime+"ms created\n";
    }

    if (cmd.startsWith("delete") && faultInjectionEnabled) {
      String[] vkeyS = cmd.substring(7).split("[v#]");
      Id key = factory.buildIdFromToString(vkeyS[0]);
      long version = Long.parseLong(vkeyS[1]);
      VersionKey vkey = new VersionKey(key, version);
      FragmentKey id = new FragmentKey(vkey, Integer.parseInt(vkeyS[2]));

      final String[] ret = new String[] { null };
      fragmentStorage.unstore(id, new Continuation() {
        public void receiveResult(Object o) {
          ret[0] = "result("+o+")";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
      
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return "delete("+id+")="+ret[0];
    }

    if (cmd.startsWith("burst") && faultInjectionEnabled) {
      String[] vkeyS = cmd.substring(6).split("[v#]");
      Id key = factory.buildIdFromToString(vkeyS[0]);
      long version = Long.parseLong(vkeyS[1]);
      VersionKey vkey = new VersionKey(key, version);
      final FragmentKey id = new FragmentKey(vkey, Integer.parseInt(vkeyS[2]));
      final Id fragmentLoc = getFragmentLocation(id);

      final String[] ret = new String[] { "" };
      final Boolean[] done = new Boolean[] { null };
      final long now = environment.getTimeSource().currentTimeMillis();
      addContinuation(new GlacierContinuation() {
        int receivedSoFar = 0;
        final int total = 100;
        public String toString() {
          return "Burst continuation";
        }
        public void init() {
          for (int i=0; i<total; i++) {
            sendMessage(
              fragmentLoc,
              new GlacierQueryMessage(getMyUID(), new FragmentKey[] { id }, getLocalNodeHandle(), fragmentLoc, tagDebug),
              null
            );
          }
        }
        public void receiveResult(Object o) {
          if (o instanceof GlacierResponseMessage) {
            ret[0] = ret[0] + (environment.getTimeSource().currentTimeMillis() - now) + " msec ("+((GlacierResponseMessage)o).getSource().getId()+")\n";
            if ((++receivedSoFar) == total)
              timeoutExpired();
          }
        }
        public void receiveException(Exception e) {
        }
        public void timeoutExpired() {        
          done[0] = new Boolean(true);
          terminate();
        }
        public long getTimeout() {
          return now + 120 * SECONDS;
        }
      });
        
      while (done[0] == null)
        Thread.currentThread().yield();
      
      return "burst("+id+")="+ret[0];
    }

    if (cmd.startsWith("manifest")) {
      String[] vkeyS = cmd.substring(9).split("v");
      Id key = factory.buildIdFromToString(vkeyS[0]);
      long version = Long.parseLong(vkeyS[1]);
      VersionKey vkey = new VersionKey(key, version);

      final String[] ret = new String[] { null };
      retrieveManifest(vkey, tagDebug, new Continuation() {
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

    if (cmd.startsWith("retrieve")) {
      String[] vkeyS = cmd.substring(9).split("[v#]");
      Id key = factory.buildIdFromToString(vkeyS[0]);
      long version = Long.parseLong(vkeyS[1]);
      VersionKey vkey = new VersionKey(key, version);
      final FragmentKey id = new FragmentKey(vkey, Integer.parseInt(vkeyS[2]));
      final FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(id);

      final String[] ret = new String[] { null };
      fragmentStorage.getObject(id, new Continuation() {
        public void receiveResult(Object o) {
          FragmentAndManifest fam = (FragmentAndManifest) o;
          MessageDigest md = null;
          try {
            md = MessageDigest.getInstance("SHA");
          } catch (NoSuchAlgorithmException e) {
          }

          md.reset();
          md.update(fam.fragment.getPayload());

          ret[0] = "OK\n\nFragment: "+fam.fragment.getPayload().length+" bytes, Hash=["+dump(md.digest(), false)+"], ID="+id.getFragmentID()+"\n\nValidation: " +
                   (fam.manifest.validatesFragment(fam.fragment, id.getFragmentID()) ? "OK" : "FAIL") + "\n\n" + 
                   fam.manifest.toStringFull()+"\n\nMetadata:\n - Stored since: "+metadata.getStoredSince()+
                   "\n - Current expiration: "+metadata.getCurrentExpiration()+"\n - Previous expiration: "+metadata.getPreviousExpiration()+"\n";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
      
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return "retrieve("+id+")="+ret[0];
    }

    if (cmd.startsWith("validate")) {
      FragmentKeySet keyset = (FragmentKeySet) fragmentStorage.scan();
      final Iterator iter = keyset.getIterator();
      final StringBuffer result = new StringBuffer();
  
      result.append(keyset.numElements()+ " fragment(s)\n");

      final String[] ret = new String[] { null };
      if (iter.hasNext()) {
        final FragmentKey thisKey = (FragmentKey) iter.next();
        fragmentStorage.getObject(thisKey, new Continuation() {
          FragmentKey currentKey = thisKey;
          int totalChecks = 1, totalFailures = 0;
          public void receiveResult(Object o) {
            FragmentAndManifest fam = (FragmentAndManifest) o;
            boolean success = fam.manifest.validatesFragment(fam.fragment, currentKey.getFragmentID());
            if (!success)
              totalFailures ++;
            result.append(currentKey.toStringFull()+" "+ (success ? "OK" : "FAIL") + "\n");
            advance();
          }
          public void receiveException(Exception e) {
            totalFailures ++;
            result.append(currentKey.toStringFull()+" EXC: "+e+"\n");
            advance();
          }
          public void advance() {
            if (iter.hasNext()) {
              currentKey = (FragmentKey) iter.next();
              totalChecks ++;
              fragmentStorage.getObject(currentKey, this);
            } else {
              if (totalFailures == 0)
                ret[0] = "OK ("+totalChecks+" fragments checked)";
              else
                ret[0] = "FAIL, "+totalFailures+"/"+totalChecks+" fragments damaged"; 
            }
          }
        });
        
        while (ret[0] == null)
          Thread.currentThread().yield();
      
        return "validate="+ret[0]+"\n\n"+result.toString();
      }

      return "validate: no objects\n\n" + result.toString();
    }

    if (cmd.startsWith("fetch")) {
      String[] vkeyS = cmd.substring(6).split("[v#]");
      Id key = factory.buildIdFromToString(vkeyS[0]);
      long version = Long.parseLong(vkeyS[1]);
      VersionKey vkey = new VersionKey(key, version);
      final FragmentKey id = new FragmentKey(vkey, Integer.parseInt(vkeyS[2]));
      final long now = environment.getTimeSource().currentTimeMillis();
      final Id fragmentLoc = getFragmentLocation(id);

      final String[] ret = new String[] { null };
      addContinuation(new GlacierContinuation() {
        public String toString() {
          return "DebugFetch continuation";
        }
        public void init() {
          sendMessage(
            fragmentLoc,
            new GlacierFetchMessage(getMyUID(), id, GlacierFetchMessage.FETCH_FRAGMENT_AND_MANIFEST, getLocalNodeHandle(), fragmentLoc, tagDebug),
            null
          );
        }
        public void receiveResult(Object o) {
          if (o instanceof GlacierDataMessage) {
            GlacierDataMessage gdm = (GlacierDataMessage) o;
            MessageDigest md = null;
            try {
              md = MessageDigest.getInstance("SHA");
            } catch (NoSuchAlgorithmException e) {
            }

            md.reset();
            md.update(gdm.getFragment(0).getPayload());

            ret[0] = "\n\nResponse: "+gdm.getKey(0).toStringFull()+" ("+gdm.numKeys()+" keys)\n" +  "Holder: "+gdm.getSource()+"\n" +
                     "Fragment: "+gdm.getFragment(0).getPayload().length+" bytes, Hash=["+dump(md.digest(), false)+"]\n\nValidation: " +
                     (gdm.getManifest(0).validatesFragment(gdm.getFragment(0), gdm.getKey(0).getFragmentID()) ? "OK" : "FAIL") + "\n\n" + 
                     gdm.getManifest(0).toStringFull();
                     
            terminate();
          } else {
            ret[0] = "Received "+o;
            terminate();
          }
        }
        public void receiveException(Exception e) {
          ret[0] = "Exception="+e;
          terminate();
        }
        public void timeoutExpired() {        
          ret[0] = "Timeout";
          terminate();
        }
        public long getTimeout() {
          return now + 5 * SECONDS;
        }
      });
        
      while ((ret[0] == null) && (environment.getTimeSource().currentTimeMillis() < (now + 5*SECONDS)))
        Thread.currentThread().yield();
      
      if (ret[0] == null)
        ret[0] = "Timeout";
      
      return "fetch("+id+"@"+fragmentLoc+")="+ret[0];
    }

    return null;
  }

  public void insert(final PastContent obj, final Continuation command) {
    insert(obj, GCPast.INFINITY_EXPIRATION, command);
  }

  public void refresh(Id[] ids, long[] expirations, Continuation command) {
    long[] versions = new long[ids.length];
    Arrays.fill(versions, 0);
    refresh(ids, versions, expirations, command);
  }

  public void refresh(Id[] ids, long expiration, Continuation command) {
    long[] expirations = new long[ids.length];
    Arrays.fill(expirations, expiration);
    refresh(ids, expirations, command);
  }
  
  public void refresh(final Id[] ids, final long[] versions, final long[] expirations, final Continuation command) {
    if (!enableBulkRefresh) {
      /* Ordinary refresh method (safe in 'hostile' environments) */

      final Continuation.MultiContinuation mc = new Continuation.MultiContinuation(command, ids.length);
      for (int i=0; i<ids.length; i++) {
        final Continuation thisContinuation = mc.getSubContinuation(i);
        final Id thisId = ids[i];
        final long thisVersion = versions[i];
        final long thisExpiration = expirations[i];
      
        log(Logger.INFO, "refresh("+thisId.toStringFull()+"v"+thisVersion+", exp="+thisExpiration+")");

        final VersionKey thisVersionKey = new VersionKey(thisId, thisVersion);
        Continuation prev = (Continuation) pendingTraffic.put(thisVersionKey, new Continuation.SimpleContinuation() {
          public void receiveResult(Object o) {
            retrieveManifest(thisVersionKey, tagRefresh, new Continuation() {
              public void receiveResult(Object o) {
                if (o instanceof Manifest) {
                  Manifest manifest = (Manifest) o;

                  log(Logger.FINE, "refresh("+thisId.toStringFull()+"v"+thisVersion+"): Got manifest");
                  manifest = policy.updateManifest(new VersionKey(thisId, thisVersion), manifest, thisExpiration);
                  Manifest[] manifests = new Manifest[numFragments];
                  for (int i=0; i<numFragments; i++)
                    manifests[i] = manifest;
                  distribute(new VersionKey(thisId, thisVersion), null, manifests, thisExpiration, tagRefresh, thisContinuation);
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
        });
      
        if (prev != null)
          prev.receiveException(new GlacierException("Key collision in traffic shaper (refresh)"));
      }
    } else {
      /* Aggregated refresh method */
      
      addContinuation(new GlacierContinuation() {
        int minAcceptable = (int)(numSurvivors * minFragmentsAfterInsert);
        FragmentKey[][] fragmentKey;
        VersionKey[] versionKey;
        Id[][] fragmentLocation;
        NodeHandle[][] fragmentHolder;
        boolean[][] fragmentChecked;
        Vector holders;
        Manifest manifests[];
        int successes[];
        boolean answered;
        long nextTimeout;
        int currentStage;
        int retriesRemaining;
        final int stageProbing = 1;
        final int stageFetchingManifests = 2;
        final int stagePatching = 3;
      
        public String toString() {
          return "AggregateRefresh continuation ("+fragmentKey.length+" fragments)";
        }
        public void init() {
          log(Logger.INFO, "Initializing AggregateRefresh continuation");
        
          fragmentKey = new FragmentKey[ids.length][numFragments];
          fragmentLocation = new Id[ids.length][numFragments];
          fragmentHolder = new NodeHandle[ids.length][numFragments];
          fragmentChecked = new boolean[ids.length][numFragments];
          manifests = new Manifest[ids.length];
          versionKey = new VersionKey[ids.length];
          successes = new int[ids.length];
          nextTimeout = environment.getTimeSource().currentTimeMillis() + bulkRefreshProbeInterval;
          currentStage = stageProbing;
          holders = new Vector();
          retriesRemaining = (int)(bulkRefreshMaxProbeFactor * numFragments);
          answered = false;
        
          boolean haveFragmentMyself = false;
          for (int i=0; i<ids.length; i++) {
            manifests[i] = null;
            versionKey[i] = new VersionKey(ids[i], versions[i]);
            for (int j=0; j<numFragments; j++) {
              fragmentKey[i][j] = new FragmentKey(new VersionKey(ids[i], versions[i]), j);
              fragmentLocation[i][j] = getFragmentLocation(fragmentKey[i][j]);
              fragmentChecked[i][j] = false;
              if (fragmentStorage.getMetadata(fragmentKey[i][j]) != null) {
                haveFragmentMyself = true;
                fragmentHolder[i][j] = getLocalNodeHandle();
              } else {
                fragmentHolder[i][j] = null;
              }
            }
          }

          if (haveFragmentMyself)
            holders.add(getLocalNodeHandle());

          Arrays.fill(successes, 0);
        
          log(Logger.FINE, "AR Initialization completed, "+fragmentKey.length+" candidate objects. Triggering first probe...");
          timeoutExpired();
        }
        public void receiveResult(Object o) {
          if (o instanceof GlacierRefreshResponseMessage) {
            GlacierRefreshResponseMessage grrm = (GlacierRefreshResponseMessage) o;
            IdRange thisRange = grrm.getRange();
            NodeHandle holder = grrm.isOnline() ? grrm.getSource() : null;
            
            log(Logger.FINE, "AR got refresh response: range "+thisRange+", online="+grrm.isOnline());
            if (thisRange != null) {
              for (int i=0; i<ids.length; i++) {
                for (int j=0; j<numFragments; j++) {
                  if (thisRange.containsId(fragmentLocation[i][j])) {
                    fragmentChecked[i][j] = true;
                    fragmentHolder[i][j] = holder;
                  }
                }
              }
            }
            
            if (!holders.contains(holder))
              holders.add(holder);
          } else if (o instanceof GlacierDataMessage) {
            GlacierDataMessage gdm = (GlacierDataMessage) o;
            
            log(Logger.FINE, "AR Received data message with "+gdm.numKeys()+" keys");
            for (int i=0; i<gdm.numKeys(); i++) {
              if ((gdm.getManifest(i) != null) && (gdm.getKey(i) != null)) {
                Manifest thisManifest = gdm.getManifest(i);
                FragmentKey thisKey = gdm.getKey(i);
                
                log(Logger.FINE, "AR Received manifest for "+gdm.getKey(i)+", checking signature...");
                if (policy.checkSignature(thisManifest, thisKey.getVersionKey())) {
                  log(Logger.FINE, "AR Signature OK");
                  for (int j=0; j<ids.length; j++) {
                    if ((manifests[j] == null) && (versionKey[j].equals(thisKey.getVersionKey()))) {
                      manifests[j] = thisManifest;
                      log(Logger.FINE, "AR Storing under #"+j);
                    }
                  }
                } else {
                  warn("AR Invalid signature");
                }
              }
            }
          } else if (o instanceof GlacierRefreshCompleteMessage) {
            GlacierRefreshCompleteMessage grcm = (GlacierRefreshCompleteMessage) o;
            log(Logger.FINE, "AR Refresh completion reported by "+grcm.getSource());

            for (int i=0; i<grcm.numKeys(); i++) {
              log(Logger.FINE, "AR Refresh completion: Key "+grcm.getKey(i)+", "+grcm.getUpdates(i)+" update(s)");
              
              int index = -1;
              for (int j=0; j<ids.length; j++) {
                if (grcm.getKey(i).equals(versionKey[j]))
                  index = j;
              }
              
              if (index >= 0) {
                int maxSuccesses = 0;
                for (int j=0; j<numFragments; j++) {
                  if (!fragmentChecked[index][j] && (fragmentHolder[index][j] != null) && (fragmentHolder[index][j].equals(grcm.getSource()))) {
                    maxSuccesses ++;
                    fragmentChecked[index][j] = true;
                  }
                }
                    
                if (grcm.getUpdates(i) > maxSuccesses) {
                  warn("Node "+grcm.getSource()+" reports "+grcm.getUpdates(i)+" for "+grcm.getKey(i)+", but is responsible for only "+maxSuccesses+" fragments -- duplicate message, or under attack?");
                  successes[index] += maxSuccesses;
                } else {
                  successes[index] += grcm.getUpdates(i);
                }
              } else {
                warn("Node "+grcm.getSource()+" reports completion for "+grcm.getKey(i)+", but no refresh request matches?!?");
              }
            }

            if (!answered) {
              boolean allSuccessful = true;
              for (int i=0; i<successes.length; i++)
                if (successes[i] < minAcceptable)
                  allSuccessful = false;
                
              if (allSuccessful) {
                log(Logger.FINE, "AR Reporing success");
              
                Object[] result = new Object[ids.length];
                for (int i=0; i<ids.length; i++)
                  result[i] = new Boolean(true);
            
                answered = true;
                command.receiveResult(result);
              }
            }
          } else {
            warn("Unexpected result in AR continuation: "+o+" -- discarded");
          }
        }
        public void receiveException(Exception e) {
          warn("Exception during AggregateRefresh: "+e);
          e.printStackTrace();
          terminate();
          
          if (!answered) {
            Object[] result = new Object[ids.length];
            Exception ee = new GlacierException("Exception during refresh: "+e);

            for (int i=0; i<ids.length; i++)
              result[i] = ee;
            
            answered = true;
            command.receiveResult(result);
          }
        }
        public void timeoutExpired() {
          if (currentStage == stageProbing) {
            nextTimeout = environment.getTimeSource().currentTimeMillis() + bulkRefreshProbeInterval;
            
            int nextProbe = environment.getRandomSource().nextInt(ids.length);
            int nextFID = environment.getRandomSource().nextInt(numFragments);
            int maxSteps = ids.length * numFragments;
            while ((maxSteps > 0) && fragmentChecked[nextProbe][nextFID]) {
              nextFID ++;
              if (nextFID >= numFragments) {
                nextFID = 0;
                nextProbe = (nextProbe + 1) % ids.length;
              }
              
              maxSteps --;
            }
            
            if (!fragmentChecked[nextProbe][nextFID] && (retriesRemaining > 0)) {
              log(Logger.FINE, "AR Sending a probe to "+fragmentKey[nextProbe][nextFID]+" at "+fragmentLocation[nextProbe][nextFID]+" ("+retriesRemaining+" probes left)");
              fragmentChecked[nextProbe][nextFID] = true;
              retriesRemaining --;
              sendMessage(
                fragmentLocation[nextProbe][nextFID],
                new GlacierRefreshProbeMessage(getMyUID(), fragmentLocation[nextProbe][nextFID], getLocalNodeHandle(), fragmentLocation[nextProbe][nextFID], tagRefresh),
                null
              );
            } else {
              currentStage = stageFetchingManifests;
              retriesRemaining = 3;
            }
          }
          
          if (currentStage == stageFetchingManifests) {
            nextTimeout = environment.getTimeSource().currentTimeMillis() + bulkRefreshManifestInterval;

            boolean[] objectCovered = new boolean[ids.length];
            boolean allObjectsCovered = true;
            for (int i=0; i<ids.length; i++) {
              objectCovered[i] = (manifests[i] != null);
              allObjectsCovered &= objectCovered[i];
            }
            
            if (!allObjectsCovered && ((retriesRemaining--) > 0)) {
              log(Logger.FINE, "AR Fetching manifests, "+retriesRemaining+" attempts remaining");
              while (true) {
                int idx = environment.getRandomSource().nextInt(ids.length);
                int maxSteps = ids.length + 2;
                while (objectCovered[idx] && ((--maxSteps)>0))
                  idx = (idx+1) % ids.length;
                if (maxSteps <= 0)
                  break;
                
                int fid = environment.getRandomSource().nextInt(numFragments);
                maxSteps = numFragments + 2;
                while ((fragmentHolder[idx][fid] == null) && ((--maxSteps)>0))
                  fid = (fid+1) % numFragments;

                if (fragmentHolder[idx][fid] != null) {
                  NodeHandle thisHolder = fragmentHolder[idx][fid];
                  Vector idsToQuery = new Vector();
                  for (int i=0; i<ids.length; i++) {
                    if (!objectCovered[i]) {
                      for (int j=0; j<numFragments; j++) {
                        if ((fragmentHolder[i][j] != null) && (fragmentHolder[i][j].equals(thisHolder))) {
                          idsToQuery.add(fragmentKey[i][j]);
                          objectCovered[i] = true;
                          break;
                        }
                      }
                    }
                  }
                  
                  log(Logger.FINE, "AR Asking "+thisHolder+" for "+idsToQuery.size()+" manifests");
                  for (int i=0; i<idsToQuery.size(); i+= bulkRefreshManifestAggregationFactor) {
                    int idsHere = Math.min(idsToQuery.size() - i, bulkRefreshManifestAggregationFactor);
                    FragmentKey[] keys = new FragmentKey[idsHere];
                    for (int j=0; j<idsHere; j++)
                      keys[j] = (FragmentKey) idsToQuery.elementAt(i+j);

                    log(Logger.FINE, "AR Sending a manifest fetch with "+idsHere+" IDs, starting at "+keys[0]);                    
                    sendMessage(
                      null,
                      new GlacierFetchMessage(getMyUID(), keys, GlacierFetchMessage.FETCH_MANIFEST, getLocalNodeHandle(), thisHolder.getId(), tagRefresh),
                      thisHolder
                    );
                  }
                } else {
                  objectCovered[idx] = true;
                }
              }
                
              log(Logger.FINE, "AR Manifest fetches sent; awaiting responses...");
                
            } else {
              currentStage = stagePatching;
              retriesRemaining = bulkRefreshPatchRetries;
              
              log(Logger.FINE, "AR Patching manifests...");
              for (int i=0; i<ids.length; i++)
                if (manifests[i] != null)
                  manifests[i] = policy.updateManifest(versionKey[i], manifests[i], expirations[i]);
                  
              log(Logger.FINE, "AR Done patching manifests");
              
              for (int i=0; i<ids.length; i++)
                for (int j=0; j<numFragments; j++)
                  fragmentChecked[i][j] = ((fragmentHolder[i][j] == null) || (manifests[i] == null));
            }
          }
          
          if (currentStage == stagePatching) {
            nextTimeout = environment.getTimeSource().currentTimeMillis() + bulkRefreshPatchInterval;
          
            if ((retriesRemaining--) > 0) {
              log(Logger.FINE, "AR Sending patches... ("+retriesRemaining+" retries left)");

              int totalPatchesSent = 0;
              for (int h=0; h<holders.size(); h++) {
                NodeHandle thisHolder = (NodeHandle) holders.elementAt(h);
                
                /* Find out which patches this holder should get */
                
                boolean[] sendPatchForObject = new boolean[ids.length];
                int numPatches = 0;
              
                for (int i=0; i<ids.length; i++) {
                  sendPatchForObject[i] = false;
                
                  for (int j=0; j<numFragments; j++)
                    if (!fragmentChecked[i][j] && fragmentHolder[i][j].equals(thisHolder))
                      sendPatchForObject[i] = true;
                
                  if (sendPatchForObject[i])
                    numPatches ++;
                }
              
                log(Logger.FINE, "AR Holder #"+h+" ("+thisHolder+") should get "+numPatches+" patches");
              
                /* Send the patches */
              
                int nextPatch = 0;
                for (int i=0; i<numPatches; i+=bulkRefreshPatchAggregationFactor) {
                  int patchesHere = Math.min(numPatches-i, bulkRefreshPatchAggregationFactor);

                  VersionKey[] keys = new VersionKey[patchesHere];
                  long[] lifetimes = new long[patchesHere];
                  byte[][] signatures = new byte[patchesHere][];
                
                  for (int j=0; j<patchesHere; j++) {
                    while (!sendPatchForObject[nextPatch])
                      nextPatch ++;
                  
                    keys[j] = versionKey[nextPatch];
                    lifetimes[j] = expirations[nextPatch];
                    signatures[j] = manifests[nextPatch].signature;
                    nextPatch ++;
                  }
                
                  log(Logger.FINE, "AR Sending a patch with "+patchesHere+" IDs, starting at "+keys[0]+", to "+thisHolder.getId());
                  totalPatchesSent += patchesHere;
                  
                  sendMessage(
                    null,
                    new GlacierRefreshPatchMessage(getMyUID(), keys, lifetimes, signatures, getLocalNodeHandle(), thisHolder.getId(), tagRefresh),
                    thisHolder
                  );
                }
              }
              
              if (totalPatchesSent == 0) {
                log(Logger.FINE, "AR No patches sent; refresh seems to be complete...");
                retriesRemaining = 0;
                timeoutExpired();
              }
            } else {
              log(Logger.FINE, "AR Giving up");
              terminate();

              Object[] result = new Object[ids.length];
              for (int i=0; i<ids.length; i++) {
                result[i] = (successes[i] >= minAcceptable) ? (Object)(new Boolean(true)) : (Object)(new GlacierException("Only "+successes[i]+" fragments of "+versionKey[i]+" refreshed successfully; need "+minAcceptable));
                log(Logger.FINE, " - AR Result for "+versionKey[i]+": " + ((result[i] instanceof Boolean) ? "OK" : "Failed") + " (with "+successes[i]+"/"+numFragments+" fragments, "+minAcceptable+" acceptable)");
              }
            
              answered = true;
              command.receiveResult(result);
            }
          }
        }
        public long getTimeout() {
          return nextTimeout;
        }
      });
    }
  }

  private void distribute(final VersionKey key, final Fragment[] fragments, final Manifest[] manifests, final long expiration, final char tag, final Continuation command) {
    final long tStart = environment.getTimeSource().currentTimeMillis();
    addContinuation(new GlacierContinuation() {
      NodeHandle[] holder;
      boolean[] receiptReceived;
      boolean doInsert = (fragments != null);
      boolean doRefresh = !doInsert;
      boolean answered = false;
      boolean inhibitInsertions = true;
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
      private int numHoldersKnown() {
        int result = 0;
        for (int i=0; i<holder.length; i++)
          if (holder[i] != null)
            result ++;
        return result;
      }
      private String whoAmI() {
        return (doRefresh) ? "Refresh" : "Insert";
      }
      public void init() {
        log(Logger.INFO, "Initializing "+whoAmI()+" continuation for " + key);
        holder = new NodeHandle[numFragments];
        receiptReceived = new boolean[numFragments];

        /* Send queries */
        
        log(Logger.FINE, "Sending queries for " + key);
        for (int i = 0; i < numFragments; i++) {
          Id fragmentLoc = getFragmentLocation(key.getId(), i, key.getVersion());
          FragmentKey keys[] = new FragmentKey[1];
          keys[0] = new FragmentKey(key, i);
      
          log(Logger.FINE, "Query #"+i+" to "+fragmentLoc);
          sendMessage(
            fragmentLoc,
            new GlacierQueryMessage(getMyUID(), keys, getLocalNodeHandle(), fragmentLoc, tag),
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

          /* Sanity checks */

          int fragmentID = grm.getKey(0).getFragmentID();
          if (fragmentID < numFragments) {
            if (grm.getAuthoritative(0)) {

              /* OK, so the message makes sense. Let's see... */
            
              if (doInsert && !grm.getHaveIt(0)) {
              
                /* If this is an insertion, and the holder is telling us that he does not
                   have the fragment, we send it to him */
              
                if (holder[fragmentID] == null) {
                  holder[fragmentID] = grm.getSource();
                  if (!inhibitInsertions) {
                    log(Logger.FINE, "Got insert response, sending fragment "+grm.getKey(0));
                    sendMessage(
                      null,
                      new GlacierDataMessage(getMyUID(), grm.getKey(0), fragments[fragmentID], manifests[fragmentID], getLocalNodeHandle(), grm.getSource().getId(), false, tag),
                      grm.getSource()
                    );
                  } else {
                    if (numHoldersKnown() >= minAcceptable) {
                      log(Logger.FINE, "Got "+numHoldersKnown()+" insert responses, sending fragments...");
                      inhibitInsertions = false;
                      for (int i=0; i<holder.length; i++) {
                        if (holder[i] != null) {
                          log(Logger.FINE, "Sending fragment #"+i);
                          sendMessage(
                            null,
                            new GlacierDataMessage(getMyUID(), new FragmentKey(key, i), fragments[i], manifests[i], getLocalNodeHandle(), holder[i].getId(), false, tag),
                            holder[i]
                          );
                        }
                      }
                      
                      log(Logger.FINE, "Done sending fragments, now accepting further responses");
                    } else {
                      log(Logger.FINE, "Got insert response #"+numHoldersKnown()+" ("+minAcceptable+" needed to start insertion)");
                    }
                  }
                } else {
                  warn("Received two insert responses for the same fragment -- discarded");
                }
                
              } else if (grm.getHaveIt(0) && (grm.getExpiration(0) < expiration)) {
              
                /* If the holder has an old version of the fragment, we send the manifest only. */
              
                if (holder[fragmentID] == null) {
                  holder[fragmentID] = grm.getSource();
                  log(Logger.FINE, "Got refresh response (exp="+grm.getExpiration(0)+"<"+expiration+"), sending manifest "+grm.getKey(0));
                  sendMessage(
                    null,
                    new GlacierDataMessage(getMyUID(), grm.getKey(0), null, manifests[fragmentID], getLocalNodeHandle(), grm.getSource().getId(), false, tag),
                    grm.getSource()
                  );

                  /* Refreshes are not acknowledged */

                  if (doRefresh) {
                    receiptReceived[fragmentID] = true;
                    if ((numReceiptsReceived() >= minAcceptable) && !answered) {
                      answered = true;
                      reportSuccess();
                    }
                  }
                } else {
                  warn("Received two refresh responses for the same fragment -- discarded");
                }
              } else if (grm.getHaveIt(0) && (grm.getExpiration(0) >= expiration)) {
              
                /* If the holder has a current version of the fragment, we are happy */
              
                log(Logger.FINE, "Receipt received after "+whoAmI()+": "+grm.getKey(0));
                receiptReceived[fragmentID] = true;
                if ((numReceiptsReceived() >= minAcceptable) && !answered) {
                  answered = true;
                  reportSuccess();
                }
              }
            } else {
              log(Logger.FINE, whoAmI() + " response, but not authoritative -- ignoring");
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
        log(Logger.FINE, "Reporting success for "+key+", "+numReceiptsReceived()+"/"+numFragments+" receipts received so far");
        if (doInsert)
          command.receiveResult(new Boolean[] { new Boolean(true) });
        else
          command.receiveResult(new Boolean(true));
      }      
      public void timeoutExpired() {        
        if (numReceiptsReceived() >= minAcceptable) {
          log(Logger.INFO, whoAmI()+" of "+key+" successful, "+numReceiptsReceived()+"/"+numFragments+" receipts received");
          if (!answered) {
            answered = true;
            reportSuccess();
          }
        } else {
          warn(whoAmI()+" "+key+" failed, only "+numReceiptsReceived()+"/"+numFragments+" receipts received");
          if (!answered) {
            answered = true;
            command.receiveException(new GlacierException(whoAmI()+" failed, did not receive enough receipts"));
          }
        }

        terminate();
      }
      public long getTimeout() {
        return tStart + ((doRefresh) ? refreshTimeout : insertTimeout);
      }
    });
  }  

  public void insert(final PastContent obj, final long expiration, final Continuation command) {
    long theVersion = (obj instanceof GCPastContent) ? ((GCPastContent)obj).getVersion() : 0;
    final VersionKey vkey = new VersionKey(obj.getId(), theVersion);

    log(Logger.INFO, "insert(" + obj + " (id=" + vkey.toStringFull() + ", mutable=" + obj.isMutable() + ")");

    endpoint.process(new Executable() {
      public Object execute() {
        boolean[] generateFragment = new boolean[numFragments];
        Arrays.fill(generateFragment, true);
        return policy.encodeObject(obj, generateFragment);
      }
    }, new Continuation() {
      public void receiveResult(Object o) {
        final Fragment[] fragments = (Fragment[]) o;
        if (fragments == null) {
          command.receiveException(new GlacierException("Cannot encode object"));
          return;
        }

        log(Logger.FINE, "insert(" + vkey.toStringFull() + ") encoded fragments OK, creating manifests...");

        endpoint.process(new Executable() {
          public Object execute() {
            return policy.createManifests(vkey, obj, fragments, expiration);
          }
        }, new Continuation() {
          public void receiveResult(Object o) {
            if (o instanceof Manifest[]) {
              final Manifest[] manifests = (Manifest[]) o;
              if (manifests == null) {
                command.receiveException(new GlacierException("Cannot create manifests"));
                return;
              }

              distribute(vkey, fragments, manifests, expiration, tagInsert, command);
            } else {
              warn("insert(" + vkey.toStringFull() + ") cannot create manifests - returned o="+o);
              command.receiveException(new GlacierException("Cannot create manifests in insert()"));
            }
          }
          public void receiveException(Exception e) {
            warn("insert(" + vkey.toStringFull() + ") cannot create manifests - exception e="+e);
            e.printStackTrace();
            command.receiveException(e);
          }
        });
      }
      public void receiveException(Exception e) {
        command.receiveException(new GlacierException("EncodeObject failed: e="+e));
        e.printStackTrace();
      }
    });
  }

  private void timerExpired() {
    log(Logger.FINE, "Timer expired");

    boolean foundTerminated = false;
    long earliestTimeout;
    int numDelete;
        
    do {
      long now = environment.getTimeSource().currentTimeMillis();
      int[] deleteList = new int[100];
      numDelete = 0;
      earliestTimeout = -1;
        
      log(Logger.FINE, "Timer run at "+now);
          
      Enumeration enu = continuations.elements();
      while (enu.hasMoreElements()) {
        GlacierContinuation gc = (GlacierContinuation) enu.nextElement();
        long currentTimeout = gc.getTimeout();

        if (!gc.hasTerminated() && currentTimeout < (now + 1*SECONDS)) {
          log(Logger.FINE, "Timer: Resuming ["+gc+"]");
          gc.syncTimeoutExpired();
          if (!gc.hasTerminated() && (gc.getTimeout() <= currentTimeout))
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
        log(Logger.FINE, "Deleting "+numDelete+" expired continuations");
        for (int i=0; i<numDelete; i++)
          continuations.remove(new Integer(deleteList[i]));
      }
            
    } while ((numDelete == 100) || ((earliestTimeout >= 0) && (earliestTimeout < environment.getTimeSource().currentTimeMillis())));

    if (earliestTimeout >= 0) {
      log(Logger.FINE, "Next timeout is at "+earliestTimeout);
      setTimer((int)Math.max(earliestTimeout - environment.getTimeSource().currentTimeMillis(), 1*SECONDS));
    } else log(Logger.FINE, "No more timeouts");
  }

  public void neighborSeen(Id nodeId, long when) {

    if (nodeId.equals(getLocalNodeHandle().getId()))
      return;

    log(Logger.FINE, "Neighbor "+nodeId+" was seen at "+when);

    if (when > environment.getTimeSource().currentTimeMillis()) {
      warn("Neighbor: "+when+" is in the future (now="+environment.getTimeSource().currentTimeMillis()+")");
      when = environment.getTimeSource().currentTimeMillis();
    }

    final Id fNodeId = nodeId;
    final long fWhen = when;

    neighborStorage.getObject(nodeId, 
      new Continuation() {
        public void receiveResult(Object o) {
          log(Logger.FINE, "Continue: neighborSeen ("+fNodeId+", "+fWhen+") after getObject");

          final long previousWhen = (o!=null) ? ((Long)o).longValue() : 0;
          log(Logger.FINE, "Neighbor: "+fNodeId+" previously seen at "+previousWhen);
          if (previousWhen >= fWhen) {
            log(Logger.FINE, "Neighbor: No update needed (new TS="+fWhen+")");
            return;
          }
          
          neighborStorage.store(fNodeId, null, new Long(fWhen),
            new Continuation() {
              public void receiveResult(Object o) {
                log(Logger.FINE, "Continue: neighborSeen ("+fNodeId+", "+fWhen+") after store");
                log(Logger.FINE, "Neighbor: Updated "+fNodeId+" from "+previousWhen+" to "+fWhen);
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
    log(Logger.INFO, "Leafset update: " + handle + " has " + (joined ? "joined" : "left"));

    if (!joined)
      return;

    neighborSeen(handle.getId(), environment.getTimeSource().currentTimeMillis());
  }

  public void lookupHandle(Id id, NodeHandle handle, Continuation command) {
    command.receiveException(new UnsupportedOperationException("LookupHandle() is not supported on Glacier"));
  }    
  
  public void lookupHandles(Id id, int num, Continuation command) {
    lookupHandles(id, 0, num, command);
  }

  public void lookupHandles(final Id id, final long version, int num, final Continuation command) {
    log(Logger.INFO, "lookupHandles("+id+"v"+version+", n="+num+")");
    
    retrieveManifest(new VersionKey(id, version), tagLookupHandles, new Continuation() {
      boolean haveAnswered = false;
      public void receiveResult(Object o) {
        if (haveAnswered) {
          log(Logger.FINE, "lookupHandles("+id+"): received manifest "+o+" but has already answered. Discarding...");
          return;
        }
        if (o instanceof Manifest) {
          log(Logger.FINE, "lookupHandles("+id+"): received manifest "+o+", returning handle...");
          haveAnswered = true;
          command.receiveResult(new PastContentHandle[] {
            new GlacierContentHandle(id, version, getLocalNodeHandle(), (Manifest) o)
          });
        } else {
          warn("lookupHandles("+id+"): Cannot retrieve manifest");
          haveAnswered = true;
          command.receiveResult(new PastContentHandle[] { null });
        }
      }
      public void receiveException(Exception e) {
        warn("lookupHandles("+id+"): Exception "+e);
        e.printStackTrace();
        haveAnswered = true;
        command.receiveException(e);
      }
    });
  }

  public void lookup(Id id, long version, Continuation command) {
    VersionKey vkey = new VersionKey(id, version);
    log(Logger.INFO, "lookup("+id+"v"+version+")");
    retrieveObject(vkey, null, true, tagLookup, command);
  }

  public void lookup(Id id, boolean cache, Continuation command) {
    lookup(id, 0, command);
  }

  public void lookup(Id id, Continuation command) {
    lookup(id, 0, command);
  }

  public void fetch(PastContentHandle handle, Continuation command) {
    log(Logger.INFO, "fetch("+handle.getId()+")");
    
    if (!(handle instanceof GlacierContentHandle)) {
      command.receiveException(new GlacierException("Unknown handle type"));
      return;
    }

    GlacierContentHandle gch = (GlacierContentHandle) handle;
    log(Logger.FINE, "exact: fetch("+gch.getId()+"v"+gch.getVersion()+")");
    
    retrieveObject(new VersionKey(gch.getId(), gch.getVersion()), gch.getManifest(), true, tagFetch, command);
  }

  public void retrieveManifest(final VersionKey key, final char tag, final Continuation command) {
    log(Logger.FINE, "retrieveManifest(key="+key+" tag="+tag+")");
    addContinuation(new GlacierContinuation() {
      protected boolean checkedFragment[];
      protected long timeout;
      
      public String toString() {
        return "retrieveManifest("+key+")";
      }
      public void init() {
        checkedFragment = new boolean[numFragments];
        Arrays.fill(checkedFragment, false);
        timeout = environment.getTimeSource().currentTimeMillis() + manifestRequestTimeout;
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
      
        int nextID;
        do {
          nextID = environment.getRandomSource().nextInt(numFragments);
        } while (checkedFragment[nextID]);
     
        checkedFragment[nextID] = true;
        FragmentKey nextKey = new FragmentKey(key, nextID);
        Id nextLocation = getFragmentLocation(nextKey);
        log(Logger.FINE, "retrieveManifest: Asking "+nextLocation+" for "+nextKey);
        sendMessage(
          nextLocation,
          new GlacierFetchMessage(getMyUID(), nextKey, GlacierFetchMessage.FETCH_MANIFEST, getLocalNodeHandle(), nextLocation, tag),
          null
        );
      }
      public void receiveResult(Object o) {
        if (o instanceof GlacierDataMessage) {
          GlacierDataMessage gdm = (GlacierDataMessage) o;
          
          if ((gdm.numKeys() > 0) && (gdm.getManifest(0) != null)) {
            log(Logger.FINE, "retrieveManifest("+key+") received manifest");
            if (policy.checkSignature(gdm.getManifest(0), key)) {
              command.receiveResult(gdm.getManifest(0));
              terminate();
            } else {
              warn("retrieveManifest("+key+"): invalid signature in "+gdm.getKey(0));
            }
          } else warn("retrieveManifest("+key+") retrieved GDM without a manifest?!?");
        } else if (o instanceof GlacierResponseMessage) {
          log(Logger.FINE, "retrieveManifest("+key+"): Fragment not available:" + ((GlacierResponseMessage)o).getKey(0));
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
        log(Logger.FINE, "retrieveManifest("+key+"): Timeout ("+numCheckedFragments()+" fragments checked)");
        if (numCheckedFragments() < numFragments) {
          log(Logger.FINE, "retrying...");
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
        
  public void retrieveObject(final VersionKey key, final Manifest manifest, final boolean beStrict, final char tag, final Continuation c) {
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
        synchronized (numActiveRestores) {
          numActiveRestores[0] ++;
        }

        checkedFragment = new boolean[numFragments];
        haveFragment = new Fragment[numFragments];
        for (int i = 0; i < numFragments; i++) {
          checkedFragment[i] = false;
          haveFragment[i] = null;
        }
        timeout = environment.getTimeSource().currentTimeMillis();
        attemptsLeft = restoreMaxBoosts;
        timeoutExpired();
      }
      private void localTerminate() {
        synchronized (numActiveRestores) {
          numActiveRestores[0] --;
        }

        terminate();
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
            log(Logger.FINE, "Fragment "+((GlacierDataMessage)o).getKey(0)+" not available (GDM returned null), sending another request");
            if (numCheckedFragments() < numFragments)
              sendRandomRequest();
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
            
          log(Logger.FINE, "retrieveObject: Received fragment #"+fragmentID+" for "+gdm.getKey(0));
          haveFragment[fragmentID] = thisFragment;
          
          currentFragmentRequestTimeout -= fragmentRequestTimeoutDecrement;
          if (currentFragmentRequestTimeout < fragmentRequestTimeoutMin)
            currentFragmentRequestTimeout = fragmentRequestTimeoutMin;
          log(Logger.FINE, "Timeout decreased to "+currentFragmentRequestTimeout);
          
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

            log(Logger.FINE, "Decode object: " + key);
            Serializable theObject = policy.decodeObject(material);
            log(Logger.FINE, "Decode complete: " + key);

            if ((theObject == null) || !(theObject instanceof PastContent)) {
              warn("retrieveObject: Decoder delivered "+theObject+", unexpected -- failed");
              c.receiveException(new GlacierException("Decoder delivered "+theObject+", unexpected -- failed"));
            } else {
              c.receiveResult(theObject);
            }

            localTerminate();
          }
        } else if (o instanceof GlacierResponseMessage) {
          log(Logger.FINE, "Fragment "+((GlacierResponseMessage)o).getKey(0)+" not available");
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
        localTerminate();
      }
      public void sendRandomRequest() {
        int nextID;

        do {
          nextID = environment.getRandomSource().nextInt(numFragments);
        } while (checkedFragment[nextID]);
     
        checkedFragment[nextID] = true;
        FragmentKey nextKey = new FragmentKey(key, nextID);
        Id nextLocation = getFragmentLocation(nextKey);
        log(Logger.FINE, "retrieveObject: Asking "+nextLocation+" for "+nextKey);
        sendMessage(
          nextLocation,
          new GlacierFetchMessage(getMyUID(), nextKey, GlacierFetchMessage.FETCH_FRAGMENT, getLocalNodeHandle(), nextLocation, tag),
          null
        );
      }
      public void timeoutExpired() {
        if (attemptsLeft > 0) {
          log(Logger.FINE, "retrieveObject: Retrying ("+attemptsLeft+" attempts left)");
          if (attemptsLeft < restoreMaxBoosts) {
            currentFragmentRequestTimeout *= 2;
            if (currentFragmentRequestTimeout > fragmentRequestTimeoutMax)
              currentFragmentRequestTimeout = fragmentRequestTimeoutMax;
            log(Logger.FINE, "Timeout increased to "+currentFragmentRequestTimeout);
          }
          
          timeout = timeout + currentFragmentRequestTimeout;
          attemptsLeft --;

          int numRequests = numSurvivors - numHaveFragments();
          if (attemptsLeft < (restoreMaxBoosts - 1))
            numRequests = Math.min(2*numRequests, numFragments - numCheckedFragments());
          if ((attemptsLeft == 0) && beStrict)
            numRequests = numFragments - numCheckedFragments();
            
          for (int i=0; (i<numRequests) && (numCheckedFragments() < numFragments); i++) {
            sendRandomRequest();
          }
        } else {
          log(Logger.INFO, "retrieveObject: Giving up on "+key+" ("+restoreMaxBoosts+" attempts, "+numCheckedFragments()+" checked, "+numHaveFragments()+" gotten)");
          c.receiveException(new GlacierNotEnoughFragmentsException("Maximum number of attempts ("+restoreMaxBoosts+") reached for key "+key, numCheckedFragments(), numHaveFragments()));
          localTerminate();
        }
      }
      public long getTimeout() {
        return timeout;
      }
    });
  }
          
  public void retrieveFragment(final FragmentKey key, final Manifest manifest, final char tag, final GlacierContinuation c) {
    final Continuation c2 = new Continuation() {
      public void receiveResult(Object o) {
        if (o != null) {
          if (o instanceof FragmentAndManifest) {
            Fragment thisFragment = ((FragmentAndManifest)o).fragment;
            if (manifest.validatesFragment(thisFragment, key.getFragmentID())) {
              log(Logger.FINE, "retrieveFragment: Found in trash: "+key.toStringFull());
              c.receiveResult(thisFragment);
              return;
            }
          
            warn("Fragment found in trash, but does not match manifest?!? -- fetching normally");
          } else {
            warn("Fragment "+key.toStringFull()+" found in trash, but object is not a FAM ("+o+")?!? -- ignoring");
          }
        }
        
        addContinuation(new GlacierContinuation() {
          protected int attemptsLeft;
          protected boolean inPhaseTwo;
          protected long timeout;
      
          public String toString() {
            return "retrieveFragment("+key+")";
          }
          public void init() {
            attemptsLeft = fragmentRequestMaxAttempts;
            timeout = environment.getTimeSource().currentTimeMillis();
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
                warn("retrieveFragment: DataMessage does not contain any fragments -- discarded");
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
              log(Logger.FINE, "retrieveFragment: Retrying ("+attemptsLeft+" attempts left)");
              timeout = timeout + currentFragmentRequestTimeout;
              attemptsLeft --;
              sendMessage(
                key.getVersionKey().getId(),
                new GlacierFetchMessage(getMyUID(), key, GlacierFetchMessage.FETCH_FRAGMENT, getLocalNodeHandle(), key.getVersionKey().getId(), tag),
                null
              );
            } else {
              timeout = timeout + 3 * restoreMaxBoosts * currentFragmentRequestTimeout;
              if (inPhaseTwo) {
                warn("retrieveFragment: Already in phase two");
              }
              inPhaseTwo = true;
          
              retrieveObject(key.getVersionKey(), manifest, false, tag, new Continuation() {
                public void receiveResult(Object o) {
                  if (o == null) {
                    warn("retrieveFragment: retrieveObject("+key.getVersionKey()+") failed, returns null");
                    c.receiveException(new GlacierException("Cannot restore either the object or the fragment -- try again later!"));
                    return;
                  }
              
                  final PastContent retrievedObject = (PastContent) o;
                  endpoint.process(new Executable() {
                    public Object execute() {
                      log(Logger.FINE, "Reencode object: " + key.getVersionKey());
                      boolean generateFragment[] = new boolean[numFragments];
                      Arrays.fill(generateFragment, false);
                      generateFragment[key.getFragmentID()] = true;
                      Object result = policy.encodeObject(retrievedObject, generateFragment);
                      log(Logger.FINE, "Reencode complete: " + key.getVersionKey());
                      return result;
                    }
                  }, new Continuation() {
                    public void receiveResult(Object o) {
                      Fragment[] frag = (Fragment[]) o;
                  
                      if (!manifest.validatesFragment(frag[key.getFragmentID()], key.getFragmentID())) {
                        warn("Reconstructed fragment #"+key.getFragmentID()+" does not match manifest ??!?");
                        c.receiveException(new GlacierException("Recovered object, but cannot re-encode it (strange!) -- try again later!"));
                        return;
                      }
              
                      c.receiveResult(frag[key.getFragmentID()]);
                    }
                    public void receiveException(Exception e) {
                      c.receiveException(new GlacierException("Recovered object, but re-encode failed: "+e));
                      e.printStackTrace();
                    }
                  });
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
      public void receiveException(Exception e) {
        warn("Exception while checking for "+key.toStringFull()+" in trash storage -- ignoring");
      }
    };
    
/*    if ((trashStorage!=null) && trashStorage.exists(key)) {
      log(Logger.FINE, "retrieveFragment: Key "+key.toStringFull()+" found in trash, retrieving...");
      trashStorage.getObject(key, c2);
    } else {
      log(Logger.FINE, "retrieveFragment: Key "+key.toStringFull()+" not found in trash");
      c2.receiveResult(null);
    } */
    
    if (trashStorage!=null) {
      trashStorage.getObject(key, new Continuation() {
        public void receiveResult(Object o) {
          if (o != null) 
            log(Logger.FINE, "retrieveFragment: Key "+key.toStringFull()+" found in trash, retrieving...");
          else
            log(Logger.FINE, "retrieveFragment: Key "+key.toStringFull()+" not found in trash");

          c2.receiveResult(o);
        } 
        
        public void receiveException(Exception e) {
          warn("Exception while getting object " + key + " from trash " + e);
          c2.receiveResult(null);
        }
      });
    } else {
      log(Logger.FINE, "retrieveFragment: Key "+key.toStringFull()+" not found in trash");
      c2.receiveResult(null);
    }
  }

  public void rateLimitedRetrieveFragment(final FragmentKey key, final Manifest manifest, final char tag, final GlacierContinuation c) {
    log(Logger.FINE, "rateLimitedRetrieveFragment("+key+")");
    if (pendingTraffic.containsKey(key)) {
      log(Logger.FINE, "Fragment is already being retrieved -- discarding request");
      return;
    }
  
    log(Logger.FINE, "Added pending job: retrieveFragment("+key+")");  
    Continuation prev = (Continuation) pendingTraffic.put(key, new Continuation.SimpleContinuation() {
      public void receiveResult(Object o) {
        retrieveFragment(key, manifest, tag, c);
      }
    });
    
    if (prev != null)
      prev.receiveException(new GlacierException("Key collision in traffic shaper (rateLimitedRetrieveFragment)"));
  }

  public Id[][] getNeighborRanges() {
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

    Id[][] result = new Id[allIDs.size()][3];
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
      
      result[i][0] = ccwId;
      result[i][1] = currentElement;
      result[i][2] = cwId;
    }
    
    return result;
  }
  
  public void deliver(Id id, Message message) {

    final GlacierMessage msg = (GlacierMessage) message;
    log(Logger.FINE, "Received message " + msg + " with destination " + id + " from " + msg.getSource().getId());

    if (msg instanceof GlacierDataMessage) {
      GlacierDataMessage gdm = (GlacierDataMessage) msg;
      long thisSize = 1000;
      updateTokenBucket();
      for (int i=0; i<gdm.numKeys(); i++) {
        if (gdm.getFragment(i) != null)
          thisSize += gdm.getFragment(i).getPayload().length;
        if (gdm.getManifest(i) != null)
          thisSize += numFragments * 21;
      }

      tokenBucket -= thisSize;
      bucketConsumed += thisSize;
      if (bucketMin > tokenBucket)
        bucketMin = tokenBucket;

      log(Logger.FINE, "Token bucket contains "+tokenBucket+" tokens (consumed "+thisSize+")");
    }

    if (msg.isResponse()) {
      GlacierContinuation gc = (GlacierContinuation) continuations.get(new Integer(msg.getUID()));

      if (gc != null) {
        if (!gc.terminated) {
          log(Logger.FINE, "Resuming ["+gc+"]");
          gc.syncReceiveResult(msg);
          log(Logger.FINE, "---");
        } else {
          log(Logger.FINE, "Message UID#"+msg.getUID()+" is response, but continuation has already terminated");
        }
      } else {
        log(Logger.FINE, "Unusual: Message UID#"+msg.getUID()+" is response, but continuation not found");
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
        log(Logger.INFO, "Queried for " + gqm.getKey(i) + " (at "+fragmentLocation+")");
  
        keyA[i] = gqm.getKey(i);
        haveItA[i] = fragmentStorage.exists(gqm.getKey(i));
        if (haveItA[i]) {
          FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(gqm.getKey(i));
          if (metadata != null) {
            expirationA[i] = metadata.getCurrentExpiration();
          } else {
            warn("QUERY cannot read metadata in object "+gqm.getKey(i).toStringFull()+", storage returned null");
            expirationA[i] = 0;
            haveItA[i] = false;
          }
        } else {
          expirationA[i] = 0;
        }
        log(Logger.FINE, "My range is "+responsibleRange);
        log(Logger.FINE, "Location is "+fragmentLocation);
        authoritativeA[i] = responsibleRange.containsId(fragmentLocation);
        log(Logger.FINE, "Result: haveIt="+haveItA[i]+" amAuthority="+authoritativeA[i]+" expiration="+expirationA[i]);
      }
      
      sendMessage(
        null,
        new GlacierResponseMessage(gqm.getUID(), keyA, haveItA, expirationA, authoritativeA, getLocalNodeHandle(), gqm.getSource().getId(), true, gqm.getTag()),
        gqm.getSource()
      );

    } else if (msg instanceof GlacierNeighborRequestMessage) {
      final GlacierNeighborRequestMessage gnrm = (GlacierNeighborRequestMessage) msg;
      final IdSet requestedNeighbors = neighborStorage.scan(gnrm.getRequestedRange());
      final int numRequested = requestedNeighbors.numElements();

      if (numRequested < 1) {
        log(Logger.FINE, "No neighbors in that range -- canceled");
        return; 
      }
            
      log(Logger.INFO, "Neighbor request for "+gnrm.getRequestedRange()+", found "+numRequested+" neighbors");
              
      final Id[] neighbors = new Id[numRequested];
      final long[] lastSeen = new long[numRequested];
            
      Iterator iter = requestedNeighbors.getIterator();
      for (int i=0; i<numRequested; i++)
        neighbors[i] = (Id)(iter.next());
              
      neighborStorage.getObject(neighbors[0], new Continuation() {
        int currentLookup = 0;
        
        public void receiveResult(Object o) {
          log(Logger.FINE, "Continue: NeighborRequest from "+gnrm.getSource().getId()+" for range "+gnrm.getRequestedRange());
        
          if (o == null) {
            warn("Problem while retrieving neighbors -- canceled");
            return;
          }
          
          if (o instanceof Long) {
            log(Logger.FINE, "Retr: Neighbor "+neighbors[currentLookup]+" was last seen at "+o);
            lastSeen[currentLookup] = ((Long)o).longValue();
            currentLookup ++;
            if (currentLookup < numRequested) {
              neighborStorage.getObject(neighbors[currentLookup], this);
            } else {
              log(Logger.FINE, "Sending neighbor response...");
              sendMessage(
                null,
                new GlacierNeighborResponseMessage(gnrm.getUID(), neighbors, lastSeen, getLocalNodeHandle(), gnrm.getSource().getId(), gnrm.getTag()),
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

      log(Logger.INFO, "SyncRequest from "+gsm.getSource().getId()+" for "+gsm.getRange()+" offset "+gsm.getOffsetFID());
      log(Logger.FINE, "Contains "+gsm.getBloomFilter());
      
      Iterator iter = fragmentStorage.scan().getIterator();
      final IdRange range = gsm.getRange();
      final int offset = gsm.getOffsetFID();
      final BloomFilter bv = gsm.getBloomFilter();
      final long earliestAcceptableExpiration = environment.getTimeSource().currentTimeMillis() + syncMinRemainingLifetime;
      final long latestAcceptableStoredSince = environment.getTimeSource().currentTimeMillis() - syncMinQuietTime;
      
      final Vector missing = new Vector();
      
      while (iter.hasNext()) {
        FragmentKey fkey = (FragmentKey)iter.next();
        Id thisPos = getFragmentLocation(fkey);
        if (range.containsId(thisPos)) {
          FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(fkey);
          if (metadata != null) {
            if (!bv.contains(getHashInput(fkey.getVersionKey(), metadata.getCurrentExpiration()))) {
              if (metadata.getCurrentExpiration() >= earliestAcceptableExpiration) {
                if (metadata.getStoredSince() <= latestAcceptableStoredSince) {
                  log(Logger.FINER, fkey+" @"+thisPos+" - MISSING");
                  missing.add(fkey);
                  if (missing.size() >= syncMaxFragments) {
                    log(Logger.INFO, "Limit of "+syncMaxFragments+" missing fragments reached");
                    break;
                  }
                } else {
                  log(Logger.FINE, fkey+" @"+thisPos+" - TOO FRESH (stored "+(environment.getTimeSource().currentTimeMillis()-metadata.getStoredSince())+"ms)");
                }
              } else {
                log(Logger.FINE, fkey+" @"+thisPos+" - EXPIRES SOON (in "+(metadata.getCurrentExpiration()-environment.getTimeSource().currentTimeMillis())+"ms)");
              }
            } else {
              log(Logger.FINER, fkey+" @"+thisPos+" - OK");
            }
          } else {
            warn("SYNC RESPONSE cannot read metadata in object "+fkey.toStringFull()+", storage returned null");
          }
        } else log(Logger.FINER, fkey+" @"+thisPos+" - OUT OF RANGE");
      }

      if (missing.isEmpty()) {
        log(Logger.INFO, "No fragments missing. OK. ");
        return;
      }
      
      log(Logger.INFO, "Sending "+missing.size()+" fragments to "+gsm.getSource().getId());
      
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
      
          log(Logger.FINE, "Retrieved manifest "+thisKey + " (dest="+gsm.getSource().getId()+", offset="+offset+")");
          
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
          log(Logger.FINE, "He should have key "+keys[manifestIndex]+" @"+getFragmentLocation(keys[manifestIndex]));
          manifestIndex ++;
          currentLookup ++;
          if ((manifestIndex == manifestAggregationFactor) || (currentLookup == numLookups)) {
            log(Logger.FINE, "Sending a packet with "+keys.length+" manifests to "+gsm.getSource().getId());
            
            sendMessage(
              null,
              new GlacierDataMessage(getUID(), keys, fragments, manifests, getLocalNodeHandle(), gsm.getSource().getId(), false, tagSyncManifests),
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

    } else if (msg instanceof GlacierRefreshProbeMessage) {
      final GlacierRefreshProbeMessage grpm = (GlacierRefreshProbeMessage) msg;
      Id requestedId = grpm.getRequestedId();
      
      log(Logger.INFO, "Refresh probe for "+requestedId+" (RR="+responsibleRange+")");
      
      Id[][] ranges = getNeighborRanges();
      IdRange returnedRange = null;
      boolean online = false;

      if (responsibleRange.containsId(requestedId)) {
        returnedRange = responsibleRange;
        online = true;
      } else {
        online = false;
        for (int i=0; i<ranges.length; i++) {
          IdRange thisRange = factory.buildIdRange(ranges[i][0], ranges[i][2]);
          log(Logger.FINE, " - "+thisRange+" ("+ranges[i][1]+")");
          if (thisRange.containsId(requestedId))
            returnedRange = thisRange;
        }
      }
      
      sendMessage(
        null,
        new GlacierRefreshResponseMessage(grpm.getUID(), returnedRange, online, getLocalNodeHandle(), grpm.getSource().getId(), grpm.getTag()),
        grpm.getSource()
      );
      
    } else if (msg instanceof GlacierRefreshPatchMessage) {
      final GlacierRefreshPatchMessage grpm = (GlacierRefreshPatchMessage) msg;

      log(Logger.INFO, "AR Refresh patches received for "+grpm.numKeys()+" keys. Processing...");
      Continuation c = new Continuation() {
        static final int phaseFetch = 1;
        static final int phaseStore = 2;
        static final int phaseAdvance = 3;
        int[] successes = new int[grpm.numKeys()];
        int currentPhase = phaseAdvance;
        FragmentKey currentKey = null;
        int currentIndex = 0;
        int currentFID = -1;
        
        public void receiveResult(Object o) {
          if (currentPhase == phaseFetch) {
            log(Logger.FINE, "AR Patch: Got FAM for "+currentKey);
         
            FragmentAndManifest fam = (FragmentAndManifest) o;
            fam.manifest.update(grpm.getLifetime(currentIndex), grpm.getSignature(currentIndex));
            
            if (policy.checkSignature(fam.manifest, currentKey.getVersionKey())) {
              FragmentMetadata metadata = (FragmentMetadata) fragmentStorage.getMetadata(currentKey);
              if (metadata != null) {
                if (metadata.currentExpirationDate <= grpm.getLifetime(currentIndex)) {
                  currentPhase = phaseStore;
                  if (metadata.currentExpirationDate == grpm.getLifetime(currentIndex)) {
                    log(Logger.FINE, "AR Duplicate refresh request (prev="+metadata.previousExpirationDate+" cur="+metadata.currentExpirationDate+" updated="+grpm.getLifetime(currentIndex)+") -- ignoring");
                  } else {
                    FragmentMetadata newMetadata = new FragmentMetadata(grpm.getLifetime(currentIndex), metadata.currentExpirationDate, metadata.storedSince);
                    log(Logger.FINE, "AR FAM "+currentKey+" updated ("+newMetadata.previousExpirationDate+" -> "+newMetadata.currentExpirationDate+"), writing to disk...");
                    fragmentStorage.store(currentKey, newMetadata, fam, this);
                    return;
                  }
                } else {
                  warn("RefreshPatch attempts to roll back lifetime from "+metadata.currentExpirationDate+" to "+grpm.getLifetime(currentIndex));
                  currentPhase = phaseStore;
                }
              } else {
                warn("Cannot fetch metadata for key "+currentKey+", got 'null'");
                currentPhase = phaseAdvance;
              }
            } else {
              warn("RefreshPatch with invalid signature: "+currentKey);
              currentPhase = phaseAdvance;
            }
          }
          
          if (currentPhase == phaseStore) {
            log(Logger.FINE, "AR Patch: Update completed for "+currentKey);
            successes[currentIndex] ++;
            currentPhase = phaseAdvance;
          }
          
          if (currentPhase == phaseAdvance) {
            do {
              currentFID ++;
              if (currentFID >= numFragments) {
                currentFID = 0;
                currentIndex ++;
              }
              
              if (currentIndex >= grpm.numKeys()) {
                respond();
                return;
              }
              
              currentKey = new FragmentKey(grpm.getKey(currentIndex), currentFID);
            } while (!fragmentStorage.exists(currentKey));
            
            currentPhase = phaseFetch;
            log(Logger.FINE, "AR Patch: Fetching FAM for "+currentKey);
            fragmentStorage.getObject(currentKey, this);
          }
        }
        public void respond() {
          int totalSuccesses = 0;
          for (int i=0; i<successes.length; i++)
            totalSuccesses += successes[i];

          log(Logger.FINE, "AR Patch: Sending response ("+totalSuccesses+" updates total)");

          sendMessage(
            null,
            new GlacierRefreshCompleteMessage(grpm.getUID(), grpm.getAllKeys(), successes, getLocalNodeHandle(), grpm.getSource().getId(), grpm.getTag()),
            grpm.getSource()
          );
        }
        public void receiveException(Exception e) {
          warn("Exception while processing AR patch (key "+currentKey+", phase "+currentPhase+"): "+e);
          e.printStackTrace();
          currentPhase = phaseAdvance;
          receiveResult(null);
        }
      };              
        
      c.receiveResult(null);
    
    } else if (msg instanceof GlacierRangeQueryMessage) {
      final GlacierRangeQueryMessage grqm = (GlacierRangeQueryMessage) msg;
      IdRange requestedRange = grqm.getRequestedRange();
      
      log(Logger.INFO, "Range query for "+requestedRange);

      Id[][] ranges = getNeighborRanges();
      
      for (int i=0; i<ranges.length; i++) {
        IdRange thisRange = factory.buildIdRange(ranges[i][0], ranges[i][2]);
        IdRange intersectRange = requestedRange.intersectRange(thisRange);
        if (!intersectRange.isEmpty()) {
          log(Logger.FINE, "     - Intersects: "+intersectRange+", sending RangeForward");
          sendMessage(
            ranges[i][1],
            new GlacierRangeForwardMessage(grqm.getUID(), requestedRange, grqm.getSource(), getLocalNodeHandle(), ranges[i][1], grqm.getTag()),
            null
          );
        }
      }
      
      log(Logger.FINE, "Finished processing range query");
      
      return;
      
    } else if (msg instanceof GlacierRangeForwardMessage) {
      GlacierRangeForwardMessage grfm = (GlacierRangeForwardMessage) msg;
      
      if (!grfm.getDestination().equals(getLocalNodeHandle().getId())) {
        log(Logger.WARNING, "Glog(Logger.WARNINGNot for us (dest="+grfm.getDestination()+", we="+getLocalNodeHandle().getId());
        return;
      }
      
      IdRange commonRange = responsibleRange.intersectRange(grfm.getRequestedRange());
      if (!commonRange.isEmpty()) {
        log(Logger.INFO, "Range forward: Returning common range "+commonRange+" to requestor "+grfm.getRequestor());
        sendMessage(
          null,
          new GlacierRangeResponseMessage(grfm.getUID(), commonRange, getLocalNodeHandle(), grfm.getRequestor().getId(), grfm.getTag()),
          grfm.getRequestor()
        );
      } else {
        warn("Received GRFM by "+grfm.getRequestor()+", but no common range??!? -- ignored");
      }

      return;
      
    } else if (msg instanceof GlacierFetchMessage) {
      final GlacierFetchMessage gfm = (GlacierFetchMessage) msg;
      log(Logger.INFO, "Fetch request for " + gfm.getKey(0) + ((gfm.getNumKeys()>1) ? (" and " + (gfm.getNumKeys() - 1) + " other keys") : "") + ", request="+gfm.getRequest());

      /* FetchMessages are sent during recovery to retrieve a fragment from
         another node. They can be answered a) if the recipient has a copy
         of the fragment, or b) if the recipient has a full replica of
         the object. In the second case, the fragment is created on-the-fly */

      fragmentStorage.getObject(gfm.getKey(0), new Continuation() {
        int currentLookup = 0;
        Fragment fragment[] = new Fragment[gfm.getNumKeys()];
        Manifest manifest[] = new Manifest[gfm.getNumKeys()];
        int numFragments = 0, numManifests = 0;
        
        public void returnResponse() {
          log(Logger.FINE, "Returning response with "+numFragments+" fragments, "+numManifests+" manifests ("+gfm.getNumKeys()+" queries originally)");
          sendMessage(
            null,
            new GlacierDataMessage(gfm.getUID(), gfm.getAllKeys(), fragment, manifest, getLocalNodeHandle(), gfm.getSource().getId(), true, gfm.getTag()),
            gfm.getSource()
          );
        }
        public void receiveResult(Object o) {
          if (o != null) {
            log(Logger.INFO, "Fragment "+gfm.getKey(currentLookup)+" found ("+o+")");
            FragmentAndManifest fam = (FragmentAndManifest) o;
            fragment[currentLookup] = ((gfm.getRequest() & GlacierFetchMessage.FETCH_FRAGMENT)!=0) ? fam.fragment : null;
            if (fragment[currentLookup] != null)
              numFragments ++;
            manifest[currentLookup] = ((gfm.getRequest() & GlacierFetchMessage.FETCH_MANIFEST)!=0) ? fam.manifest : null;
            if (manifest[currentLookup] != null)
              numManifests ++;
          } else {
            log(Logger.INFO, "Fragment "+gfm.getKey(currentLookup)+" not found");
            fragment[currentLookup] = null;
            manifest[currentLookup] = null;
          }
          
          nextLookup();
        }
        public void nextLookup() {
          currentLookup ++;
          if (currentLookup >= gfm.getNumKeys())
            returnResponse();
          else
            fragmentStorage.getObject(gfm.getKey(currentLookup), this);
        }
        public void receiveException(Exception e) { 
          warn("Exception while retrieving fragment "+gfm.getKey(currentLookup)+" (lookup #"+currentLookup+"), e="+e);
          e.printStackTrace();
          fragment[currentLookup] = null;
          manifest[currentLookup] = null;
          nextLookup();
        }
      });
    } else if (msg instanceof GlacierDataMessage) {
      final GlacierDataMessage gdm = (GlacierDataMessage) msg;
      for (int i=0; i<gdm.numKeys(); i++) {
        final FragmentKey thisKey = gdm.getKey(i);
        final Fragment thisFragment = gdm.getFragment(i);
        final Manifest thisManifest = gdm.getManifest(i);
        
        if ((thisFragment != null) && (thisManifest != null)) {
          log(Logger.INFO, "Data: Fragment+Manifest for "+thisKey);

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
            log(Logger.FINE, "Verified ok. Storing locally.");
            
            FragmentAndManifest fam = new FragmentAndManifest(thisFragment, thisManifest);

            fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), 0, environment.getTimeSource().currentTimeMillis()), fam,
              new Continuation() {
                public void receiveResult(Object o) {
                  log(Logger.INFO, "Stored OK, sending receipt: "+thisKey);

                  sendMessage(
                    null,
                    new GlacierResponseMessage(gdm.getUID(), thisKey, true, thisManifest.getExpiration(), responsibleRange.containsId(getFragmentLocation(thisKey)), getLocalNodeHandle(), gdm.getSource().getId(), true, gdm.getTag()),
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
            if ((metadata == null) || (metadata.getCurrentExpiration() < thisManifest.getExpiration())) {
              log(Logger.INFO, "Replacing old manifest for "+thisKey+" (expires "+((metadata == null) ? "(broken)" : ""+metadata.getCurrentExpiration())+") by new one (expires "+thisManifest.getExpiration()+")");

              fragmentStorage.getObject(thisKey, new Continuation() {
                public void receiveResult(Object o) {
                  if (o instanceof FragmentAndManifest) {
                    FragmentAndManifest fam = (FragmentAndManifest) o;

                    log(Logger.FINE, "Got FAM for "+thisKey+", now replacing old manifest with new one...");
                    
                    String fault = null;
                    
                    if (!thisManifest.validatesFragment(fam.fragment, thisKey.getFragmentID()))
                      fault = "Update: Manifest does not validate this fragment";
                    if (!policy.checkSignature(thisManifest, thisKey.getVersionKey()))
                      fault = "Update: Manifest is not signed properly";
                    if (!Arrays.equals(thisManifest.getObjectHash(), fam.manifest.getObjectHash()))
                      fault = "Update: Object hashes not equal";
                    for (int i=0; i<numFragments; i++)
                      if (!Arrays.equals(thisManifest.getFragmentHash(i), fam.manifest.getFragmentHash(i)))
                        fault = "Update: Fragment hash #"+i+" does not match";

                    if (fault == null) {
                      fam.manifest = thisManifest;
                      fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), ((metadata == null) ? 0 : metadata.getCurrentExpiration()), environment.getTimeSource().currentTimeMillis()), fam,
                        new Continuation() {
                          public void receiveResult(Object o) {
                            log(Logger.FINE, "Old manifest for "+thisKey+" replaced OK, sending receipt");
                            sendMessage(
                              null,
                              new GlacierResponseMessage(gdm.getUID(), thisKey, true, thisManifest.getExpiration(), true, getLocalNodeHandle(), gdm.getSource().getId(), true, gdm.getTag()),
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
                      warn(fault);
                    }
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
              warn("We already have exp="+((metadata == null) ? "(broken)" : ""+metadata.getCurrentExpiration())+", discarding manifest for "+thisKey+" with exp="+thisManifest.getExpiration());
            }
            
            continue;
          }

          log(Logger.INFO, "Data: Manifest for: "+thisKey+", must fetch");

          final long tStart = environment.getTimeSource().currentTimeMillis();
          rateLimitedRetrieveFragment(thisKey, thisManifest, tagSyncFetch, new GlacierContinuation() {
            public String toString() {
              return "Fetch synced fragment: "+thisKey;
            }
            public void receiveResult(Object o) {
              if (o instanceof Fragment) {
                if (!fragmentStorage.exists(thisKey)) {
                  log(Logger.INFO, "Received fragment "+thisKey+" (from primary) matches existing manifest, storing...");
              
                  FragmentAndManifest fam = new FragmentAndManifest((Fragment) o, thisManifest);

                  fragmentStorage.store(thisKey, new FragmentMetadata(thisManifest.getExpiration(), 0, environment.getTimeSource().currentTimeMillis()), fam,
                    new Continuation() {
                      public void receiveResult(Object o) {
                        log(Logger.FINE, "Recovered fragment stored OK");
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
              if (e instanceof GlacierNotEnoughFragmentsException) {
                GlacierNotEnoughFragmentsException gnf = (GlacierNotEnoughFragmentsException) e;
                log(Logger.INFO, "Not enough fragments to reconstruct "+thisKey+": "+gnf.checked+"/"+numFragments+" checked, "+gnf.found+" found, "+numSurvivors+" needed");
              } else {
                warn("Exception while recovering synced fragment "+thisKey+": "+e);
                e.printStackTrace();
              }
              
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
  
  public void setSyncInterval(int syncIntervalSec) {
    this.syncInterval = syncIntervalSec * SECONDS;
  }
  
  public void setSyncMaxFragments(int syncMaxFragments) {
    this.syncMaxFragments = syncMaxFragments;
  }
  
  public void setRateLimit(int rps) {
    this.rateLimitedRequestsPerSecond = rps;
  }
  
  public void setNeighborTimeout(long neighborTimeoutMin) {
    this.neighborTimeout = neighborTimeoutMin * MINUTES;
  }
  
  public void setBandwidthLimit(long bytesPerSecond, long maxBurst) {
    this.bucketTokensPerSecond = bytesPerSecond;
    this.bucketMaxBurstSize = maxBurst;
  }
  
  public long getTrashSize() {
    if (trashStorage == null)
      return 0;
      
    return trashStorage.getStorage().getTotalSize();
  }
  
  public void emptyTrash(final Continuation c) {
    if (trashStorage != null) {
      log(Logger.INFO, "Emptying trash (removing all objects)");

      trashStorage.flush(c);
    } else {
      c.receiveResult(null);
    }
  }
  
  public void addStatisticsListener(GlacierStatisticsListener gsl) {
    listeners.add(gsl);
  }
  
  public void removeStatisticsListener(GlacierStatisticsListener gsl) {
    listeners.removeElement(gsl);
  }
  
  public Environment getEnvironment() {
    return environment;
  }
}
