package rice.p2p.aggregation;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Arrays;

import rice.Continuation;
import rice.Executable;
import rice.p2p.aggregation.messaging.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.VersionKey;
import rice.p2p.glacier.VersioningPast;
import rice.p2p.glacier.v2.DebugContent;
import rice.p2p.past.Past;
import rice.p2p.past.PastImpl;
import rice.p2p.past.PastContent;
import rice.p2p.past.PastContentHandle;
import rice.p2p.past.gc.GCPast;
import rice.p2p.past.gc.GCPastContent;
import rice.p2p.past.gc.GCPastContentHandle;
import rice.persistence.StorageManager;
import rice.visualization.server.DebugCommandHandler;
import rice.p2p.glacier.v2.GlacierContentHandle;

import rice.post.PostEntityAddress;
import rice.post.storage.SignedData;
import rice.post.security.SecurityUtils;

public class AggregationImpl implements Past, GCPast, VersioningPast, Aggregation, Application, DebugCommandHandler {

  protected final Past aggregateStore;
  protected final StorageManager waitingList;
  protected final AggregationPolicy policy;
  protected final AggregateList aggregateList;
  protected final Endpoint endpoint;
  protected final Past objectStore;
  protected final String instance;
  protected final IdFactory factory;
  protected final String debugID;
  protected final Random random;
  protected final Node node;

  private final char tiFlush = 1;
  private final char tiMonitor = 2;
  private final char tiConsolidate = 3;
  private final char tiStatistics = 4;
  private final char tiExpire = 5;
  protected Hashtable timers;
  protected Continuation flushWait;
  protected boolean rebuildInProgress;
  protected Vector monitorIDs;
  protected AggregationStatistics stats;

  private int loglevel = 2;
  private final boolean logStatistics = true;

  private static final long SECONDS = 1000;
  private static final long MINUTES = 60 * SECONDS;
  private static final long HOURS = 60 * MINUTES;
  private static final long DAYS = 24 * HOURS;
  private static final long WEEKS = 7 * DAYS;

  private static final long flushDelayAfterJoin = 30 * SECONDS;
  private static final long flushStressInterval = 5 * MINUTES;
  private static long flushInterval = 5 * MINUTES;

  private static int maxAggregateSize = 1024*1024;
  private static int maxObjectsInAggregate = 25;
  private static int maxAggregatesPerRun = 2;
  
  private static final boolean addMissingAfterRefresh = true;
  private static final int maxReaggregationPerRefresh = 100;
  private static final int nominalReferenceCount = 2;
  private static final int maxPointersPerAggregate = 100;
  private static final long pointerArrayLifetime = 2 * WEEKS;
  private static final long aggregateGracePeriod = 1 * DAYS;

  private static final long aggrRefreshInterval = 15 * MINUTES;
  private static final long aggrRefreshDelayAfterJoin = 70 * SECONDS;
  private static long expirationRenewThreshold = 3 * DAYS;

  private static final boolean monitorEnabled = false;
  private static final long monitorRefreshInterval = 15 * MINUTES;

  private static final long consolidationDelayAfterJoin = 5 * MINUTES;
  private static long consolidationInterval = 15 * MINUTES;
  private static long consolidationThreshold = 14 * DAYS;
  private static int consolidationMinObjectsInAggregate = 20;
  private static double consolidationMinComponentsAlive = 0.8;

  private static final boolean aggregateLogEnabled = true;

  private static final long statsGranularity = 1 * HOURS;
  private static final long statsRange = 3 * WEEKS;
  private static final long statsInterval = 60 * SECONDS;

  private final double jitterRange = 0.1;

  public AggregationImpl(Node node, Past aggregateStore, Past objectStore, StorageManager waitingList, String configFileName, IdFactory factory, String instance) throws IOException {
    this(node, aggregateStore, objectStore, waitingList, configFileName, factory, instance, getDefaultPolicy());
  }

  public AggregationImpl(Node node, Past aggregateStore, Past objectStore, StorageManager waitingList, String configFileName, IdFactory factory, String instance, AggregationPolicy policy) throws IOException {
    this.endpoint = node.registerApplication(this, instance);
    this.waitingList = waitingList;
    this.instance = instance;
    this.aggregateStore = aggregateStore;
    this.objectStore = objectStore;
    this.node = node;
    this.timers = new Hashtable();
    this.random = new Random();
    this.aggregateList = new AggregateList(configFileName, getLocalNodeHandle().getId().toString(), factory, aggregateLogEnabled);
    this.stats = aggregateList.getStatistics(statsGranularity, statsRange, nominalReferenceCount);
    this.policy = policy;
    this.factory = factory;
    this.flushWait = null;
    this.rebuildInProgress = false;
    this.monitorIDs = new Vector();
    this.debugID = "A" + Character.toUpperCase(instance.charAt(instance.lastIndexOf('-')+1));

    if (!aggregateList.readOK())
      warn("Failed to read configuration file; aggregate list must be rebuilt!");
    else 
      log(2, "Aggregate list read OK -- current root: " + ((aggregateList.getRoot() == null) ? "null" : aggregateList.getRoot().toStringFull()));

    removeDeadAggregates();

    addTimer(jitterTerm(flushDelayAfterJoin), tiFlush);
    addTimer(jitterTerm(aggrRefreshDelayAfterJoin), tiExpire);
    addTimer(jitterTerm(consolidationDelayAfterJoin), tiConsolidate);
    addTimer(statsInterval, tiStatistics);
    if (monitorEnabled)
      addTimer(monitorRefreshInterval, tiMonitor);
  }

  private static AggregationPolicy getDefaultPolicy() {
    return new AggregationDefaultPolicy();
  }

  private long jitterTerm(long basis) {
    return (long)((1-jitterRange)*basis) + random.nextInt((int)(2*jitterRange*basis));
  }

  private String getLogPrefix() {
    return "COUNT: " + System.currentTimeMillis() + " " + debugID;
  }

  private void log(int level, String str) {
    if (level <= loglevel)
      System.out.println(getLogPrefix() + level + " " + str);
  }

  private void warn(String str) {
    System.out.println(getLogPrefix() + " *** WARNING *** " + str);
  }

  /**
   * Schedule a timer event
   *
   * @param timeoutMsec Length of the delay (in milliseconds)
   * @param timeoutID Identifier (to distinguish between multiple timers)
   */
  private void addTimer(long timeoutMsec, char timeoutID) {
    /*
     *  We schedule a GlacierTimeoutMessage with the ID of the
     *  requested timer. This message will be delivered if the
     *  pires and it has not been removed in the meantime.
     */
    CancellableTask timer = endpoint.scheduleMessage(new AggregationTimeoutMessage(timeoutID, getLocalNodeHandle()), timeoutMsec);
    timers.put(new Integer(timeoutID), timer);
  }

  /**
   * Cancel a timer event that has not yet occurred
   *
   * @param timeoutID Identifier of the timer event to be cancelled
   */
  private void removeTimer(int timeoutID) {
    CancellableTask timer = (CancellableTask) timers.remove(new Integer(timeoutID));

    if (timer != null) {
      timer.cancel();
    }
  }

  private void panic(String s) throws Error {
    System.err.println("PANIC: " + s);
    throw new Error("Panic");
  }

