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

import rice.Continuation;
import rice.p2p.aggregation.messaging.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.VersionKey;
import rice.p2p.glacier.VersioningPast;
import rice.p2p.glacier.v2.DebugContent;
import rice.p2p.past.Past;
import rice.p2p.past.PastContent;
import rice.p2p.past.PastContentHandle;
import rice.p2p.past.gc.GCPast;
import rice.p2p.past.gc.GCPastContent;
import rice.p2p.past.gc.GCPastContentHandle;
import rice.persistence.StorageManager;
import rice.visualization.server.DebugCommandHandler;

class ObjectDescriptor implements Serializable {
  
  public Id key;
  public long version;
  public long currentLifetime;
  public long refreshedLifetime;
  public int size;
  
  public ObjectDescriptor(Id key, long version, long currentLifetime, long refreshedLifetime, int size) {
    this.key = key;
    this.currentLifetime = currentLifetime;
    this.refreshedLifetime = refreshedLifetime;
    this.size = size;
    this.version = version;
  }
  
  public String toString() {
    return "objDesc["+key.toStringFull()+"v"+version+", lt="+currentLifetime+", rt="+refreshedLifetime+", size="+size+"]";
  }
};

class AggregateDescriptor {
  
  public Id key;
  public long currentLifetime;
  public ObjectDescriptor[] objects;
  public Id[] pointers;
  public boolean marker;
  public int referenceCount;

  public AggregateDescriptor(Id key, long currentLifetime, ObjectDescriptor[] objects, Id[] pointers) {
    this.key = key;
    this.currentLifetime = currentLifetime;
    this.objects = objects;
    this.pointers = pointers;
    this.marker = false;
    this.referenceCount = 0;
  }
  
  public int lookupNewest(Id id) {
    int result = -1;
    for (int i=0; i<objects.length; i++)
      if (objects[i].key.equals(id))
        if ((result == -1) || (objects[i].version > objects[result].version))
          result = i;
    return result;
  }

  public int lookupSpecific(Id id, long version) {
    for (int i=0; i<objects.length; i++)
      if (objects[i].key.equals(id) && (objects[i].version == version))
        return i;
        
    return -1;
  }
  
  public void addReference() {
    referenceCount ++;
  }
}  

public class AggregationImpl implements Past, GCPast, VersioningPast, Aggregation, Application, DebugCommandHandler {

  protected final Past aggregateStore;
  protected final StorageManager waitingList;
  protected final AggregationPolicy policy;
  protected final Hashtable aggregateList;
  protected final String configFileName;
  protected final Endpoint endpoint;
  protected final Past objectStore;
  protected final String instance;
  protected final IdFactory factory;
  protected final String debugID;
  protected final Node node;

  private final char tiFlush = 1;
  private final char tiMonitor = 2;
  protected Hashtable timers;
  protected Id rootKey;
  protected int expirationCounter;
  protected Continuation flushWait;
  protected boolean rebuildInProgress;
  protected int numAggregates;
  protected int numObjectsInAggregates;
  protected Vector monitorIDs;

  private final int loglevel = 2;

  private static final long SECONDS = 1000;
  private static final long MINUTES = 60 * SECONDS;
  private static final long HOURS = 60 * MINUTES;
  private static final long DAYS = 24 * HOURS;
  private static final long WEEKS = 7 * DAYS;

  private static final long flushDelayAfterJoin = 30 * SECONDS;
  private static long flushInterval = 5 * MINUTES;

  private static int maxAggregateSize = 1024*1024;
  private static int maxObjectsInAggregate = 20;
  
  private static final boolean addMissingAfterRefresh = false;
  private static final int nominalReferenceCount = 2;
  private static final int maxPointersPerAggregate = 100;
  private static final long pointerArrayLifetime = 2 * WEEKS;

  private static final long expirationInterval = 5 * MINUTES;
  private static long expirationRenewThreshold = 1 * DAYS;

  private static final boolean monitorEnabled = false;
  private static final long monitorRefreshInterval = 10 * MINUTES;

  public AggregationImpl(Node node, Past aggregateStore, Past objectStore, StorageManager waitingList, String configFileName, IdFactory factory, String instance) {
    this(node, aggregateStore, objectStore, waitingList, configFileName, factory, instance, getDefaultPolicy());
  }