  public String handleDebugCommand(String command) {
    if (command.indexOf(" ") < 0)
      return null;
  
    String requestedInstance = command.substring(0, command.indexOf(" "));
    String myInstance = "aggr."+instance.substring(instance.lastIndexOf("-") + 1);
    String cmd = command.substring(requestedInstance.length() + 1);
    
    if (!requestedInstance.equals(myInstance) && !requestedInstance.equals("a")) {
      String subResult = null;

      if ((subResult == null) && (aggregateStore instanceof DebugCommandHandler))
        subResult = ((DebugCommandHandler)aggregateStore).handleDebugCommand(command);
      if ((subResult == null) && (objectStore instanceof DebugCommandHandler))
        subResult = ((DebugCommandHandler)objectStore).handleDebugCommand(command);

      return subResult;
    }
  
    log(2, "Debug command: "+cmd);
  
    if (cmd.startsWith("status")) {
      return stats.numObjectsTotal+" objects total\n"+stats.numObjectsAlive+" objects alive\n"+
             stats.numAggregatesTotal+" aggregates total\n"+stats.numPointerArrays+" pointer arrays\n"+
             stats.criticalAggregates+" critical aggregates\n"+stats.orphanedAggregates+" orphaned aggregates\n";
    }

    if (cmd.startsWith("insert")) {
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

    if (cmd.startsWith("set loglevel")) {
      loglevel = Integer.parseInt(cmd.substring(13));
      return "Log level set to "+loglevel;
    }

    if (cmd.startsWith("show config")) {
      return 
        "flushDelayAfterJoin = " + (int)(flushDelayAfterJoin / SECONDS) + " sec\n" +
        "flushInterval = " + (int)(flushInterval / SECONDS) + " sec\n" +
        "maxAggregateSize = " + maxAggregateSize + " bytes\n" +
        "maxObjectsInAggregate = " + maxObjectsInAggregate + " objects\n" +
        "maxAggregatesPerRun = " + maxAggregatesPerRun + " aggregates\n" +
        "addMissingAfterRefresh = " + addMissingAfterRefresh + "\n" +
        "nominalReferenceCount = " + nominalReferenceCount + "\n" +
        "maxPointersPerAggregate = " + maxPointersPerAggregate + "\n" +
        "pointerArrayLifetime = " + (int)(pointerArrayLifetime / DAYS) + " days\n" +
        "aggrRefreshInterval = " + (int)(aggrRefreshInterval / SECONDS) + " sec\n" +
        "aggrRefreshDelayAfterJoin = " + (int)(aggrRefreshDelayAfterJoin / SECONDS) + " sec\n" +
        "expirationRenewThreshold = " + (int)(expirationRenewThreshold / HOURS) + " hrs\n" +
        "consolidationDelayAfterJoin = " + (int)(consolidationDelayAfterJoin / SECONDS) + " sec\n" +
        "consolidationInterval = " + (int)(consolidationInterval / SECONDS) + " sec\n" +
        "consolidationThreshold = " + (int)(consolidationThreshold / HOURS) + " hrs\n" +
        "consolidationMinObjectsInAggregate = " + consolidationMinObjectsInAggregate + "\n" +
        "consolidationMinComponentsAlive = " + consolidationMinComponentsAlive + "\n";
    }    

    if (cmd.startsWith("ls")) {
      Enumeration enum = aggregateList.elements();
      StringBuffer result = new StringBuffer();
      int numAggr = 0, numObj = 0;

      long now = System.currentTimeMillis();
      if (cmd.indexOf("-r") < 0)
        now = 0;

      aggregateList.recalculateReferenceCounts(null);
      aggregateList.resetMarkers();
      while (enum.hasMoreElements()) {
        AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
        if (!aggr.marker) {
          result.append("***" + aggr.key.toStringFull() + " (" + aggr.objects.length + " obj, " + 
                   aggr.pointers.length + " ptr, " + aggr.referenceCount + " ref, exp=" + 
                   (aggr.currentLifetime - now) + ")\n");
          for (int i=0; i<aggr.objects.length; i++)
            result.append("    #"+i+" "+
              aggr.objects[i].key.toStringFull()+"v"+aggr.objects[i].version +
              ", lt=" + (aggr.objects[i].currentLifetime-now) +
              ", rt=" + (aggr.objects[i].refreshedLifetime-now) +
              ", size=" + aggr.objects[i].size + " bytes\n");
          for (int i=0; i<aggr.pointers.length; i++) 
            result.append("    Ref "+aggr.pointers[i].toStringFull()+"\n");
          result.append("\n");
          aggr.marker = true;
          numAggr ++;
          numObj += aggr.objects.length;
        }
      }

      result.append(numAggr + " aggregate(s), " + numObj + " object(s)");
      
      return result.toString();
    }

    if (cmd.startsWith("write list")) {
      aggregateList.writeToDisk();
      return "Done, new root is "+((aggregateList.getRoot()==null) ? "null" : aggregateList.getRoot().toStringFull());
    }

    if ((cmd.length() >= 5) && cmd.substring(0, 5).equals("reset")) {
      final String[] ret = new String[] { null };

      reset(new Continuation() {
        public void receiveResult(Object o) {
          ret[0] = "result("+o+")";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
        
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return ret[0];
    }

    if (cmd.startsWith("flush")) {
      final String[] ret = new String[] { null };

      flush(new Continuation() {
        public void receiveResult(Object o) {
          ret[0] = "result("+o+")";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
        
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return ret[0];
    }
    
    if (cmd.startsWith("get root")) {
      return "root="+((aggregateList.getRoot()==null) ? "null" : aggregateList.getRoot().toStringFull());
    }

    if (cmd.startsWith("set root")) {
      final String[] ret = new String[] { null };
      setHandle(factory.buildIdFromToString(cmd.substring(9)), new Continuation() {
        public void receiveResult(Object o) {
          ret[0] = "result("+o+")";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
        
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return ret[0];
    }

    if (cmd.startsWith("lookup")) {
      Id id = factory.buildIdFromToString(cmd.substring(7));

      final String[] ret = new String[] { null };
      lookup(id, false, new Continuation() {
        public void receiveResult(Object o) {
          ret[0] = "result("+o+")";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
      
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return "lookup("+id+")="+ret[0];
    }

    if (cmd.startsWith("handles")) {
      String args = cmd.substring(8);
      Id id = factory.buildIdFromToString(args.substring(args.indexOf(' ') + 1));
      int max = Integer.parseInt(args.substring(0, args.indexOf(' ')));

      final String[] ret = new String[] { null };
      lookupHandles(id, max, new Continuation() {
        public void receiveResult(Object o) {
          if (o instanceof PastContentHandle[]) {
            PastContentHandle[] oA = (PastContentHandle[]) o;
            ret[0] = "";
            for (int i=0; i<oA.length; i++)
              ret[0] = ret[0] + "#"+i+" "+oA[i]+"\n";
            ret[0] = ret[0] + oA.length + " handle(s) returned\n";
          } else ret[0] = "result("+o+") -- no handles returned!";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
      
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return "Handles("+max+","+id+"):\n"+ret[0];
    }
      
    if (cmd.startsWith("refresh all")) {
      long expiration = System.currentTimeMillis() + Long.parseLong(cmd.substring(12));
      TreeSet ids = new TreeSet();
      String result;
      
      aggregateList.resetMarkers();

      Enumeration enum = aggregateList.elements();
      while (enum.hasMoreElements()) {
        AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
        if (!aggr.marker) {
          aggr.marker = true;
          for (int i=0; i<aggr.objects.length; i++)
            ids.add(aggr.objects[i].key);
        }
      }
      
      if (!ids.isEmpty()) {
        Id[] allIds = (Id[]) ids.toArray(new Id[] {});
        result = "Refreshing " + allIds.length + " keys...\n";

        for (int i=0; i<allIds.length; i++)
          result = result + "#" + i + " " + allIds[i].toStringFull() + "\n";
      
        final String[] ret = new String[] { null };
        refresh(allIds, expiration, new Continuation() {
          public void receiveResult(Object o) {
            ret[0] = "result("+o+")";
          };
          public void receiveException(Exception e) {
            ret[0] = "exception("+e+")";
          }
        });

        while (ret[0] == null)
          Thread.currentThread().yield();
      
        result = result + ret[0];
      } else result = "Aggregate list is empty; nothing to refresh!";
      
      return result;
    }
      
    if (cmd.startsWith("refresh")) {
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

    if (cmd.startsWith("monitor remove") && monitorEnabled) {
      String[] args = cmd.substring(15).split(" ");
      if (args.length == 1) {
        Random rand = new Random();
        int howMany = Integer.parseInt(args[0]);
        
        if (howMany > monitorIDs.size())
          howMany = monitorIDs.size();
        
        for (int i=0; i<howMany; i++)
          monitorIDs.removeElementAt(rand.nextInt(monitorIDs.size()));
          
        return "Removed "+howMany+" elements; "+monitorIDs.size()+" elements left";
      } else return "Syntax: monitor remove <howMany>";
    }

    if (cmd.startsWith("monitor status") && monitorEnabled) {
      return "Monitor is "+(monitorEnabled ? ("enabled, monitoring "+monitorIDs.size()+" objects") : "disabled");
    }

    if (cmd.startsWith("monitor ls") && monitorEnabled) {
      StringBuffer result = new StringBuffer();
      Enumeration enum = monitorIDs.elements();
      
      while (enum.hasMoreElements())
        result.append(((Id)enum.nextElement()).toStringFull() + "\n");
        
      result.append(monitorIDs.size() + " object(s)");
      return result.toString();
    }

    if (cmd.startsWith("monitor check") && monitorEnabled) {
      final StringBuffer result = new StringBuffer();
      final String[] ret = new String[] { null };
      
      if (monitorIDs.isEmpty())
        return "Add objects first!";

      final long now = System.currentTimeMillis();
            
      Continuation c = new Continuation() {
        int currentLookup = 0;
        boolean lookupInAggrStore = false;
        boolean done = false;
        
        public void receiveResult(Object o) {
          log(3, "Monitor: Retr "+currentLookup+" a="+lookupInAggrStore+" got "+o);
          Id currentId = (Id) monitorIDs.elementAt(currentLookup);
          PastContentHandle[] handles = (PastContentHandle[]) o;
          GCPastContentHandle handle = null;
          boolean skipToNext = true;
          
          for (int i=0; i<handles.length; i++)
            if (handles[i] != null)
              handle = (GCPastContentHandle) handles[i];
          
          if (!lookupInAggrStore) {
            result.append(currentId.toStringFull() + " - OS ");
            result.append((handle==null) ? "--" : ""+(handle.getExpiration()-now));
            
            AggregateDescriptor adc = (AggregateDescriptor) aggregateList.getADC(currentId);
            if (adc != null) {
              result.append(" AD " + (adc.currentLifetime - now));
              
              int objDescIndex = adc.lookupNewest(currentId);
              if (objDescIndex >= 0) {
                ObjectDescriptor odc = adc.objects[objDescIndex];
                result.append(" OD " + (odc.currentLifetime - now));
                lookupInAggrStore = true;
                skipToNext = false;
                aggregateStore.lookupHandles(adc.key, 1, this);
              } else {
                result.append(" OD ??\n");
              }
            } else {
              result.append(" AD ??\n");
            }
          } else {
            result.append(" AS " + ((handle==null) ? "--\n" : ""+(handle.getExpiration()-now) + "\n"));
            lookupInAggrStore = false;
          }
          
          if (skipToNext) {
            currentLookup++;
            if (currentLookup < monitorIDs.size()) {
              log(3, "Monitor: Continuing with element "+currentLookup);
              objectStore.lookupHandles((Id) monitorIDs.elementAt(currentLookup), 1, this);
            } else {
              log(3, "Monitor: Done");
              ret[0] = "done";
            }
          }
        }
        public void receiveException(Exception e) {
          warn("Montior: Failed, e="+e);
          e.printStackTrace();
          ret[0] = "done";
        }
      };
      
      objectStore.lookupHandles((Id) monitorIDs.elementAt(0), 1, c);
      while (ret[0] == null)
        Thread.currentThread().yield();

      return result.toString();
    }

    if (cmd.startsWith("monitor add") && monitorEnabled) {
      String[] args = cmd.substring(12).split(" ");
      if (args.length == 6) {
        final int numFiles = Integer.parseInt(args[0]);
        final int avgBurstSize = Integer.parseInt(args[1]);
        final double sizeSkew = Double.parseDouble(args[2]);
        final int smallSize = Integer.parseInt(args[3]);
        final int largeSize = Integer.parseInt(args[4]);
        final long expiration = System.currentTimeMillis() + Long.parseLong(args[5]);
        final Random rand = new Random();
        
        Continuation c = new Continuation() {
          int remainingTotal = numFiles;
          public void receiveResult(Object o) {
            if (remainingTotal > 0) {
              final int burstSize = Math.min((int)((avgBurstSize*0.3) + rand.nextInt((int)(1.4*avgBurstSize))), remainingTotal);
              final Continuation outerContinuation = this;
              remainingTotal -= burstSize;
              log(3, "Inserting burst of size "+burstSize+", remaining objects: "+remainingTotal);
              Continuation c2 = new Continuation() {
                long remainingHere = burstSize;
                public void receiveResult(Object o) {
                  if (remainingHere > 0) {
                    log(3, "Continuing burst insert, "+remainingHere+" remaining");
                    int thisAvgSize = ((0.001*rand.nextInt(1000)) < sizeSkew) ? smallSize : largeSize;
                    int thisSize = (int)(0.3*thisAvgSize + rand.nextInt((int)(1.4*thisAvgSize)));
                    Id randomID = factory.buildRandomId(rand);
                    remainingHere --;
                    monitorIDs.add(randomID);
                    insert(new DebugContent(randomID, false, 0, new byte[thisSize]), expiration, this);
                  } else {
                    log(3, "Burst insertion complete, flushing...");
                    flush(outerContinuation);
                  }
                }
                public void receiveException(Exception e) {
                  warn("Monitor.add component insertion failed: "+e);
                  e.printStackTrace();
                  receiveResult(e);
                }
              };
              
              c2.receiveResult(new Boolean(true));
            } else {
              log(2, "Monitor add completed, "+numFiles+" objects created successfully");
            }            
          }
          public void receiveException(Exception e) {
            warn("Monitor.add aggregate insertion failed: "+e);
            e.printStackTrace();
            receiveResult(e);
          }
        };
        
        c.receiveResult(new Boolean(true));
        return "In progress...";
      }
      
      return "Syntax: monitor add <#files> <avgBurstSize> <sizeSkew> <smallSize> <largeSize> <lifetime>";
    }
      
    if (cmd.startsWith("killall")) {
      String args = cmd.substring(8);
      String expirationArg = args.substring(args.lastIndexOf(' ') + 1);
      String keyArg = args.substring(0, args.lastIndexOf(' '));

      Id id = factory.buildIdFromToString(keyArg);
      long expiration = System.currentTimeMillis() + Long.parseLong(expirationArg);

      AggregateDescriptor aggr = (AggregateDescriptor) aggregateList.getADC(id);
      if (aggr != null) {
        aggregateList.setAggregateLifetime(aggr, Math.min(aggr.currentLifetime, expiration));
        for (int i=0; i<aggr.objects.length; i++) {
          aggregateList.setObjectCurrentLifetime(aggr, i, Math.min(aggr.objects[i].currentLifetime, expiration));
          aggregateList.setObjectRefreshedLifetime(aggr, i, Math.min(aggr.objects[i].refreshedLifetime, expiration));
        }
        return "OK";
      }
      
      return "Aggregate "+id+" not found in aggregate list";
    }

    if (cmd.startsWith("waiting")) {
      Iterator iter = waitingList.scan().getIterator();
      String result = "";

      result = result + waitingList.scan().numElements()+ " object(s) waiting\n";
      
      while (iter.hasNext()) {
        Id thisId = (Id) iter.next();
        result = result + thisId.toStringFull()+" "+waitingList.getMetadata(thisId)+"\n";
      }
        
      return result;
    }
      
    if (cmd.startsWith("vlookup")) {
      String[] vkeyS = cmd.substring(8).split("v");
      Id key = factory.buildIdFromToString(vkeyS[0]);
      long version = Long.parseLong(vkeyS[1]);

      final String[] ret = new String[] { null };
      lookup(key, version, new Continuation() {
        public void receiveResult(Object o) {
          ret[0] = "result("+o+")";
        }
        public void receiveException(Exception e) {
          ret[0] = "exception("+e+")";
        }
      });
      
      while (ret[0] == null)
        Thread.currentThread().yield();
      
      return "vlookup("+key+"v"+version+")="+ret[0];
    }
    
    return null;
  }

  private void removeDeadAggregates() {
    Vector toRemove = new Vector();
    Enumeration enum = aggregateList.elements();
    long now = System.currentTimeMillis();
  
    /* Note: Multiple keys are mapped to a single aggregate descriptor, so the same ADC
       may be returned multiple times - but we must delete it only once! */
    
    while (enum.hasMoreElements()) {
      AggregateDescriptor adc = (AggregateDescriptor) enum.nextElement();
      if (adc.currentLifetime < (now - aggregateGracePeriod)) {
        if (!toRemove.contains(adc))
          toRemove.add(adc);
        warn("Scheduling dead aggregate for removal: "+adc.key.toStringFull()+"(expired "+adc.currentLifetime+")");
      }
    }
    
    if (toRemove.size() > 0) {
      log(2, "Removing "+toRemove.size()+" dead aggregates...");
      
      Enumeration rem = toRemove.elements();
      while (rem.hasMoreElements())
        aggregateList.removeAggregateDescriptor((AggregateDescriptor) rem.nextElement());
    }
  }

  private void storeAggregate(final Aggregate aggr, final long expiration, final ObjectDescriptor[] desc, final Id[] pointers, final Continuation command) {
    log(3, "storeAggregate() schedules content hash computation...");
    endpoint.process(new Executable() {
      public Object execute() {
        log(3, "storeAggregate() starts working on content hash...");
        return factory.buildId(aggr.getContentHash());
      }
    }, new Continuation() {
      public void receiveResult(Object o) {
        if (o instanceof Id) {
          aggr.setId((Id) o);
          log(2, "Storing aggregate, CH="+aggr.getId()+", expiration="+expiration+" (rel "+(expiration-System.currentTimeMillis())+") with "+desc.length+" objects:");
          for (int j=0; j<desc.length; j++)
            log(2, "#"+j+": "+desc[j]);

          Continuation c = new Continuation() {
            public void receiveResult(Object o) {
              AggregateDescriptor adc = new AggregateDescriptor(
                aggr.getId(),
                expiration,
                desc,
                pointers
              );

              if (o instanceof Boolean[]) {
                aggregateList.addAggregateDescriptor(adc);
                aggregateList.setRoot(aggr.getId());
                aggregateList.writeToDisk();
                log(3, "Aggregate inserted successfully");
                command.receiveResult(new Boolean(true));
              } else {
                warn("Unexpected result in aggregate insert (commit): "+o);
                command.receiveException(new AggregationException("Unexpected result (commit): "+o));
              }
            }
            public void receiveException(Exception e) {
              command.receiveException(e);
            }
          };
    
          if (aggregateStore instanceof GCPast) 
            ((GCPast)aggregateStore).insert(aggr, expiration, c);
          else
            aggregateStore.insert(aggr, c);
        } else {
          warn("storeAggregate() cannot determine content hash, received "+o);
          command.receiveException(new AggregationException("storeAggregate() cannot determine content hash"));
        }
      }
      public void receiveException(Exception e) {
        warn("storeAggregate() cannot determine content hash, exception "+e);
        e.printStackTrace();
        command.receiveException(e);
      }
    });
  }

  private void flushComplete(Object o) {
    if (flushWait != null) {
      Continuation c = flushWait;
      flushWait = null;
      if (o instanceof Exception) 
        c.receiveException((Exception) o);
      else 
        c.receiveResult(o);
    }
  }

  private void formAggregates(final Continuation command) {
    if (flushWait != null) {
      log(2, "Flush in progress... daisy-chaining continuation");
      final Continuation parent = flushWait;
      flushWait = new Continuation() {
        public void receiveResult(Object o) {
          log(2, "Daisy-chain receiveResult(), restarting "+command);
          parent.receiveResult(o);
          formAggregates(command);
        }
        public void receiveException(Exception e) {
          log(2, "Daisy-chain receiveException(), restarting "+command);
          parent.receiveException(e);
          formAggregates(command);
        }
      };
      return;
    }

    flushWait = command;
      
    IdSet waitingKeys = waitingList.scan();
    if (waitingKeys.numElements() == 0) {
      log(2, "NO BINS TO PACK");
      flushComplete(new Boolean(true));
      return;
    }
  
    log(2, "BIN PACKING STARTED");

    Vector currentAggregate = new Vector();
    Vector aggregates = new Vector();
    Vector deletionVector = new Vector();
    Iterator iter = waitingKeys.getIterator();
    long currentAggregateSize = 0;
    int currentObjectsInAggregate = 0;
    
    while (true) {
      ObjectDescriptor thisObject = null;
      boolean mustAddObject = false;

      if (aggregates.size() >= maxAggregatesPerRun)
        break;
      
      while (iter.hasNext()) {
        Id thisId = (Id) iter.next();
        thisObject = (ObjectDescriptor) waitingList.getMetadata(thisId);
        if (thisObject != null) {
          thisObject = new ObjectDescriptor(
            thisObject.key, thisObject.version, thisObject.currentLifetime,
            thisObject.refreshedLifetime, thisObject.size
          );
          if ((((currentAggregateSize + thisObject.size) <= maxAggregateSize) || currentAggregate.isEmpty()) && (currentObjectsInAggregate < maxObjectsInAggregate)) {
            currentAggregateSize += thisObject.size;
            currentObjectsInAggregate ++;
            currentAggregate.add(thisObject);
          } else {
            mustAddObject = true;
            break;
          }
        } else {
          warn("Metadata in waiting object "+thisId.toStringFull()+" appears to be damaged. Scheduling for deletion...");
          deletionVector.add(thisId);
        }
      }
      
      int numObjectsInAggregate = currentAggregate.size();
      if (numObjectsInAggregate < 1) {
        warn("Waiting list seems to consist entirely of damaged objects -- please remove!");
        flushComplete(new Boolean(true));
        return;
      }

      ObjectDescriptor[] desc = new ObjectDescriptor[numObjectsInAggregate];
      for (int i=0; i<numObjectsInAggregate; i++) {
        desc[i] = (ObjectDescriptor) currentAggregate.elementAt(i);
        log(3, "#"+i+": "+desc[i].key+" "+desc[i].size+" bytes");
      }

      aggregates.add(desc);
      currentAggregate.clear();
      currentObjectsInAggregate = 0;
      currentAggregateSize = 0;
        
      if (mustAddObject) {
        currentAggregate.add(thisObject);
        currentAggregateSize += thisObject.size;
      } else {
        if (!iter.hasNext())
          break;
      }
    }

    Enumeration delenda = deletionVector.elements();
    while (delenda.hasMoreElements()) {
      final Id thisId = (Id) delenda.nextElement();
      log(2, "Deleting object "+thisId.toStringFull()+" from waiting list (broken metadata)");
      waitingList.unstore(thisId, new Continuation() {
        public void receiveResult(Object o) {
          log(3, "Successfully deleted: "+thisId);
        }
        public void receiveException(Exception e) {
          warn("Cannot delete: "+thisId+", e="+e);
          e.printStackTrace();
        }
      });
    }
  
    Continuation.MultiContinuation c = new Continuation.MultiContinuation(new Continuation() {
      public void receiveResult(Object o) {
        flushComplete(new Boolean(true));
      }
      public void receiveException(Exception e) {
        flushComplete(e);
      }
    }, aggregates.size());
      
    for (int i=0; i<aggregates.size(); i++) {
      final ObjectDescriptor[] desc = (ObjectDescriptor[]) aggregates.elementAt(i);
      final GCPastContent[] obj = new GCPastContent[desc.length];
      final long aggrExpirationF = chooseAggregateLifetime(desc, System.currentTimeMillis(), 0);
      final Continuation thisContinuation = c.getSubContinuation(i);
      final int iF = i;
      
      log(3, "Retrieving #"+i+".0: "+desc[0].key);
      waitingList.getObject(new VersionKey(desc[0].key, desc[0].version), new Continuation() {
        int currentQuery = 0;
        public void receiveResult(Object o) {
          if ((o!=null) && (o instanceof GCPastContent)) {
            obj[currentQuery++] = (GCPastContent) o;
            if (currentQuery < desc.length) {
              log(3, "Retrieving #"+iF+"."+currentQuery+": "+desc[currentQuery].key);
              waitingList.getObject(new VersionKey(desc[currentQuery].key, desc[currentQuery].version), this);
            } else {
              Id[] pointers = aggregateList.getSomePointers(nominalReferenceCount, maxPointersPerAggregate, null);
              storeAggregate(new Aggregate(obj, pointers), aggrExpirationF, desc, pointers, new Continuation() {
                public void receiveResult(Object o) {
                  final Continuation.MultiContinuation c2 = new Continuation.MultiContinuation(thisContinuation, desc.length);
                  for (int i=0; i<desc.length; i++) {
                    final Continuation c2s = c2.getSubContinuation(i);
                    waitingList.unstore(new VersionKey(desc[i].key, desc[i].version), new Continuation() {
                      public void receiveResult(Object o) {
                        c2s.receiveResult(o);
                      }
                      public void receiveException(Exception e) {
                        warn("Exception while unstoring aggregate component: "+e);
                        e.printStackTrace();
                        c2s.receiveException(e);
                      }
                    });
                  }
                }
                public void receiveException(Exception e) {
                  warn("Exception while storing new aggregate: "+e);
                  e.printStackTrace();
                  thisContinuation.receiveException(e);
                }
              });
            }
          } else { 
            warn("Aggregation cannot retrieve "+desc[currentQuery].key+" (found o="+o+")");
            thisContinuation.receiveException(new AggregationException("Cannot retrieve object from waiting list: "+desc[currentQuery].key));
          }
        }
        public void receiveException(Exception e) {
          warn("Exception while building aggregate: "+e);
          thisContinuation.receiveException(e);
        }
      });
    }
  }

  private long chooseAggregateLifetime(ObjectDescriptor[] components, long now, long currentLifetime) {
    long maxLifetime = 0;

    for (int i=0; i<components.length; i++)
      if (components[i].refreshedLifetime > maxLifetime)
        maxLifetime = components[i].refreshedLifetime;

    return maxLifetime;
  }

  private void refreshAggregates() {
    Enumeration enum = aggregateList.elements();
    long now = System.currentTimeMillis();
    Vector removeList = new Vector();
    final Vector refreshAggregateList = new Vector();
    final Vector refreshLifetimeList = new Vector();

    log(2, "Checking aggregate lifetimes");

    aggregateList.resetMarkers();
    while (enum.hasMoreElements()) {
      AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
      if (!aggr.marker) {
        aggr.marker = true;
        
        boolean isBeingRefreshed = false;
        if (aggr.currentLifetime < (now + expirationRenewThreshold)) {
          long newLifetime = chooseAggregateLifetime(aggr.objects, now, aggr.currentLifetime);
          if (newLifetime > aggr.currentLifetime) {
            log(2, "Refreshing aggregate "+aggr.key.toStringFull()+", new expiration is "+newLifetime);
            isBeingRefreshed = true;
            
            refreshAggregateList.add(aggr);
            refreshLifetimeList.add(new Long(newLifetime));
          }
        }
            
        if ((aggr.currentLifetime < now) && !isBeingRefreshed) {
          log(3, "Adding expired aggregate "+aggr.key+" to remove list");
          removeList.add(aggr);
        }
      }
    }

    boolean deletedOne = false;
    while (!removeList.isEmpty()) {
      AggregateDescriptor aggr = (AggregateDescriptor) removeList.elementAt(0);
      log(2, "Removing expired aggregate "+aggr.key.toStringFull()+" from list");
      removeList.removeElementAt(0);
      deletedOne = true;
      aggregateList.removeAggregateDescriptor(aggr);
    }
    
    if (deletedOne)
      aggregateList.writeToDisk();

    if (!refreshAggregateList.isEmpty()) {
      log(2, "Refreshing "+refreshAggregateList.size()+" aggregate(s)");
      if (aggregateStore instanceof GCPast) {
        Id[] ids = new Id[refreshAggregateList.size()];
        long[] lifetimes = new long[refreshAggregateList.size()];
        for (int i=0; i<refreshAggregateList.size(); i++) {
          ids[i] = ((AggregateDescriptor) refreshAggregateList.elementAt(i)).key;
          lifetimes[i] = ((Long) refreshLifetimeList.elementAt(i)).longValue();
        }
        
        ((GCPast)aggregateStore).refresh(ids, lifetimes, new Continuation() {
          public void receiveResult(Object o) {
            Object[] results = (Object[]) o;
            log(3, "Received refresh results for "+results.length+" aggregates");
            int numOk = 0;
            
            for (int i=0; i<results.length; i++) {
              if (results[i] instanceof Boolean) {
                AggregateDescriptor aggr = (AggregateDescriptor) refreshAggregateList.elementAt(i);
                long newLifetime = ((Long) refreshLifetimeList.elementAt(i)).longValue();
                log(3, "Aggregate #"+i+" ("+aggr.key.toStringFull()+"): OK, new lifetime is "+newLifetime);
                aggregateList.refreshAggregate(aggr, newLifetime);
                numOk ++;
              } else {
                AggregateDescriptor aggr = (AggregateDescriptor) refreshAggregateList.elementAt(i);
                Exception e = (Exception) results[i];
                warn("Aggregate #"+i+" ("+aggr.key.toStringFull()+"): Refresh failed, e="+e);
                e.printStackTrace();
              }
            }
            
            aggregateList.writeToDisk();
            log(2, "Refresh complete, "+numOk+"/"+results.length+" aggregates refreshed OK");
          }
          public void receiveException(Exception e) {
            warn("Interface contract broken; exception "+e+" returned directly");
            e.printStackTrace();
          }
        });
      } else {
        log(3, "Aggregate store does not support GC; refreshing directly");
        for (int i=0; i<refreshAggregateList.size(); i++) {
          AggregateDescriptor aggr = (AggregateDescriptor) refreshAggregateList.elementAt(i);
          long newLifetime = ((Long) refreshLifetimeList.elementAt(i)).longValue();
          aggregateList.refreshAggregate(aggr, newLifetime);
        }
      }
    }
  }

  private void consolidateAggregates() {
    final long now = System.currentTimeMillis();
    Enumeration enum = aggregateList.elements();
    Vector candidateList = new Vector();

    log(2, "Looking for aggregates to consolidate");
    
    aggregateList.resetMarkers();
    while (enum.hasMoreElements()) {
      AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
      if (!aggr.marker) {
        aggr.marker = true;
        
        /* An aggregate is a candidate for consolidation iff
              - it does not need to be refreshed yet, but has reached a certain age
                (to prevent consolidation of 'garbage'), and
              - it does not contain enough objects, or too many of them are dead
           Pointer arrays are never consolidated; they expire after some time.
        */
        
        if ((aggr.currentLifetime > (now + expirationRenewThreshold)) &&
            (aggr.currentLifetime < (now + consolidationThreshold)) &&
            (aggr.objectsAliveAt(now) > 0)) {
          float fractionAlive = ((float)aggr.objectsAliveAt(now)) / aggr.objects.length;
          if ((aggr.objects.length < consolidationMinObjectsInAggregate) ||
              (fractionAlive < consolidationMinComponentsAlive)) {
            log(3, "Can consolidate: "+aggr.key.toStringFull()+", "+aggr.objectsAliveAt(now)+"/"+aggr.objects.length+" alive");
            candidateList.add(aggr);
          }
        }
      }
    }

    if (candidateList.isEmpty()) {
      log(2, "No candidates for consolidation");
      return;
    }
    
    log(3, candidateList.size() + " candidate(s) for consolidation");
    
    final Vector componentList = new Vector();
    Random rand = new Random();
    int objectsSoFar = 0;
    int bytesSoFar = 0;
    
    while (!candidateList.isEmpty()) {
      AggregateDescriptor adc = (AggregateDescriptor) candidateList.remove(rand.nextInt(candidateList.size()));
      componentList.add(adc);
      log(3, "Picked candidate "+adc.key.toStringFull()+" ("+adc.objectsAliveAt(now)+"/"+adc.objects.length+" objects, "+adc.bytesAliveAt(now)+" bytes alive)");
      objectsSoFar += adc.objectsAliveAt(now);
      bytesSoFar += adc.bytesAliveAt(now);
      
      int p = 0;
      while (p < candidateList.size()) {
        AggregateDescriptor adx = (AggregateDescriptor) candidateList.elementAt(p);
        if (((adx.objectsAliveAt(now) + objectsSoFar) > maxObjectsInAggregate) ||
            ((adx.bytesAliveAt(now) + bytesSoFar) > maxAggregateSize))
          candidateList.removeElementAt(p);
        else
          p++;
      }
    }
    
    if (componentList.isEmpty() || (objectsSoFar < consolidationMinObjectsInAggregate)) {
      log(2, "Not enough objects ("+objectsSoFar+" found, "+consolidationMinObjectsInAggregate+" required), postponing...");
      return;
    }

    log(3, "Consolidation: Decided to consolidate "+objectsSoFar+" objects from "+componentList.size()+" aggregates ("+bytesSoFar+" bytes)");
    
    final AggregateDescriptor[] adc = (AggregateDescriptor[]) componentList.toArray(new AggregateDescriptor[] {});
    final Aggregate[] aggr = new Aggregate[adc.length];
    final int objectsTotal = objectsSoFar;
    final Id firstKey = adc[0].key;

    log(3, "Consolidation: Fetching aggregate #0: "+firstKey.toStringFull());
    aggregateStore.lookup(firstKey, new Continuation() {
      int currentLookup = 0;
      public void receiveResult(Object o) {
        if (o instanceof Aggregate) {
          aggr[currentLookup] = (Aggregate) o;
          currentLookup ++;
          if (currentLookup >= componentList.size()) {
            GCPastContent[] components = new GCPastContent[objectsTotal];
            ObjectDescriptor[] desc = new ObjectDescriptor[objectsTotal];
            int componentIndex = 0;

            log(2, "Consolidation: All aggregates fetched OK, forming new aggregate...");

            for (int i=0; i<adc.length; i++) {
              for (int j=0; j<adc[i].objects.length; j++) {
                if (adc[i].objects[j].isAliveAt(now)) {
                  components[componentIndex] = aggr[i].components[j];
                  desc[componentIndex] = adc[i].objects[j];
                  log(3, "  #"+componentIndex+": "+adc[i].objects[j].key.toStringFull());
                  componentIndex ++;
                } else {
                  log(3, "Skipped (dead): "+adc[i].objects[j].key.toStringFull());
                }
              }
            }

            Id[] obsoleteAggregates = new Id[adc.length];
            for (int i=0; i<adc.length; i++)
              obsoleteAggregates[i] = adc[i].key;

            Id[] pointers = aggregateList.getSomePointers(nominalReferenceCount, maxPointersPerAggregate, obsoleteAggregates);
            final long aggrExpirationF = chooseAggregateLifetime(desc, System.currentTimeMillis(), 0);

            storeAggregate(new Aggregate(components, pointers), aggrExpirationF, desc, pointers, new Continuation() {
              public void receiveResult(Object o) {
                log(2, "Consolidated Aggregate stored OK, removing old descriptors...");
                for (int i=0; i<adc.length; i++) {
                  log(3, "Removing "+adc[i].key.toStringFull()+" ...");
                  aggregateList.removeAggregateDescriptor(adc[i]);
                }
                
                aggregateList.writeToDisk();
                log(2, "Consolidation completed, "+objectsTotal+" objects from "+aggr.length+" aggregates consolidated");
              }
              public void receiveException(Exception e) {
                warn("Exception during consolidation store: e="+e+" -- aborting");
                e.printStackTrace();
              }
            });
          } else {
            log(3, "Consolidation: Fetching aggregate #"+currentLookup+": "+adc[currentLookup].key.toStringFull());
            aggregateStore.lookup(adc[currentLookup].key, this);
          }
        }
      }
      public void receiveException(Exception e) {
        warn("Exception during consolidation lookup "+adc[currentLookup].key.toStringFull()+": "+e+" -- aborting");
        e.printStackTrace();
      }
    });
  }

  private void reconnectTree() {
  
    if (rebuildInProgress) {
      log(2, "Skipping connectivity check (rebuild in progress)");
      return;
    }
  
    log(2, "Checking for disconnections");
    
    Id[] disconnected = aggregateList.getSomePointers(1, maxPointersPerAggregate, null);
    if (disconnected.length < 2) {
      Id newRoot = (disconnected.length == 1) ? disconnected[0] : null;
      Id currentRoot = aggregateList.getRoot();
      if (((newRoot == null) && (currentRoot != null)) || ((newRoot != null) && (currentRoot == null)) || 
          ((newRoot != null) && (currentRoot != null) && !newRoot.equals(currentRoot)))
        aggregateList.setRoot(newRoot);
      log(2, "No aggregates disconnected (n="+disconnected.length+")");
      log(3, "root="+((aggregateList.getRoot() == null) ? "null" : aggregateList.getRoot().toStringFull()));
      return;
    }
    
    log(2, "Found "+disconnected.length+" disconnected aggregates; inserting pointer array");
    storeAggregate(
      new Aggregate(new GCPastContent[] {}, disconnected), 
      System.currentTimeMillis() + pointerArrayLifetime,
      new ObjectDescriptor[] {},
      disconnected,
      new Continuation() {
        public void receiveResult(Object o) {
          log(3, "Successfully inserted pointer array");
        }
        public void receiveException(Exception e) {
          warn("Error while inserting pointer array: "+e);
          e.printStackTrace();
        }
      }
    );
  }

  private void timerExpired(char timerID) {
    log(3, "TIMER EXPIRED: #" + (int) timerID);

    switch (timerID) {
      case tiFlush :
      {
        log(2, "Scheduled flush, waiting list: "+waitingList.getSize());
        
        formAggregates(new Continuation() {
          public void receiveResult(Object o) { 
            log(3, "Scheduled flush: Success (o="+o+")");
          }
          public void receiveException(Exception e) {
            warn("Scheduled flush: Failure (e="+e+")");
            e.printStackTrace();
          }
        });
        
        log(2, "Waiting list: "+waitingList.getSize()+" Scan: "+getNumObjectsWaiting()+" Max: "+(maxObjectsInAggregate * maxAggregatesPerRun));
        
        if (getNumObjectsWaiting() >= (maxObjectsInAggregate * maxAggregatesPerRun)) {
          log(2, "Retrying later");
          addTimer(jitterTerm(flushStressInterval), tiFlush);
        } else {
          log(2, "OK, waiting for next deadline");
          addTimer(jitterTerm(flushInterval), tiFlush);
        }
        
        break;
      }
      case tiExpire :
      {
        refreshAggregates();
        reconnectTree();
        addTimer(jitterTerm(aggrRefreshInterval), tiExpire);
        break;
      }
      case tiConsolidate :
      {
        consolidateAggregates();
        addTimer(jitterTerm(consolidationInterval), tiConsolidate);
        break;
      }
      case tiMonitor :
      {
        Id[] ids = (Id[]) monitorIDs.toArray(new Id[] {});
        log(2, "Monitor: Refreshing "+ids.length+" objects");
        refresh(ids, System.currentTimeMillis() + 3 * monitorRefreshInterval, new Continuation() {
          public void receiveResult(Object o) {
            log(3, "Monitor: Refresh completed, result="+o);
          }
          public void receiveException(Exception e) {
            log(3, "Monitor: Refresh failed, exception="+e);
            e.printStackTrace();
          }
        });
      
        addTimer(monitorRefreshInterval, tiMonitor);
        break;
      }
      case tiStatistics :
      {
        stats = aggregateList.getStatistics(statsGranularity, statsRange, nominalReferenceCount);
        stats.dump();
        addTimer(statsInterval, tiStatistics);
        break;
      }
      default:
      {
        panic("Unknown timer expired: " + (int) timerID);
      }
    }
  }

  private void refreshInObjectStore(Id[] ids, long[] expirations, Continuation command) {
    if (objectStore instanceof GCPast) {
      ((GCPast)objectStore).refresh(ids, expirations, command);
    } else {
      command.receiveResult(new Boolean(true));
    }
  }
  
  public void refresh(Id[] ids, long expiration, Continuation command) {
    long[] expirations = new long[ids.length];
    Arrays.fill(expirations, expiration);
    refresh(ids, expirations, command);
  }
  
  public void refresh(final Id[] ids, final long[] expirations, final Continuation command) {
    if (ids.length < 1) {
      command.receiveResult(new Boolean[] {});
      return;
    }

    log(2, "Refreshing "+ids.length+" keys");

    refreshInObjectStore(ids, expirations, new Continuation() {
      Object[] result;
      
      public void receiveResult(Object o) {
        if (o instanceof Object[]) {
          result = (Object[]) o;
        } else {
          warn("refresh: ObjectStore result is of incorrect type; expected Object[], got "+o);
          result = new Object[ids.length];
          for (int i=0; i<ids.length; i++)
            result[i] = o;
        }

        refreshInAggregates();
      }
      public void receiveException(Exception e) {
        result = new Object[ids.length];
        for (int i=0; i<ids.length; i++)
          result[i] = e;
        e.printStackTrace();
        refreshInAggregates();
      }
      private void refreshInAggregates() {
        Continuation c = new Continuation() {
          public void receiveResult(Object o) {
            aggregateList.writeToDisk(); 
            command.receiveResult(o);
          }
          public void receiveException(Exception e) {
            e.printStackTrace();
            command.receiveException(e);
          }
        };

        refreshInternal(ids, expirations, result, c);
      }
    });
  }
  
  public void refresh(final Id[] ids, final long[] versions, final long[] expirations, final Continuation command) {
    final Object result[] = new Object[ids.length];
    
    for (int i=0; i<ids.length; i++) {
      log(2, "Refresh("+ids[i]+"v"+versions[i]+", expiration="+expirations[i]+")");

      AggregateDescriptor adc = (AggregateDescriptor) aggregateList.getADC(new VersionKey(ids[i], versions[i]));
      if (adc!=null) {
        int objDescIndex = adc.lookupSpecific(ids[i], versions[i]);
        if (objDescIndex < 0) {
          result[i] = new AggregationException("Inconsistency detected in aggregate list -- try restarting the application");
        } else {
          if (adc.objects[objDescIndex].refreshedLifetime < expirations[i])
            aggregateList.setObjectRefreshedLifetime(adc, objDescIndex, expirations[i]);

          result[i] = new Boolean(true);
        }
      } else result[i] = new AggregationException("Not found");
    }
      
    if (objectStore instanceof VersioningPast) {
      ((VersioningPast)objectStore).refresh(ids, versions, expirations, new Continuation() {
        public void receiveResult(Object o) {
          if (o instanceof Object[]) {
            Object[] subresult = (Object[]) o;
            for (int i=0; i<result.length; i++)
              if ((result[i] instanceof Boolean) && !(subresult[i] instanceof Boolean))
                result[i] = subresult[i];
          } else {
            Exception e = new AggregationException("Object store returns unexpected result: "+o);
            for (int i=0; i<result.length; i++)
              result[i] = e;
          }
          
          command.receiveResult(result);
        }
        public void receiveException(Exception e) {
          command.receiveException(e);
        }
      });
    } else {
      command.receiveResult(result);
    }
  }

  private void refreshInternal(final Id[] ids, final long[] expirations, final Object[] result, final Continuation command) {
    log(2, "refreshInternal: Accepted "+ids.length+" keys, starting with first key...");
    
    final Continuation theContinuation = new Continuation() {
      int objectsMissing = 0;
      int objectsFetched = 0;
      int currentIndex = -1;

      public void receiveResult(Object o) {
        if (currentIndex >= 0) {
          log(3, "receiveResult("+o+") for index "+currentIndex+", length="+ids.length);
          log(3, "Internal refresh of "+ids[currentIndex].toStringFull()+" returned "+o);
          result[currentIndex] = o;
        }

        currentIndex ++;
        if (currentIndex >= ids.length) {
          if (objectsMissing > 0) 
            warn("refresh: "+objectsMissing+"/"+ids.length+" objects not in aggregate list, fetched "+objectsFetched+" (max "+maxReaggregationPerRefresh+")");
            
          int nOK = 0;
          for (int i=0; i<ids.length; i++)
            if (result[i] instanceof Boolean)
              nOK ++;
              
          log(2, "refreshInternal: Processed "+ids.length+" keys, completed "+nOK);
          if (loglevel>3)
            for (int i=0; i<ids.length; i++)
              log(4, " - "+ids[i].toStringFull()+": "+result[i]);
            
          command.receiveResult(result);
          return;
        }
        
        final Id id = ids[currentIndex];
        final long expiration = expirations[currentIndex];
        log(2, "Refresh("+id.toStringFull()+", expiration="+expiration+") started");
        
        /* In the common case, the aggregate is in the list, and we can simply update its lifetime there */
        
        AggregateDescriptor adc = (AggregateDescriptor) aggregateList.getADC(id);
        if (adc!=null) {
          int objDescIndex = adc.lookupNewest(id);
          if (objDescIndex < 0) {
            warn("NL: Aggregate found, but object not found in aggregate?!? -- aborted");
            command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
            return;
          }

          if (adc.objects[objDescIndex].refreshedLifetime < expiration) {
            log(3, "Changing expiration date from "+adc.objects[objDescIndex].refreshedLifetime+" to "+expiration);
            aggregateList.setObjectRefreshedLifetime(adc, objDescIndex, expiration);
          } else {
            log(3, "Expiration is "+adc.objects[objDescIndex].refreshedLifetime+" already, no update needed");
          }

          receiveResult(new Boolean(true));
          return;
        } else {

          /* If the aggregate is not in the list, check whether it is still in the waiting list */
        
          boolean foundWaiting = false;
          IdSet waitingIds = waitingList.scan();
          Iterator iter = waitingIds.getIterator();
      
          while (iter.hasNext()) {
            final VersionKey vkey = (VersionKey) iter.next();
            if (vkey.getId().equals(id)) {
              ObjectDescriptor thisObject = (ObjectDescriptor) waitingList.getMetadata(vkey);
              log(2, "Refreshing in waiting list: " + vkey.toStringFull());
              foundWaiting = true;
          
              if (thisObject == null) {
                warn("Broken object in waiting list: " + vkey.toStringFull() + ", removing...");
                final Continuation myParent = this;
                waitingList.unstore(vkey, new Continuation() {
                  public void receiveResult(Object o) {
                    log(2, "Broken object "+vkey.toStringFull()+" removed successfully");
                    myParent.receiveResult(new AggregationException("Object in waiting list, but broken: "+vkey.toStringFull()));
                  }
                  public void receiveException(Exception e) {
                    warn("Cannot remove broken object "+vkey.toStringFull()+" from waiting list (exception: "+e+")");
                    e.printStackTrace();
                    myParent.receiveResult(new AggregationException("Object broken, in waiting list, and cannot remove: "+vkey.toStringFull()+" (e="+e+")"));
                  }
                });
                return;
              }

              if (thisObject.refreshedLifetime < expiration) {
                ObjectDescriptor newDescriptor = new ObjectDescriptor(
                  thisObject.key, thisObject.version, thisObject.currentLifetime, 
                  expiration, thisObject.size
                );

                final Continuation myParent = this;
                waitingList.setMetadata(vkey, newDescriptor, new Continuation() {
                  public void receiveResult(Object o) {
                    log(3, "Refreshed metadata written ok for "+vkey.toStringFull());
                    myParent.receiveResult(new Boolean(true));
                  }
                  public void receiveException(Exception e) {
                    warn("Cannot refresh waiting object "+vkey.toStringFull()+", e="+e);
                    e.printStackTrace();
                    myParent.receiveResult(new AggregationException("Cannot refresh waiting object "+vkey.toStringFull()+", setMetadata() failed (e="+e+")"));
                  }
                });
              } else {
                log(3, "Object found in waiting list and no update needed: "+vkey.toStringFull());
                receiveResult(new Boolean(true));
                return;
              }
            }
          }
          
          /* If the object is neither in the aggregate list nor in the waiting list, there must have
             been a failure, and we need to reaggregate this object. */
    
          objectsMissing ++;
          if (addMissingAfterRefresh) {
            if (objectsFetched < maxReaggregationPerRefresh) {
              objectsFetched ++;
              final Continuation myParent = this;
              objectStore.lookup(id, false, new Continuation() {
                public void receiveResult(Object o) {
                  if (o instanceof PastContent) {
                    final PastContent obj = (PastContent) o;
                    warn("Refresh: Found in PAST, but not in aggregate list: "+id.toStringFull());
            
                    long theVersion;
                    if (o instanceof GCPastContent) {
                      theVersion = ((GCPastContent)obj).getVersion();
                    } else {
                      theVersion = 0;
                    } 

                    final VersionKey vkey = new VersionKey(obj.getId(), theVersion);
                    final long theVersionF = theVersion;
                    final int theSize = getSize(obj);

                    if (policy.shouldBeAggregated(obj, theSize)) {
                      if (!waitingList.exists(vkey)) {
                        log(3, "ADDING MISSING OBJECT AFTER REFRESH: "+obj.getId());

                        waitingList.store(vkey, new ObjectDescriptor(obj.getId(), theVersionF, expiration, expiration, theSize), obj, new Continuation() {
                          public void receiveResult(Object o) {
                            ((PastImpl) objectStore).cache(obj, new Continuation() {
                              public void receiveResult(Object o) {
                                log(3, "Refresh: Missing object "+id.toStringFull()+" added ok");
                                myParent.receiveResult(new Boolean(true));
                              }
                              public void receiveException(Exception e) {
                                warn("Refresh: Exception while precaching object: "+id.toStringFull()+" (e="+e+")");
                                e.printStackTrace();
                                myParent.receiveResult(new Boolean(true));
                              }
                            });
                          }
                          public void receiveException(Exception e) { 
                            warn("Refresh: Exception while refreshing aggregate: "+id.toStringFull()+" (e="+e+")");
                            e.printStackTrace();
                            myParent.receiveResult(new AggregationException("Cannot store reaggregated object in waiting list: "+id.toStringFull()));
                          }
                        });

                        return;
                      } else {
                        log(3, "Refresh: Missing object already in waiting list: "+id.toStringFull());
                        myParent.receiveResult(new Boolean(true));
                        return;
                      }
                    }
                    
                    log(3, "Refresh: Missing object should not be aggregated: "+id.toStringFull());
                    myParent.receiveResult(new Boolean(true));
                    return;
                  } else {
                    warn("Refresh: Cannot find refreshed object "+id.toStringFull()+", lookup returns "+o);
                    myParent.receiveException(new AggregationException("Object not found during reaggregation: "+id.toStringFull()));
                  }
                }
                public void receiveException(Exception e) {
                  warn("Refresh: Exception received while reaggregating "+id.toStringFull()+", e="+e);
                  myParent.receiveException(e);
                }
              });
              return;
            } else {
              log(3, "Refresh: Limit of "+maxReaggregationPerRefresh+" reaggregations exceeded; postponing id="+id.toStringFull());
              receiveResult(new Boolean(true));
              return;
            }
          } else {
            warn("Refresh: Refreshed object not found in any aggregate: "+id.toStringFull());
            receiveResult(new Boolean(true));
            return;
          }
        }
        
        /* This should NEVER be reached */
      }
      public void receiveException(Exception e) {
        warn("Exception while refreshing "+ids[currentIndex].toStringFull()+", e="+e);
        e.printStackTrace();
        receiveResult(e);
      }
    };
    
    theContinuation.receiveResult(null);
  }

  private int getSize(PastContent obj) {
    try {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);

      objectStream.writeObject(obj);
      objectStream.flush();

      return byteStream.toByteArray().length;
    } catch (IOException ioe) {
      warn("Cannot serialize object, size unknown: "+ioe);
    }
    
    return 0;
  }

  public Serializable getHandle() {
    return aggregateList.getRoot();
  }
  
  public void setHandle(Serializable handle, Continuation command) {
    log(2, "setHandle("+handle+")");
  
    if (!(handle instanceof Id)) {
      command.receiveException(new AggregationException("Illegal handle"));
      return;
    }
    
    if (aggregateList.getADC((Id) handle) != null) {
      log(2, "Rebuild: Handle "+handle+" is already covered by current root");
      command.receiveResult(new Boolean(true));
    }
      
    aggregateList.setRoot((Id) handle);
    rebuildAggregateList(command);
  }

  private void rebuildAggregateList(final Continuation command) {
    log(2, "rebuildAggregateList");
    if (aggregateList.getRoot() == null) {
      warn("rebuildAggregateList invoked while rootKey is null");
      command.receiveException(new AggregationException("Set handle first!"));
      return;
    }
    
    final Vector keysToDo = new Vector();
    final Vector keysDone = new Vector();
    keysToDo.add(aggregateList.getRoot());
    rebuildInProgress = true;
    
    log(2, "Rebuild: Fetching handles for aggregate " + aggregateList.getRoot().toStringFull());
    aggregateStore.lookupHandles(aggregateList.getRoot(), 999, new Continuation() {
      Id currentLookup = aggregateList.getRoot();
      
      public void receiveResult(Object o) {
        log(3, "Got handles for "+currentLookup);

        if (o instanceof PastContentHandle[]) {
          PastContentHandle[] pch = (PastContentHandle[]) o;
          PastContentHandle bestHandle = null;

          for (int i=0; i<pch.length; i++) {
            if ((pch[i] == null) || ((pch[i] instanceof GCPastContentHandle) && (((GCPastContentHandle)pch[i]).getVersion() != 0)))
              continue;
              
            if (bestHandle == null)
              bestHandle = pch[i];
          }
          
          if (bestHandle != null) {
            final PastContentHandle thisHandle = bestHandle;
            final Continuation outerContinuation = this;

            log(3, "Fetching "+thisHandle);
            aggregateStore.fetch(thisHandle, new Continuation() {
              public void receiveResult(Object o) {
                if (o instanceof Aggregate) {
                  keysToDo.remove(currentLookup);
                  keysDone.add(currentLookup);

                  log(2, "Rebuild: Got aggregate " + currentLookup.toStringFull());

                  Aggregate aggr = (Aggregate) o;
                  ObjectDescriptor[] objects = new ObjectDescriptor[aggr.components.length];
                  long aggregateExpiration = (thisHandle instanceof GCPastContentHandle) ? ((GCPastContentHandle)thisHandle).getExpiration() : GCPast.INFINITY_EXPIRATION;
          
                  for (int i=0; i<aggr.components.length; i++) {
                    objects[i] = new ObjectDescriptor(
                      aggr.components[i].getId(), 
                      aggr.components[i].getVersion(),
                      aggregateExpiration,
                      aggregateExpiration,
                      getSize(aggr.components[i])
                    );
                    
                    final GCPastContent objData = aggr.components[i];
                    log(3, "Checking whether "+objData.getId()+"v"+objData.getVersion()+" is in object store...");
                    objectStore.lookupHandles(objData.getId(), 1, new Continuation() {
                      public void receiveResult(Object o) {
/* evil... needs pastcontenthandle */                      
                        GCPastContentHandle[] result = (o instanceof GCPastContentHandle[]) ? ((GCPastContentHandle[])o) : new GCPastContentHandle[] {};
                        log(3, "Handles for "+objData.getId()+"v"+objData.getVersion()+": "+result);
                        boolean gotOne = false;
                        for (int i=0; i<result.length; i++) {
                          log(3, "Have v"+result[i].getVersion());
                          if (result[i].getVersion() >= objData.getVersion())
                            gotOne = true;
                        }
                        
                        if (gotOne) {
                          log(3, "Got it");
                        } else {
                          log(3, "Ain't got it... reinserting");
                          objectStore.insert(objData, new Continuation() {
                            public void receiveResult(Object o) {
                              log(3, "Reinsert "+objData.getId()+"v"+objData.getVersion()+" ok, result="+o);
                            }
                            public void receiveException(Exception e) {
                              log(3, "Reinsert "+objData.getId()+"v"+objData.getVersion()+" failed, exception="+e);
                              e.printStackTrace();
                            }
                          });
                        }
                      }
                      public void receiveException(Exception e) {
                        log(3, "Cannot retrieve handles for object "+objData.getId()+"v"+objData.getVersion()+" to be restored; e="+e);
                        e.printStackTrace();
                      }
                    });
                  }
            
                  aggregateList.addAggregateDescriptor(new AggregateDescriptor(currentLookup, aggregateExpiration, objects, aggr.getPointers()));
          
                  Id[] pointers = aggr.getPointers();
                  if (pointers != null) {
                    for (int i=0; i<pointers.length; i++) {
                      if (pointers[i] instanceof Id) {
                        Id thisPointer = pointers[i];
                        if (!keysDone.contains(thisPointer) && !keysToDo.contains(thisPointer))
                          keysToDo.add(thisPointer);
                      }
                    }
                  }
          
                  if (!keysToDo.isEmpty()) {
                    log(2, "Rebuild: "+keysToDo.size()+" keys to go, "+keysDone.size()+" done");
                    currentLookup = (Id) keysToDo.firstElement();
                    log(2, "Rebuild: Fetching handles for aggregate " + currentLookup.toStringFull());
                    aggregateStore.lookupHandles(currentLookup, 999, outerContinuation);
                  } else {
                    aggregateList.writeToDisk();
                    rebuildInProgress = false;
                    log(2, "Rebuild: Completed; "+keysDone.size()+" aggregates checked");
                    command.receiveResult(new Boolean(true));
                  }
                } else {
                  receiveException(new AggregationException("Fetch failed: "+currentLookup+", returned "+o));
                }
              }
              public void receiveException(Exception e) {
                outerContinuation.receiveException(e);
              }
            });
          } else {
            receiveException(new AggregationException("LookupHandles did not return any valid handles for "+currentLookup));
          }
        } else {
          receiveException(new AggregationException("LookupHandles for "+currentLookup+" failed, returned o="+o));
        }
      }
      public void receiveException(Exception e) {
        warn("Rebuild: Exception "+e);
        e.printStackTrace();
        keysToDo.remove(currentLookup);
        keysDone.add(currentLookup);
        
        if (!keysToDo.isEmpty()) {
          log(3, "Trying next key");
          currentLookup = (Id) keysToDo.firstElement();
          log(2, "Rebuild: Fetching handles for aggregate " + currentLookup.toStringFull());
          aggregateStore.lookupHandles(currentLookup, 999, this);
        } else {
          if (aggregateList.isEmpty()) {
            rebuildInProgress = false;
            command.receiveException(new AggregationException("Cannot read root aggregate! -- retry later"));
          } else {
            aggregateList.writeToDisk();
            rebuildInProgress = false;
            command.receiveResult(new Boolean(true));
          }
        }
      }
    });
  }
  
  public void insert(final PastContent obj, final Continuation command) {
    insert(obj, INFINITY_EXPIRATION, command);
  }

  public void insert(final PastContent obj, final long lifetime, final Continuation command) {

    long theVersion;
    if (obj instanceof GCPastContent) {
      theVersion = ((GCPastContent)obj).getVersion();
    } else {
      theVersion = 0;
    }

    final VersionKey vkey = new VersionKey(obj.getId(), theVersion);
    final long theVersionF = theVersion;
    final int theSize = getSize(obj);

    if (policy.shouldBeAggregated(obj, theSize)) {
      log(2, "AGGREGATE INSERT: "+obj.getId()+" version="+theVersion+" size="+theSize+" class="+obj.getClass().getName());

      if (objectStore instanceof GCPast)
        ((GCPast)objectStore).insert(obj, lifetime, command);
      else 
        objectStore.insert(obj, command);
        
      waitingList.store(vkey, new ObjectDescriptor(obj.getId(), theVersionF, lifetime, lifetime, theSize), obj, new Continuation() {
        public void receiveResult(Object o) {
        }
        public void receiveException(Exception e) { 
          warn("Exception while storing aggregate: "+obj.getId()+" (e="+e+")");
          e.printStackTrace();
        }
      });
    } else {
      log(2, "INSERT WITHOUT AGGREGATION: "+obj.getId()+" version="+theVersionF+" size="+theSize+" class="+obj.getClass().getName());
      
      Continuation c = new Continuation() {
        boolean otherSucceeded = false;
        boolean otherFailed = false;

        public void receiveResult(Object o) {
          log(3, "INSERT "+obj.getId()+" receiveResult("+o+"), otherSucc="+otherSucceeded+" otherFail="+otherFailed);
          if (otherSucceeded) {
            if (!otherFailed) {
              log(3, "--reporting Success");
              command.receiveResult(new Boolean[] { new Boolean(true) });
            }
          } else {
            otherSucceeded = true;
          }
        }
        public void receiveException(Exception e) {
          log(3, "INSERT "+obj.getId()+" receiveException("+e+"), otherSucc="+otherSucceeded+" otherFail="+otherFailed);
          log(3, "--reporting Failure");
          command.receiveException(e);
          otherFailed = true;
        }
      };
      
      if (objectStore instanceof GCPast) 
        ((GCPast)objectStore).insert(obj, lifetime, c);
      else 
        objectStore.insert(obj, c);
        
      if (aggregateStore instanceof GCPast)
        ((GCPast)aggregateStore).insert(obj, lifetime, c);
      else
        aggregateStore.insert(obj, c);
    }
  }
  
  private void retrieveObjectFromAggregate(final AggregateDescriptor adc, final int objDescIndex, final Continuation command) {
    aggregateStore.lookup(adc.key, new Continuation() {
      public void receiveResult(Object o) {
        if (o instanceof Aggregate) {
          final Aggregate aggr = (Aggregate) o;
          endpoint.process(new Executable() {
            public Object execute() {
              return factory.buildId(aggr.getContentHash());
            }
          }, new Continuation() {
            public void receiveResult(Object o) {
              if (o instanceof Id) {
                Id aggrNominalKey = (Id) o;

                if (!aggrNominalKey.equals(adc.key)) {
                  warn("Cannot validate aggregate "+adc.key+", hash="+aggrNominalKey);
                  command.receiveException(new AggregationException("Cannot validate aggregate -- retry?"));
                  return;
                }
            
                log(3, "Object "+adc.objects[objDescIndex].key+" (#"+objDescIndex+") successfully retrieved from "+adc.key);

                objectStore.insert(aggr.getComponent(objDescIndex), new Continuation() {
                  public void receiveResult(Object o) {}
                  public void receiveException(Exception e) {}
                });

                command.receiveResult(aggr.getComponent(objDescIndex));
              } else {
                warn("retrieveObjectFromAggregate cannot determine content hash, received "+o);
                command.receiveException(new AggregationException("retrieveObjectFromAggregate cannot determine content hash"));
              }
            }
            public void receiveException(Exception e) {
              warn("retrieveObjectFromAggregate cannot determine content hash, exception "+e);
              e.printStackTrace();
              command.receiveException(e);
            }
          });
        } else {
          warn("retrieveObjectFromAggregate failed; receiveResult("+o+")");
          command.receiveResult(null);
        }
      }
      public void receiveException(Exception e) {
        warn("retrieveObjectFromAggregate failed; receiveException("+e+")");
        e.printStackTrace();
        command.receiveException(e);
      }
    });
  }
  
  public void lookup(final Id id, boolean cache, final Continuation command) {
    log(2, "lookup("+id+", cache="+cache+")");
    
    objectStore.lookup(id, cache, new Continuation() {
      public void receiveResult(Object o) {
        if (o != null) {
          log(3, "NL: Found in PAST: "+id);
          command.receiveResult(o);
        } else {
          AggregateDescriptor adc = (AggregateDescriptor) aggregateList.getADC(id);
          if (adc!=null) {
            log(3, "NL: Must retrieve from aggregate");

            int objDescIndex = adc.lookupNewest(id);
            if (objDescIndex < 0) {
              warn("NL: Aggregate found, but object not found in aggregate?!? -- aborted");
              command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
              return;
            }
        
            retrieveObjectFromAggregate(adc, objDescIndex, command);

          } else {
            warn("NL: LOOKUP FAILED, OBJECT NOT FOUND: "+id);
            command.receiveResult(null);
          }
        }
      }
      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    });
  }

  public void lookup(final Id id, final long version, final Continuation command) {
    log(2, "lookup("+id+", version="+version+")");

    AggregateDescriptor adc = (AggregateDescriptor) aggregateList.getADC(new VersionKey(id, version));
    if (adc!=null) {
      log(3, "VL: Retrieving from aggregate");

      int objDescIndex = adc.lookupSpecific(id, version);
      if (objDescIndex < 0) {
        warn("VL: Aggregate found, but object not found in aggregate?!? -- aborted");
        command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
        return;
      }
        
      retrieveObjectFromAggregate(adc, objDescIndex, command);
    } else {
      log(3, "VL: Not found in aggregate list: "+id+"v"+version);
      if (aggregateStore instanceof VersioningPast) {
        VersioningPast vaggr = (VersioningPast) aggregateStore;
        vaggr.lookup(id, version, new Continuation() {
          public void receiveResult(Object o) {
            if (o != null) {
              log(3, "VL: Found in Aggregate.VersioningPAST: "+id+"v"+version);
              command.receiveResult(o);
            } else {
              log(3, "VL: Not found in Aggregate.VersioningPAST: "+id+"v"+version);
              if (objectStore instanceof VersioningPast) {
                VersioningPast vpast = (VersioningPast) objectStore;
                vpast.lookup(id, version, new Continuation() {
                  public void receiveResult(Object o) {
                    if (o != null) {
                      log(3, "VL: Found in Object.VersioningPAST: "+id+"v"+version);
                      command.receiveResult(o);
                    } else {
                      warn("VL: LOOKUP FAILED, OBJECT NOT FOUND: "+id+"v"+version);
                      command.receiveResult(null);
                    }
                  }
                  public void receiveException(Exception e) {
                    command.receiveException(e);
                  }
                });
              } else {
                log(3, "VL: Object store does not support versioning");
                command.receiveException(new AggregationException("Cannot find "+id+"v"+version+" -- try rebuilding aggregate list?"));
              }   
            }
          }
          public void receiveException(Exception e) {
            command.receiveException(new AggregationException("Aggregate.VersioningPAST returned exception for "+id+"v"+version+": "+e));
            e.printStackTrace();
          }
        });
      } else {
        log(3, "VL: Aggregate store does not support versioning");
        if (objectStore instanceof VersioningPast) {
          VersioningPast vpast = (VersioningPast) objectStore;
          vpast.lookup(id, version, new Continuation() {
            public void receiveResult(Object o) {
              if (o != null) {
                log(3, "VL: Found in Object.VersioningPAST: "+id+"v"+version);
                command.receiveResult(o);
              } else {
                warn("VL: LOOKUP FAILED, OBJECT NOT FOUND: "+id+"v"+version);
                command.receiveResult(null);
              }
            }
            public void receiveException(Exception e) {
              command.receiveException(e);
            }
          });
        }

        log(3, "VL: Object store does not support versioning");
        command.receiveResult(null);
      }
    }
  }
  
  public void lookup(Id id, Continuation command) {
    lookup(id, true, command);
  }
  
  public void lookupHandles(final Id id, final long version, final int max, final Continuation command) {
    ((VersioningPast)aggregateStore).lookupHandles(id, version, max, command);
  }
  
  public void lookupHandle(final Id id, final NodeHandle handle, final Continuation command) {
    command.receiveException(new UnsupportedOperationException("LookupHandle() is not supported on Aggregation"));
  }
  
  public void lookupHandles(final Id id, final int max, final Continuation command) {
    log(2, "lookupHandles("+id+","+max+")");
    objectStore.lookupHandles(id, max, new Continuation() {
      public void receiveResult(Object o) {
        PastContentHandle[] result = (o instanceof PastContentHandle[]) ? ((PastContentHandle[])o) : new PastContentHandle[] {};
        boolean foundHandle = false;
        
        for (int i=0; i<result.length; i++)
          if (result[i] != null)
            foundHandle = true;

        if (foundHandle) {
          log(3, "lookupHandles("+id+","+max+") handled by PAST; ret="+o);
          command.receiveResult(o);
        } else {
          log(2, "lookupHandles("+id+","+max+") failed, ret="+o);

          AggregateDescriptor adc = (AggregateDescriptor) aggregateList.getADC(id);
          if (adc!=null) {
            log(3, "lookupHandles: Retrieving from aggregate");

            int objDescIndex = adc.lookupNewest(id);
            if (objDescIndex < 0) {
              warn("lookupHandles: Aggregate found, but object not found in aggregate?!? -- aborted");
              command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
              return;
            }
          
            if (adc.objects[objDescIndex].refreshedLifetime < System.currentTimeMillis()) {
              log(3, "Object "+id+" exists, but has expired -- ignoring");
              command.receiveResult(new PastContentHandle[] { null });
              return;
            }
        
            retrieveObjectFromAggregate(adc, objDescIndex, new Continuation() {
              public void receiveResult(Object o) {
                log(3, "lookupHandles: Retrieved from aggregate: "+id+", result="+o);
                /* re-inserted implicitly by retrieveObjectFromAggregate */
                objectStore.lookupHandles(id, max, command);
              }
              public void receiveException(Exception e) {
                warn("lookupHandles: Cannot retrieve from aggregate, exception "+e);
                command.receiveException(e);
              }
            });
          } else {
            log(2, "lookupHandles: "+id+" is neither in object store nor in aggregate list");
            /* Note that we have to give up here... even if the object has not been 
               aggregated, there is no efficient way to find out its version number.
               The user must call lookupVersion in this case. */
            command.receiveResult(new PastContentHandle[] { null });
          }
        }
      }
      public void receiveException(Exception e) {
        warn("Exception in lookupHandles: "+e);
        command.receiveException(e);
      }
    });
  }
  
  public void fetch(PastContentHandle handle, Continuation command) {

    /* Note that we never give out any handles from the aggregate store, so this must
       be an object store handle */
    if (handle instanceof GlacierContentHandle)
      aggregateStore.fetch(handle, command);
    else
      objectStore.fetch(handle, command);
  }

  public void flush(Id id, Continuation command) {
    Iterator iter = waitingList.scan().getIterator();
    boolean objectIsWaiting = false;

    log(2, "flush("+id+") invoked");
    
    while (iter.hasNext()) {
      VersionKey thisKey = (VersionKey) iter.next();
      if (thisKey.getId().equals(id)) {
        objectIsWaiting = true;
        break;
      }
    }
    
    if (objectIsWaiting)
      formAggregates(command);
    else
      command.receiveResult(new Boolean(true));
  }
  
  public void flush(final Continuation command) {
    formAggregates(command);
  }
  
  public void rollback(Id id, Continuation command) {
    AggregateDescriptor adc = (AggregateDescriptor) aggregateList.getADC(id);

    if (adc!=null) {
      int objDescIndex = adc.lookupNewest(id);
      if (objDescIndex < 0) {
        warn("Rollback: Aggregate found, but object not found in aggregate?!? -- aborted");
        command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
        return;
      }
      
      log(3, "Rollback: Found "+adc.objects[objDescIndex].key+"v"+adc.objects[objDescIndex].version);
      retrieveObjectFromAggregate(adc, objDescIndex, command);
    }

    log(3, "Rollback: No version of "+id+" found");    
    command.receiveResult(null);
  }
  
  public void reset(Continuation command) {
    aggregateList.clear();

    Iterator iter = waitingList.scan().getIterator();
    while (iter.hasNext()) {
      VersionKey thisKey = (VersionKey) iter.next();
      waitingList.unstore(thisKey, new Continuation() {
        public void receiveResult(Object o) {}
        public void receiveException(Exception e) {}
      });
    }

    command.receiveResult(new Boolean(true));
  }

  public NodeHandle getLocalNodeHandle() {
    return objectStore.getLocalNodeHandle();
  }
  
  public int getReplicationFactor() {
    return objectStore.getReplicationFactor();
  }

  public boolean forward(RouteMessage message) {
    return true;
  }
  
  public void update(NodeHandle handle, boolean joined) {
  }

  public void deliver(Id id, Message message) {

    final AggregationMessage msg = (AggregationMessage) message;
    log(3, "Received message " + msg + " with destination " + id + " from " + msg.getSource().getId());

    if (msg instanceof AggregationTimeoutMessage) {
    
      /* TimeoutMessages are generated by the local node when a 
         timeout expires. */
    
      AggregationTimeoutMessage gtm = (AggregationTimeoutMessage) msg;
      timerExpired((char) gtm.getUID());
      return;
    } else {
      panic("AGGREGATION ERROR - Received message " + msg + " of unknown type.");
    }
  }

  public void setFlushInterval(int flushIntervalSec) {
    flushInterval = flushIntervalSec * SECONDS;
  }
  
  public void setMaxAggregateSize(int maxAggregateSize) {
    this.maxAggregateSize = maxAggregateSize;
  }

  public void setMaxObjectsInAggregate(int maxObjectsInAggregate) {
    this.maxObjectsInAggregate = maxObjectsInAggregate;
  }

  public void setRenewThreshold(int expirationRenewThresholdHrs) {
    this.expirationRenewThreshold = expirationRenewThresholdHrs * HOURS;
  }
  
  public void setConsolidationInterval(long consolidationIntervalSec) {
    this.consolidationInterval = consolidationIntervalSec * SECONDS;
  }
  
  public void setConsolidationThreshold(long consolidationThresholdSec) {
    this.consolidationThreshold = consolidationThresholdSec * SECONDS;
  }
  
  public void setConsolidationMinObjectsPerAggregate(int minObjectsInAggregateArg) {
    this.consolidationMinObjectsInAggregate = minObjectsInAggregateArg;
  }
  
  public void setConsolidationMinUtilization(double minUtilization) {
    this.consolidationMinComponentsAlive = minUtilization;
  }

  public void setLogLevel(int newLevel) {
    this.loglevel = newLevel;
  }

  public Past getAggregateStore() {
    return aggregateStore;
  }
  
  public Past getObjectStore() {
    return objectStore;
  }
  
  public int getNumObjectsWaiting() {
    return waitingList.scan().numElements();
  }
  
  public AggregationStatistics getStatistics() {
    return stats;
  }

}