  public AggregationImpl(Node node, Past aggregateStore, Past objectStore, StorageManager waitingList, String configFileName, IdFactory factory, String instance, AggregationPolicy policy) {
    this.endpoint = node.registerApplication(this, instance);
    this.waitingList = waitingList;
    this.instance = instance;
    this.aggregateStore = aggregateStore;
    this.objectStore = objectStore;
    this.node = node;
    this.timers = new Hashtable();
    this.aggregateList = new Hashtable();
    this.configFileName = configFileName;
    this.policy = policy;
    this.rootKey = null;
    this.factory = factory;
    this.expirationCounter = 1;
    this.flushWait = null;
    this.rebuildInProgress = false;
    this.numAggregates = 0;
    this.numObjectsInAggregates = 0;
    this.monitorIDs = new Vector();
    this.debugID = "A" + Character.toUpperCase(instance.charAt(instance.lastIndexOf('-')+1));

    readAggregateList();
    addTimer(flushDelayAfterJoin, tiFlush);
    if (monitorEnabled)
      addTimer(monitorRefreshInterval, tiMonitor);
  }

  private static AggregationPolicy getDefaultPolicy() {
    return new AggregationDefaultPolicy();
  }

  private String readLineSkipComments(BufferedReader br) throws IOException {
    while (true) {
      String line = br.readLine();
      if ((line != null) && ((line.length() == 0) || (line.charAt(0) == '#')))
        continue;
      return line;
    }
  }

  private void readAggregateList() {

    rootKey = null;
    aggregateList.clear();
    numAggregates = 0;
    numObjectsInAggregates = 0;
  
    String fileName;
    if ((new File(configFileName)).exists())
      fileName = configFileName;
    else if ((new File(configFileName + ".new")).exists())
      fileName = configFileName + ".new";
    else {
      warn("Cannot find configuration file: "+configFileName);
      return;
    }

    BufferedReader configFile = null;
    boolean readSuccessful = false;
  
    try {
      configFile = new BufferedReader(new FileReader(fileName));
      
      String[] root = readLineSkipComments(configFile).split("=");
      if (!root[0].equals("root"))
        throw new Exception("Cannot read root key: "+root[0]);
      rootKey = factory.buildIdFromToString(root[1]);
      
      while (true) {
        String aggrKeyLine = readLineSkipComments(configFile);
        if (aggrKeyLine == null) {
          readSuccessful = true;
          break;
        }
          
        String[] aggrKeyS = aggrKeyLine.split("\\[|\\]");
        Id aggrKey = factory.buildIdFromToString(aggrKeyS[1]);
        
        String[] expiresS = readLineSkipComments(configFile).split("=");
        if (!expiresS[0].equals("expires"))
          throw new Exception("Cannot find expiration date: "+expiresS[0]);
        long expires = Long.parseLong(expiresS[1]);
        
        String[] objectNumS = readLineSkipComments(configFile).split("=");
        if (!objectNumS[0].equals("objects"))
          throw new Exception("Cannot find number of objects: "+objectNumS[0]);
        int numObjects = Integer.parseInt(objectNumS[1]);
        
        ObjectDescriptor[] objects = new ObjectDescriptor[numObjects];
        for (int i=0; i<numObjects; i++) {
          String[] objS = readLineSkipComments(configFile).split("=");
          String[] objArgS = objS[1].split(";");
          String[] objIdS = objArgS[0].split("v");
          objects[i] = new ObjectDescriptor(
            factory.buildIdFromToString(objIdS[0]),
            Long.parseLong(objIdS[1]),
            Long.parseLong(objArgS[1]),
            Long.parseLong(objArgS[2]),
            Integer.parseInt(objArgS[3])
          );
        }
        
        String[] pointerNumS = readLineSkipComments(configFile).split("=");
        if (!pointerNumS[0].equals("pointers"))
          throw new Exception("Cannot find number of pointers: "+pointerNumS[0]);
        int numPointers = Integer.parseInt(pointerNumS[1]);
        
        Id[] pointers = new Id[numPointers];
        for (int i=0; i<numPointers; i++) {
          String[] ptrS = readLineSkipComments(configFile).split("=");
          pointers[i] = factory.buildIdFromToString(ptrS[1]);
        }
        
        AggregateDescriptor aggr = new AggregateDescriptor(aggrKey, expires, objects, pointers);
        addAggregateDescriptor(aggr);
      }
    } catch (Exception e) {
      warn("Cannot read configuration file: "+configFileName+" (e="+e+")");
      e.printStackTrace();
    }

    if (configFile != null) {
      try {
        configFile.close();
      } catch (Exception e) {
      }
    }
    
    if (!readSuccessful) {
      warn("Failed to read configuration file; aggregate list must be rebuilt!");
      rootKey = null;
      aggregateList.clear();
    } else {
      log(2, "Aggregate list read OK -- current root: " + ((rootKey == null) ? "null" : rootKey.toStringFull()));
      recalculateReferenceCounts();
    }
  }

  private void writeAggregateList() {
    if (rootKey == null)
      return;
  
    try {
      PrintStream configFile = new PrintStream(new FileOutputStream(configFileName + ".new"));
      Enumeration enum = aggregateList.elements();

      resetMarkers();
      configFile.println("# Aggregate list at " + getLocalNodeHandle().getId() + " (" + (new Date()) + ")");
      configFile.println();
      configFile.println("root="+rootKey.toStringFull());
      configFile.println();
      
      while (enum.hasMoreElements()) {
        AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
        if (!aggr.marker) {
          configFile.println("["+aggr.key.toStringFull()+"]");
          configFile.println("expires=" + aggr.currentLifetime);
          configFile.println("objects=" + aggr.objects.length);
          for (int i=0; i<aggr.objects.length; i++)
            configFile.println("obj"+i+"="+
              aggr.objects[i].key.toStringFull()+"v"+
              aggr.objects[i].version+";"+
              aggr.objects[i].currentLifetime+";"+
              aggr.objects[i].refreshedLifetime+";"+
              aggr.objects[i].size
            );
          configFile.println("pointers=" + aggr.pointers.length);
          for (int i=0; i<aggr.pointers.length; i++)
            configFile.println("ptr"+i+"="+aggr.pointers[i].toStringFull());
          configFile.println("");

          aggr.marker = true;
        }
      }

      configFile.close();
      (new File(configFileName)).delete();
      (new File(configFileName + ".new")).renameTo(new File(configFileName));
    } catch (IOException ioe) {
      System.err.println("AggregationImpl cannot write to its aggregate list: " + configFileName + " (" + ioe + ")");
      ioe.printStackTrace();
      System.exit(1);
    }
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
  
    if ((cmd.length() >= 6) && cmd.substring(0, 6).equals("status")) {
      Enumeration enum = aggregateList.elements();
      int numAggr = 0, numObj = 0;

      resetMarkers();
      while (enum.hasMoreElements()) {
        AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
        if (!aggr.marker) {
          numAggr ++;
          numObj += aggr.objects.length;
          aggr.marker = true;
        }
      }

      return numAggr + " active aggregates with " + numObj + " objects\n" + getNumObjectsWaiting() + " objects waiting";
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

    if ((cmd.length() >= 11) && cmd.substring(0, 11).equals("show config")) {
      return 
        "flushDelayAfterJoin = " + (int)(flushDelayAfterJoin / SECONDS) + " sec\n" +
        "flushInterval = " + (int)(flushInterval / SECONDS) + " sec\n" +
        "maxAggregateSize = " + maxAggregateSize + " bytes\n" +
        "maxObjectsInAggregate = " + maxObjectsInAggregate + " objects\n" +
        "addMissingAfterRefresh = " + addMissingAfterRefresh + "\n" +
        "nominalReferenceCount = " + nominalReferenceCount + "\n" +
        "maxPointersPerAggregate = " + maxPointersPerAggregate + "\n" +
        "pointerArrayLifetime = " + (int)(pointerArrayLifetime / DAYS) + " days\n" +
        "expirationInterval = " + (int)(expirationInterval / SECONDS) + " sec\n" +
        "expirationRenewThreshold = " + (int)(expirationRenewThreshold / HOURS) + " hrs\n";
    }    

    if ((cmd.length() >= 2) && cmd.substring(0, 2).equals("ls")) {
      Enumeration enum = aggregateList.elements();
      StringBuffer result = new StringBuffer();
      int numAggr = 0, numObj = 0;

      long now = System.currentTimeMillis();
      if (cmd.indexOf("-r") < 0)
        now = 0;

      resetMarkers();
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

    if ((cmd.length() >= 10) && cmd.substring(0, 10).equals("write list")) {
      writeAggregateList();
      return "Done, new root is "+((rootKey==null) ? "null" : rootKey.toStringFull());
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

    if ((cmd.length() >= 5) && cmd.substring(0, 5).equals("flush")) {
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
    if ((cmd.length() >= 8) && cmd.substring(0, 8).equals("get root")) {
      return "root="+((rootKey==null) ? "null" : rootKey.toStringFull());
    }

    if ((cmd.length() >= 8) && cmd.substring(0, 8).equals("set root")) {
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

    if ((cmd.length() >= 6) && cmd.substring(0, 6).equals("lookup")) {
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

    if ((cmd.length() >= 7) && cmd.substring(0, 7).equals("handles")) {
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
      
    if ((cmd.length() >= 11) && cmd.substring(0, 11).equals("refresh all")) {
      long expiration = System.currentTimeMillis() + Long.parseLong(cmd.substring(12));
      TreeSet ids = new TreeSet();
      String result;
      
      resetMarkers();

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

    if ((cmd.length() >= 14) && cmd.substring(0, 14).equals("monitor remove") && monitorEnabled) {
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

    if ((cmd.length() >= 14) && cmd.substring(0, 14).equals("monitor status") && monitorEnabled) {
      return "Monitor is "+(monitorEnabled ? ("enabled, monitoring "+monitorIDs.size()+" objects") : "disabled");
    }

    if ((cmd.length() >= 10) && cmd.substring(0, 10).equals("monitor ls") && monitorEnabled) {
      StringBuffer result = new StringBuffer();
      Enumeration enum = monitorIDs.elements();
      
      while (enum.hasMoreElements())
        result.append(((Id)enum.nextElement()).toStringFull() + "\n");
        
      result.append(monitorIDs.size() + " object(s)");
      return result.toString();
    }

    if ((cmd.length() >= 13) && cmd.substring(0, 13).equals("monitor check") && monitorEnabled) {
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
            
            AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(currentId);
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

    if ((cmd.length() >= 11) && cmd.substring(0, 11).equals("monitor add") && monitorEnabled) {
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
      
    if ((cmd.length() >= 7) && cmd.substring(0, 7).equals("killall")) {
      String args = cmd.substring(8);
      String expirationArg = args.substring(args.lastIndexOf(' ') + 1);
      String keyArg = args.substring(0, args.lastIndexOf(' '));

      Id id = factory.buildIdFromToString(keyArg);
      long expiration = System.currentTimeMillis() + Long.parseLong(expirationArg);

      AggregateDescriptor aggr = (AggregateDescriptor) aggregateList.get(id);
      if (aggr != null) {
        aggr.currentLifetime = Math.min(aggr.currentLifetime, expiration);
        for (int i=0; i<aggr.objects.length; i++) {
          aggr.objects[i].currentLifetime = Math.min(aggr.objects[i].currentLifetime, expiration);
          aggr.objects[i].refreshedLifetime = Math.min(aggr.objects[i].refreshedLifetime, expiration);
        }
        return "OK";
      }
      
      return "Aggregate "+id+" not found in aggregate list";
    }

    if ((cmd.length() >= 7) && cmd.substring(0, 7).equals("waiting")) {
      Iterator iter = waitingList.scan().getIterator();
      String result = "";

      result = result + waitingList.scan().numElements()+ " object(s) waiting\n";
      
      while (iter.hasNext())
        result = result + ((Id)iter.next()).toStringFull()+"\n";
        
      return result;
    }
      
    if ((cmd.length() >= 7) && cmd.substring(0, 7).equals("vlookup")) {
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

  private void resetMarkers() {
    Enumeration enum = aggregateList.elements();
    while (enum.hasMoreElements()) {
      AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
      aggr.marker = false;
    }
  }

  private void addAggregateDescriptor(AggregateDescriptor aggr) {
    aggregateList.put(aggr.key, aggr);
    numAggregates ++;

    for (int i=0; i<aggr.objects.length; i++) {
      aggregateList.put(new VersionKey(aggr.objects[i].key, aggr.objects[i].version), aggr);
      numObjectsInAggregates ++;
      AggregateDescriptor prevDesc = (AggregateDescriptor) aggregateList.get(aggr.objects[i].key);
      int objDescIndex = (prevDesc == null) ? -1 : prevDesc.lookupNewest(aggr.objects[i].key);
      if ((objDescIndex < 0) || (prevDesc.objects[objDescIndex].version < aggr.objects[i].version)) {
        aggregateList.put(aggr.objects[i].key, aggr);
      }
    }

    for (int i=0; i<aggr.pointers.length; i++) {
      AggregateDescriptor ref = (AggregateDescriptor) aggregateList.get(aggr.pointers[i]);
      if (ref != null)
        ref.addReference();
    }
  }

  private void removeAggregateDescriptor(AggregateDescriptor aggr) {
    aggregateList.remove(aggr.key);
    numAggregates --;
    
    for (int i=0; i<aggr.objects.length; i++) {
      aggregateList.remove(new VersionKey(aggr.objects[i].key, aggr.objects[i].version));
      numObjectsInAggregates --;
      AggregateDescriptor prevDesc = (AggregateDescriptor) aggregateList.get(aggr.objects[i].key);
      if (prevDesc.key.equals(aggr.key))
        aggregateList.remove(aggr.objects[i].key);
    }
    
    if (aggregateList.containsValue(aggr))
      warn("Removal from aggregate list incomplete: "+aggr.key.toStringFull());
  }
  
  private void recalculateReferenceCounts() {
    Enumeration enum = aggregateList.elements();
    while (enum.hasMoreElements()) {
      AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
      aggr.referenceCount = 0;
      aggr.marker = false;
    }
    
    enum = aggregateList.elements();
    while (enum.hasMoreElements()) {
      AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
      if (!aggr.marker) {
        aggr.marker = true;
        for (int i=0; i<aggr.pointers.length; i++) {
          AggregateDescriptor ref = (AggregateDescriptor) aggregateList.get(aggr.pointers[i]);
          if (ref != null)
            ref.addReference();
        }
      }
    }
  }

  private Id[] getSomePointers(int referenceThreshold) {
    if (rootKey == null)
      return new Id[] {};
      
    resetMarkers();
  
    Vector pointers = new Vector();

    Enumeration enum = aggregateList.elements();
    while (enum.hasMoreElements()) {
      AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
      if (!aggr.marker) {
        aggr.marker = true;
        if ((aggr.referenceCount < referenceThreshold) && (pointers.size() < maxPointersPerAggregate))
          pointers.add(aggr.key);
      }
    }
    
    Id[] result = new Id[pointers.size()];
    for (int i=0; i<pointers.size(); i++)
      result[i] = (Id) pointers.elementAt(i);
      
    return result;
  }

  private void storeAggregate(final Aggregate aggr, final long expiration, final ObjectDescriptor[] desc, final Id[] pointers, final Continuation command) {
    aggr.setId(factory.buildId(aggr.getContentHash()));
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
          addAggregateDescriptor(adc);
          rootKey = aggr.getId();
          writeAggregateList();
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
  }

  private VersionKey getMostCurrentWaiting(IdSet waitingKeys, Id id) {

    VersionKey highestVersion = null;
    if (id != null) {
      Iterator iter = waitingKeys.getIterator();

      while (iter.hasNext()) {
        VersionKey thisKey = (VersionKey) iter.next();
        if (thisKey.getId().equals(id)) {
          if ((highestVersion == null) || (highestVersion.getVersion() < thisKey.getVersion()))
            highestVersion = thisKey;
        }
      }
    }
    
    return highestVersion;
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
      log(3, "Flush in progress... daisy-chaining continuation");
      final Continuation parent = flushWait;
      flushWait = new Continuation() {
        public void receiveResult(Object o) {
          log(3, "Daisy-chain receiveResult(), restarting "+command);
          parent.receiveResult(o);
          formAggregates(command);
        }
        public void receiveException(Exception e) {
          log(3, "Daisy-chain receiveException(), restarting "+command);
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
    Iterator iter = waitingKeys.getIterator();
    long currentAggregateSize = 0;
    int currentObjectsInAggregate = 0;
    
    while (true) {
      ObjectDescriptor thisObject = null;
      boolean mustAddObject = false;
      
      while (iter.hasNext()) {
        thisObject = (ObjectDescriptor) waitingList.getMetadata((Id) iter.next());
        if (((currentAggregateSize + thisObject.size) <= maxAggregateSize) && (currentObjectsInAggregate < maxObjectsInAggregate)) {
          currentAggregateSize += thisObject.size;
          currentObjectsInAggregate ++;
          currentAggregate.add(thisObject);
        } else {
          mustAddObject = true;
          break;
        }
      }
      
      int numObjectsInAggregate = currentAggregate.size();
      ObjectDescriptor[] desc = new ObjectDescriptor[numObjectsInAggregate];

      for (int i=0; i<numObjectsInAggregate; i++) {
        desc[i] = (ObjectDescriptor) currentAggregate.elementAt(i);
        log(3, "#"+i+": "+desc[i].key+" "+desc[i].size+" bytes");
      }

      aggregates.add(desc);
      currentAggregate.clear();
      currentAggregateSize = 0;
        
      if (mustAddObject) {
        currentAggregate.add(thisObject);
        currentAggregateSize += thisObject.size;
      } else {
        if (!iter.hasNext())
          break;
      }
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
              Id[] pointers = getSomePointers(nominalReferenceCount);
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

    log(2, "Refreshing aggregates");

    resetMarkers();
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

            if (aggregateStore instanceof GCPast) {
              final AggregateDescriptor aggrF = aggr;
              final long newLifetimeF = newLifetime;
              ((GCPast)aggregateStore).refresh(new Id[] { aggr.key }, newLifetime, new Continuation() {
                public void receiveResult(Object o) {
                  if (o instanceof Object[]) {
                    Object[] oA = (Object[]) o;
                    if ((oA[0] instanceof Boolean) && ((Boolean)oA[0]).booleanValue()) {
                      log(3, "Aggregate successfully refreshed: "+aggrF.key);
                      aggrF.currentLifetime = newLifetimeF;
                      for (int i=0; i<aggrF.objects.length; i++) 
                        aggrF.objects[i].currentLifetime = aggrF.objects[i].refreshedLifetime;
                      writeAggregateList();
                    } else {
                      warn("Aggregate refresh failed: "+aggrF.key.toStringFull()+" (result="+oA[0]+")");
                    }
                  } else {
                    warn("Aggregate refresh: Unexpected return value "+o);
                  }
                }
                public void receiveException(Exception e) {
                  warn("Interface contract broken; exception "+e+" returned directly");
                  e.printStackTrace();
                }
              });
            } else {
              log(3, "Aggregate store does not support GC; refreshing directly");
              aggr.currentLifetime = newLifetime;
            }
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
      removeAggregateDescriptor(aggr);
    }
    
    if (deletedOne) {
      recalculateReferenceCounts();
      writeAggregateList();
    }
  }

  private void reconnectTree() {
  
    if (rebuildInProgress) {
      log(2, "Skipping connectivity check (rebuild in progress)");
      return;
    }
  
    log(2, "Checking for disconnections");
    
    Id[] disconnected = getSomePointers(1);
    if (disconnected.length < 2) {
      rootKey = (disconnected.length == 1) ? disconnected[0] : null;
      log(2, "No aggregates disconnected (n="+disconnected.length+")");
      log(3, "root="+((rootKey == null) ? "null" : rootKey.toStringFull()));
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
        Continuation doNothing = new Continuation() {
          public void receiveResult(Object o) { 
            log(3, "Scheduled flush: Success (o="+o+")");
          }
          public void receiveException(Exception e) {
            warn("Scheduled flush: Failure (e="+e+")");
            e.printStackTrace();
          }
        };
        
        if ((--expirationCounter) < 1) {
          expirationCounter = (int)(expirationInterval / flushInterval);
          refreshAggregates();
          formAggregates(doNothing);
          reconnectTree();
        } else {
          formAggregates(doNothing);
        }
        
        addTimer(flushInterval, tiFlush);
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
      default:
      {
        panic("Unknown timer expired: " + (int) timerID);
      }
    }
  }

  private void refreshInObjectStore(Id id, long expiration, Continuation command) {
    if (objectStore instanceof GCPast) {
      ((GCPast)objectStore).refresh(new Id[] { id }, expiration, command);
    } else {
      command.receiveResult(new Boolean(true));
    }
  }
  
  public void refresh(final Id[] ids, final long expiration, final Continuation command) {
    if (ids.length < 1) {
      command.receiveResult(new Boolean[] {});
      return;
    }
    
    Continuation.MultiContinuation mc = new Continuation.MultiContinuation(command, ids.length);
    for (int i=0; i<ids.length; i++)
      refresh(ids[i], expiration, mc.getSubContinuation(i));
  }
  
  public void refresh(final Id[] ids, final long[] versions, final long expiration, final Continuation command) {
    final Object result[] = new Object[ids.length];
    
    for (int i=0; i<ids.length; i++) {
      log(2, "Refresh("+ids[i]+"v"+versions[i]+", expiration="+expiration+")");

      AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(new VersionKey(ids[i], versions[i]));
      if (adc!=null) {
        int objDescIndex = adc.lookupSpecific(ids[i], versions[i]);
        if (objDescIndex < 0) {
          result[i] = new AggregationException("Inconsistency detected in aggregate list -- try restarting the application");
        } else {
          if (adc.objects[objDescIndex].refreshedLifetime < expiration)
            adc.objects[objDescIndex].refreshedLifetime = expiration;

          result[i] = new Boolean(true);
        }
      } else result[i] = new AggregationException("Not found");
    }
      
    if (objectStore instanceof VersioningPast) {
      ((VersioningPast)objectStore).refresh(ids, versions, expiration, new Continuation() {
        public void receiveResult(Object o) {
          if (o instanceof Object[]) {
            Object[] subresult = (Object[]) o;
            for (int i=0; i<result.length; i++)
              if ((result[i] instanceof Boolean) && !(subresult[i] instanceof Boolean))
                result[i] = subresult[i];
          } else {
            Exception e = new AggregationException("Object sture returns unexpected result: "+o);
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

  private void refresh(final Id id, final long expiration, final Continuation command) {
    AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(id);
    log(2, "Refresh("+id.toStringFull()+", expiration="+expiration+")");
    
    if (adc!=null) {
      int objDescIndex = adc.lookupNewest(id);
      if (objDescIndex < 0) {
        warn("NL: Aggregate found, but object not found in aggregate?!? -- aborted");
        command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
        return;
      }

      if (adc.objects[objDescIndex].refreshedLifetime < expiration)
        adc.objects[objDescIndex].refreshedLifetime = expiration;
        
      refreshInObjectStore(id, expiration, command);
    } else {
    
      /* Maybe the object is missing from the aggregate list? We attempt to fetch it 
         from PAST, and if it is found there, we add it to the waiting list */

      if (addMissingAfterRefresh) {         
        objectStore.lookup(id, false, new Continuation() {
          public void receiveResult(Object o) {
            if (o instanceof PastContent) {
              final PastContent obj = (PastContent) o;
              warn("Refresh: Found in PAST, but not in aggregate list: "+id);
            
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
                log(3, "ADDING MISSING AGGRGATE: "+obj.getId());

                waitingList.store(vkey, new ObjectDescriptor(obj.getId(), theVersionF, expiration, expiration, theSize), obj, new Continuation() {
                  public void receiveResult(Object o) {
                  }
                  public void receiveException(Exception e) { 
                    warn("Exception while refreshing aggregate: "+obj.getId()+" (e="+e+")");
                    e.printStackTrace();
                  }
                });
              }
            
              refreshInObjectStore(id, expiration, command);
            } else {
              warn("Cannot find refreshed object "+id+", lookup returns "+o);
              command.receiveException(new AggregationException("Object not found: "+id.toStringFull()));
            }
          }
          public void receiveException(Exception e) {
            command.receiveException(e);
          }
        });
      } else {
        warn("Refreshed object not found in any aggregate: "+id.toStringFull());
        refreshInObjectStore(id, expiration, command);
      }
    }
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
    return rootKey;
  }
  
  public void setHandle(Serializable handle, Continuation command) {
    log(2, "setHandle("+handle+")");
  
    if (!(handle instanceof Id)) {
      command.receiveException(new AggregationException("Illegal handle"));
      return;
    }
    
    if (aggregateList.get((Id) handle) != null) {
      log(2, "Rebuild: Handle "+handle+" is already covered by current root");
      command.receiveResult(new Boolean(true));
    }
      
    rootKey = (Id) handle;
    rebuildAggregateList(command);
  }

  private void rebuildAggregateList(final Continuation command) {
    log(2, "rebuildAggregateList");
    if (rootKey == null) {
      warn("rebuildAggregateList invoked while rootKey is null");
      command.receiveException(new AggregationException("Set handle first!"));
      return;
    }
    
    final Vector keysToDo = new Vector();
    final Vector keysDone = new Vector();
    keysToDo.add(rootKey);
    rebuildInProgress = true;
    
    log(3, "Rebuild: Fetching handles for aggregate " + rootKey.toStringFull());
    aggregateStore.lookupHandles(rootKey, 999, new Continuation() {
      Id currentLookup = rootKey;
      
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

                  log(3, "Rebuild: Got aggregate " + currentLookup.toStringFull());

                  Aggregate aggr = (Aggregate) o;
                  ObjectDescriptor[] objects = new ObjectDescriptor[aggr.components.length];
                  long aggregateExpiration = (thisHandle instanceof GCPastContentHandle) ? ((GCPastContentHandle)thisHandle).getExpiration() : GCPast.INFINITY_EXPIRATION;
          
                  for (int i=0; i<aggr.components.length; i++)
                    objects[i] = new ObjectDescriptor(
                      aggr.components[i].getId(), 
                      aggr.components[i].getVersion(),
                      aggregateExpiration,
                      aggregateExpiration,
                      getSize(aggr.components[i])
                    );
            
                  addAggregateDescriptor(new AggregateDescriptor(currentLookup, aggregateExpiration, objects, aggr.getPointers()));
          
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
                    log(3, "Rebuild: "+keysToDo.size()+" keys to go, "+keysDone.size()+" done");
                    currentLookup = (Id) keysToDo.firstElement();
                    log(3, "Rebuild: Fetching handles for aggregate " + currentLookup.toStringFull());
                    aggregateStore.lookupHandles(currentLookup, 999, outerContinuation);
                  } else {
                    recalculateReferenceCounts();
                    writeAggregateList();
                    rebuildInProgress = false;
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
          log(3, "Rebuild: Fetching handles for aggregate " + currentLookup.toStringFull());
          aggregateStore.lookupHandles(currentLookup, 999, this);
        } else {
          if (aggregateList.isEmpty()) {
            rebuildInProgress = false;
            command.receiveException(new AggregationException("Cannot read root aggregate! -- retry later"));
          } else {
            recalculateReferenceCounts();
            writeAggregateList();
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
      log(2, "AGGREGATE INSERT: "+obj.getId()+" size="+theSize+" class="+obj.getClass().getName());

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
      log(2, "INSERT WITHOUT AGGREGATION: "+obj.getId());
      
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
          Aggregate aggr = (Aggregate) o;
          Id aggrNominalKey = factory.buildId(aggr.getContentHash());

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
          AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(id);
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

    AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(new VersionKey(id, version));
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
      if (objectStore instanceof VersioningPast) {
        VersioningPast vpast = (VersioningPast) objectStore;
        vpast.lookup(id, version, new Continuation() {
          public void receiveResult(Object o) {
            if (o != null) {
              log(3, "VL: Found in VersioningPAST: "+id+"v"+version);
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
    }
  }
  
  public void lookup(Id id, Continuation command) {
    lookup(id, true, command);
  }
  
  public void lookupHandles(final Id id, final long version, final int max, final Continuation command) {
    panic("lookupHandles invoked with version number!");
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
          warn("lookupHandles("+id+","+max+") failed, ret="+o+" -- restoring");

          AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(id);
          if (adc!=null) {
            log(3, "lookupHandles: Retrieving from aggregate");

            int objDescIndex = adc.lookupNewest(id);
            if (objDescIndex < 0) {
              warn("lookupHandles: Aggregate found, but object not found in aggregate?!? -- aborted");
              command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
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
            warn("lookupHandles: "+id+" is neither in object store nor in aggregate list");
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
  
    objectStore.fetch(handle, command);
  }

  public void flush(Id id, Continuation command) {
    Iterator iter = waitingList.scan().getIterator();
    boolean objectIsWaiting = false;

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
    AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(id);

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
    rootKey = null;
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

  public Past getAggregateStore() {
    return aggregateStore;
  }
  
  public Past getObjectStore() {
    return objectStore;
  }
  
  public int getNumObjectsWaiting() {
    return waitingList.scan().numElements();
  }
  
  public int getNumAggregates() {
    return numAggregates;
  }
  
  public int getNumObjectsInAggregates() {
    return numObjectsInAggregates;
  }
}
