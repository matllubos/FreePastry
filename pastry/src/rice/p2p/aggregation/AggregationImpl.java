package rice.p2p.aggregation;

import java.util.*;
import java.io.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.StorageManager;
import rice.Continuation;
import rice.p2p.glacier.v2.DebugContent;
import rice.p2p.glacier.VersionKey;
import rice.p2p.glacier.VersioningPast;
import rice.p2p.commonapi.*;
import rice.visualization.server.DebugCommandHandler;

class ObjectDescriptor {
  
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
  protected final Hashtable aggregateList;
  protected final String configFileName;
  protected final Endpoint endpoint;
  protected final Past objectStore;
  protected final String instance;
  protected final IdFactory factory;
  protected final Node node;
  protected Id rootKey;

  private final char tiFlush = 1;
  protected Hashtable timers;
  protected Vector waiting;
  protected int expirationCounter;

  private static final int SECONDS = 1000;
  private static final int MINUTES = 60 * SECONDS;
  private static final int HOURS = 60 * MINUTES;

  private static final int flushDelayAfterJoin = 5 * SECONDS;
  private static final int flushInterval = 30 * SECONDS;

  private static final int maxAggregateSize = 1024*1024;
  private static final int aggregateComponentThreshold = 100*1024;
  
  private static final boolean addMissingAfterRefresh = false;
  private static final int nominalReferenceCount = 2;
  private static final int maxPointersPerAggregate = 100;
  private static final long pointerArrayLifetime = 14L*7*24*HOURS;

  private static final int expirationInterval = 30 * SECONDS;
  private static final int expirationRenewThreshold = 30 * SECONDS;

  public AggregationImpl(Node node, Past aggregateStore, Past objectStore, StorageManager waitingList, String configFileName, IdFactory factory, String instance) {
    this.endpoint = node.registerApplication(this, instance);
    this.waitingList = waitingList;
    this.instance = instance;
    this.aggregateStore = aggregateStore;
    this.objectStore = objectStore;
    this.node = node;
    this.timers = new Hashtable();
    this.waiting = new Vector();
    this.aggregateList = new Hashtable();
    this.configFileName = configFileName;
    this.rootKey = null;
    this.factory = factory;
    this.expirationCounter = 1;
    readAggregateList();

    /* read waiting entries from metadata !!! */
    
    addTimer(flushDelayAfterJoin, tiFlush);
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
      log("Aggregate list read OK");
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
    TimerTask timer = endpoint.scheduleMessage(new AggregationTimeoutMessage(timeoutID, getLocalNodeHandle()), timeoutMsec);
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

  private void panic(String s) throws Error {
    System.err.println("PANIC: " + s);
    throw new Error("Panic");
  }

  public String handleDebugCommand(String command) {
    String requestedInstance = command.substring(0, command.indexOf(" "));
    String myInstance = "aggr."+instance.substring(instance.lastIndexOf("-") + 1);
    
    if (!requestedInstance.equals(myInstance) && !requestedInstance.equals("a")) {
      String subResult = null;

      if ((subResult == null) && (aggregateStore instanceof DebugCommandHandler))
        subResult = ((DebugCommandHandler)aggregateStore).handleDebugCommand(command);
      if ((subResult == null) && (objectStore instanceof DebugCommandHandler))
        subResult = ((DebugCommandHandler)objectStore).handleDebugCommand(command);

      return subResult;
    }
  
    String cmd = command.substring(requestedInstance.length() + 1);
  
    if ((cmd.length() >= 6) && cmd.substring(0, 6).equals("status")) {
      String result = "OK";
      return result;
    }

    if ((cmd.length() >= 6) && cmd.substring(0, 6).equals("insert")) {
      int numObjects = Integer.parseInt(cmd.substring(7));
      String result = "";
      
      for (int i=0; i<numObjects; i++) {
        Id randomID = factory.buildRandomId(new Random());
        result = result + randomID.toStringFull() + "\n";
        insert(
          new DebugContent(randomID, false, 0),
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

    if ((cmd.length() >= 2) && cmd.substring(0, 2).equals("ls")) {
      Enumeration enum = aggregateList.elements();
      String result = "";
      int numAggr = 0;

      long now = System.currentTimeMillis();
      if (cmd.indexOf("-r") < 0)
        now = 0;

      resetMarkers();
      while (enum.hasMoreElements()) {
        AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
        if (!aggr.marker) {
          result = result + "***" + aggr.key.toStringFull() + " (" + aggr.objects.length + " obj, " + 
                   aggr.pointers.length + " ptr, " + aggr.referenceCount + " ref, exp=" + 
                   (aggr.currentLifetime - now) + ")\n";
          for (int i=0; i<aggr.objects.length; i++)
            result = result + "    #"+i+" "+
              aggr.objects[i].key.toStringFull()+"v"+aggr.objects[i].version +
              ", lt=" + (aggr.objects[i].currentLifetime-now) +
              ", rt=" + (aggr.objects[i].refreshedLifetime-now) +
              ", size=" + aggr.objects[i].size + " bytes\n";
          for (int i=0; i<aggr.pointers.length; i++) 
            result = result + "    Ref "+aggr.pointers[i].toStringFull()+"\n";
          result = result + "\n";
          aggr.marker = true;
          numAggr ++;
        }
      }

      result = result + numAggr + " aggregate(s)";
      
      return result;
    }

    if ((cmd.length() >= 10) && cmd.substring(0, 10).equals("write list")) {
      writeAggregateList();
      return "Done, new root is "+((rootKey==null) ? "null" : rootKey.toStringFull());
    }

    if ((cmd.length() >= 10) && cmd.substring(0, 10).equals("clear list")) {
      rootKey = null;
      aggregateList.clear();
      return "Done; list is now empty!";
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
          }
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

    for (int i=0; i<aggr.objects.length; i++) {
      aggregateList.put(new VersionKey(aggr.objects[i].key, aggr.objects[i].version), aggr);
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
    for (int i=0; i<aggr.objects.length; i++) {
      aggregateList.remove(new VersionKey(aggr.objects[i].key, aggr.objects[i].version));
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
    log("Storing aggregate, CH="+aggr.getId());
  
    aggregateStore.insert(aggr, new Continuation() {
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
          log("AGGR: Aggregate inserted successfully");
          command.receiveResult(new Boolean(true));
        } else {
          warn("Unexpected result in aggregate insert (commit): "+o);
          command.receiveException(new AggregationException("Unexpected result (commit): "+o));
        }
      }
      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    });
  }

  private void packBins() {
    if (waiting.size() == 0) {
      log("NO BINS TO PACK");
      return;
    }
  
    log("BIN PACKING STARTED");

    Vector currentAggregate = new Vector();
    Iterator iter = waiting.iterator();
    long currentAggregateSize = 0;
    
    while (true) {
      ObjectDescriptor thisObject = null;
      boolean mustAddObject = false;
      
      while (iter.hasNext()) {
        thisObject = (ObjectDescriptor) iter.next();
        if ((currentAggregateSize + thisObject.size) <= maxAggregateSize) {
          currentAggregateSize += thisObject.size;
          currentAggregate.add(thisObject);
        } else {
          mustAddObject = true;
          break;
        }
      }
      
      final int numObjectsInAggregate = currentAggregate.size();
      final ObjectDescriptor[] desc = new ObjectDescriptor[numObjectsInAggregate];
      final GCPastContent[] obj = new GCPastContent[numObjectsInAggregate];
      long aggrExpiration = 0;
      for (int i=0; i<numObjectsInAggregate; i++) {
        desc[i] = (ObjectDescriptor) currentAggregate.elementAt(i);
        if (desc[i].currentLifetime > aggrExpiration)
          aggrExpiration = desc[i].currentLifetime;
        log("#"+i+": "+desc[i].key+" "+desc[i].size+" bytes");
      }
      
      final long aggrExpirationF = aggrExpiration;
      log("AGGR: Retrieving #0: "+desc[0].key);
      waitingList.getObject(new VersionKey(desc[0].key, desc[0].version), new Continuation() {
        int currentQuery = 0;
        public void receiveResult(Object o) {
          if ((o!=null) && (o instanceof GCPastContent)) {
            obj[currentQuery++] = (GCPastContent) o;
            if (currentQuery < numObjectsInAggregate) {
              log("AGGR: Retrieving #"+currentQuery+": "+desc[currentQuery].key);
              waitingList.getObject(new VersionKey(desc[currentQuery].key, desc[currentQuery].version), this);
            } else {
              Id[] pointers = getSomePointers(nominalReferenceCount);
              storeAggregate(new Aggregate(obj, pointers), aggrExpirationF, desc, pointers, new Continuation() {
                public void receiveResult(Object o) {
                  for (int i=0; i<desc.length; i++) {
                    waiting.remove(desc[i]);
                    waitingList.unstore(new VersionKey(desc[i].key, desc[i].version), new Continuation() {
                      public void receiveResult(Object o) {
                      }
                      public void receiveException(Exception e) {
                        warn("Exception while unstoring aggregate component: "+e);
                        e.printStackTrace();
                      }
                    });
                  }
                }
                public void receiveException(Exception e) {
                  warn("Exception while storing new aggregate: "+e);
                  e.printStackTrace();
                }
              });
            }
          } else warn("Aggregation cannot retrieve "+desc[currentQuery].key+" (found o="+o+")");
        }
        public void receiveException(Exception e) {
          warn("Exception while building aggregate: "+e);
        }
      });

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
  }

  private void refreshAggregates() {
    Enumeration enum = aggregateList.elements();
    long now = System.currentTimeMillis();
    Vector removeList = new Vector();

    log("AGGR refreshing aggregates");

    resetMarkers();
    while (enum.hasMoreElements()) {
      AggregateDescriptor aggr = (AggregateDescriptor) enum.nextElement();
      if (!aggr.marker) {
        aggr.marker = true;
        
        boolean isBeingRefreshed = false;
        if (aggr.currentLifetime < (now + expirationRenewThreshold)) {
          long maxLifetime = 0;
          for (int i=0; i<aggr.objects.length; i++)
            if (aggr.objects[i].refreshedLifetime > maxLifetime)
              maxLifetime = aggr.objects[i].refreshedLifetime;
          if (maxLifetime > aggr.currentLifetime) {
            log("Refreshing "+aggr.key+", new expiration is "+maxLifetime);
            isBeingRefreshed = true;

            if (aggregateStore instanceof GCPast) {
              final AggregateDescriptor aggrF = aggr;
              final long maxLifetimeF = maxLifetime;
              ((GCPast)aggregateStore).refresh(new Id[] { aggr.key }, maxLifetime, new Continuation() {
                public void receiveResult(Object o) {
                  if (o instanceof Object[]) {
                    Object[] oA = (Object[]) o;
                    if ((oA[0] instanceof Boolean) && ((Boolean)oA[0]).booleanValue()) {
                      log("Aggregate successfully refreshed: "+aggrF.key);
                      aggrF.currentLifetime = maxLifetimeF;
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
              log("Aggregate store does not support GC; refreshing directly");
              aggr.currentLifetime = maxLifetime;
            }
          }
        }
            
        if ((aggr.currentLifetime < now) && !isBeingRefreshed) {
          log("AGGR Adding expired aggregate "+aggr.key+" to remove list");
          removeList.add(aggr);
        }
      }
    }

    boolean deletedOne = false;
    while (!removeList.isEmpty()) {
      AggregateDescriptor aggr = (AggregateDescriptor) removeList.elementAt(0);
      log("AGGR Removing expired aggregate "+aggr.key.toStringFull()+" from list");
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
    log("AGGR Checking for disconnections");
    
    Id[] disconnected = getSomePointers(1);
    if (disconnected.length < 2) {
      rootKey = (disconnected.length == 1) ? disconnected[0] : null;
      log("AGGR No aggregates disconnected (n="+disconnected.length+")");
      log("root="+((rootKey == null) ? "null" : rootKey.toStringFull()));
      return;
    }
    
    log("AGGR Found "+disconnected.length+" disconnected aggregates; inserting pointer array");
    storeAggregate(
      new Aggregate(new GCPastContent[] {}, disconnected), 
      System.currentTimeMillis() + pointerArrayLifetime,
      new ObjectDescriptor[] {},
      disconnected,
      new Continuation() {
        public void receiveResult(Object o) {
          log("AGGR Successfully inserted pointer array");
        }
        public void receiveException(Exception e) {
          warn("AGGR Error while inserting pointer array: "+e);
          e.printStackTrace();
        }
      }
    );
  }

  private void timerExpired(char timerID) {
    log("AGGR TIMER EXPIRED: #" + (int) timerID);

    switch (timerID) {
      case tiFlush :
      {
        boolean checkDisconnected = false;
        
        expirationCounter --;
        if (expirationCounter < 1) {
          refreshAggregates();
          expirationCounter = expirationInterval / flushInterval;
          checkDisconnected = true;
        }
        
        packBins();
       
        if (checkDisconnected)
          reconnectTree();
        
        addTimer(flushInterval, tiFlush);
        break;
      }
      default:
      {
        panic("Unknown timer expired: " + (int) timerID);
      }
    }
  }

  private boolean shouldBeAggregated(PastContent obj, int size) {
    return (!obj.isMutable() && (size<aggregateComponentThreshold));
  }
  
  private void refreshInObjectStore(Id id, long expiration, Continuation command) {
    if (objectStore instanceof GCPast) {
      ((GCPast)objectStore).refresh(new Id[] { id }, expiration, command);
    } else {
      command.receiveResult(new Boolean(true));
    }
  }
  
  public void refresh(final Id[] id, final long expiration, final Continuation command) {
    if (id.length < 1) {
      command.receiveResult(new Boolean[] {});
      return;
    }
    
    Continuation.MultiContinuation mc = new Continuation.MultiContinuation(command, id.length);
    for (int i=0; i<id.length; i++)
      refresh(id[i], expiration, mc.getSubContinuation(i));
  }

  private void refresh(final Id id, final long expiration, final Continuation command) {
    AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(id);
    log("AGGR: Refresh("+id+", expiration="+expiration+")");
    
    if (adc!=null) {
      int objDescIndex = adc.lookupNewest(id);
      if (objDescIndex < 0) {
        warn("NL: Aggregate found, but object not found in aggregate?!? -- aborted");
        command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
        return;
      }
      
      if (adc.objects[objDescIndex].refreshedLifetime < expiration)
        adc.objects[objDescIndex].refreshedLifetime = expiration;
        
      /* YYY maybe re-insert object if objectstore.refresh fails? */

      refreshInObjectStore(id, expiration, command);
    } else {
    
      /* Maybe the object is missing from the aggregate list? We attempt to fetch it 
         from PAST, and if it is found there, we add it to the waiting list */

      if (addMissingAfterRefresh) {         
        objectStore.lookup(id, false, new Continuation() {
          public void receiveResult(Object o) {
            if (o instanceof PastContent) {
              final PastContent obj = (PastContent) o;
              log("AGGR: Refresh: Found in PAST: "+id);
            
              long theVersion;
              if (o instanceof GCPastContent) {
                theVersion = ((GCPastContent)obj).getVersion();
              } else {
                theVersion = 0;
              }

              final VersionKey vkey = new VersionKey(obj.getId(), theVersion);
              final long theVersionF = theVersion;
              final int theSize = getSize(obj);

              if (shouldBeAggregated(obj, theSize)) {
                log("ADDING MISSING AGGRGATE: "+obj.getId());

                waitingList.store(vkey, obj, new Continuation() {
                  public void receiveResult(Object o) {
                    waiting.add(new ObjectDescriptor(obj.getId(), theVersionF, expiration, expiration, theSize));
                  }
                  public void receiveException(Exception e) { 
                    warn("Exception while refreshing aggregate: "+obj.getId()+" (e="+e+")");
                    e.printStackTrace();
                  }
                });
              }
            
              refreshInObjectStore(id, expiration, command);
            } else {
              warn("AGGR: Cannot find refreshed object "+id+", lookup returns "+o);
              command.receiveException(new AggregationException("Object not found: "+id.toStringFull()));
            }
          }
          public void receiveException(Exception e) {
            command.receiveException(e);
          }
        });
      } else {
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
    log("AGGR setHandle("+handle+")");
  
    if (!(handle instanceof Id)) {
      command.receiveException(new AggregationException("Illegal handle"));
      return;
    }
      
    Id newRoot = (Id) handle;
    if (!newRoot.equals(rootKey)) {
      rootKey = newRoot;
      rebuildAggregateList(command);
    } else {
      command.receiveResult(new Boolean(true));
    }
  }

  private long getCurrentExpiration(Id vkey) {
    return System.currentTimeMillis() + 30 * MINUTES; /* YYY */
    /*********** IMPLEMENT ME ************/
  }

  private void rebuildAggregateList(final Continuation command) {
    log("AGGR rebuildAggregateList");
    if (rootKey == null) {
      warn("rebuildAggregateList invoked while rootKey is null");
      command.receiveException(new AggregationException("Set handle first!"));
      return;
    }
    
    final Vector keysToDo = new Vector();
    final Vector keysDone = new Vector();
    keysToDo.add(rootKey);
    
    log("AGGR: Rebuild: Fetching aggregate " + rootKey.toStringFull());
    aggregateStore.lookup(rootKey, new Continuation() {
      Id currentLookup = rootKey;
      
      public void receiveResult(Object o) {
        if (o instanceof Aggregate) {
          keysToDo.remove(currentLookup);
          keysDone.add(currentLookup);

          log("AGGR: Rebuild: Got aggregate " + currentLookup.toStringFull());

          Aggregate aggr = (Aggregate) o;
          ObjectDescriptor[] objects = new ObjectDescriptor[aggr.components.length];
          long aggregateExpiration = getCurrentExpiration(currentLookup);
          
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
            log("AGGR: Rebuild: "+keysToDo.size()+" keys to go, "+keysDone.size()+" done");
            currentLookup = (Id) keysToDo.firstElement();
            log("AGGR: Rebuild: Fetching aggregate " + currentLookup.toStringFull());
            aggregateStore.lookup(currentLookup, this);
          } else {
            writeAggregateList();
            command.receiveResult(new Boolean(true));
          }
        } else {
          receiveException(new AggregationException("Read failed: "+currentLookup+", returned "+o));
        }
      }
      public void receiveException(Exception e) {
        warn("AGGR: Rebuild: Exception "+e);
        e.printStackTrace();
        keysToDo.remove(currentLookup);
        keysDone.add(currentLookup);
        
        if (!keysToDo.isEmpty()) {
          log("AGGR: Trying next key");
          currentLookup = (Id) keysToDo.firstElement();
          log("AGGR: Rebuild: Fetching aggregate " + currentLookup.toStringFull());
          aggregateStore.lookup(currentLookup, this);
        } else {
          if (aggregateList.isEmpty())
            command.receiveException(new AggregationException("Cannot read root aggregate! -- retry later"));
          else
            writeAggregateList();
            command.receiveResult(new Boolean(true));
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
      panic("INSERTED OBJECT IS NOT OF TYPE GCPASTCONTENT!");
      theVersion = 0;
    }

    final VersionKey vkey = new VersionKey(obj.getId(), theVersion);
    final long theVersionF = theVersion;
    final int theSize = getSize(obj);

    if (shouldBeAggregated(obj, theSize)) {
      log("AGGREGATE INSERT: "+obj.getId());

//      objectStore.insert(obj, command);
      waitingList.store(vkey, obj, new Continuation() {
        public void receiveResult(Object o) {
          waiting.add(new ObjectDescriptor(obj.getId(), theVersionF, lifetime, lifetime, theSize));
        }
        public void receiveException(Exception e) { 
          warn("Exception while storing aggregate: "+obj.getId()+" (e="+e+")");
          e.printStackTrace();
        }
      });
    } else {
      log("INSERT WITHOUT AGGREGATION: "+obj.getId());
      
      Continuation c = new Continuation() {
        boolean otherSucceeded = false;
        boolean otherFailed = false;

        public void receiveResult(Object o) {
          synchronized (this) {
            log("AGGR.INSERT "+obj.getId()+" receiveResult("+o+"), otherSucc="+otherSucceeded+" otherFail="+otherFailed);
            if (otherSucceeded) {
              if (!otherFailed) {
                log("--reporting Success");
                command.receiveResult(new Boolean[] { new Boolean(true) });
              }
            } else {
              otherSucceeded = true;
            }
          }
        }
        public void receiveException(Exception e) {
          synchronized (this) {
            log("AGGR.INSERT "+obj.getId()+" receiveException("+e+"), otherSucc="+otherSucceeded+" otherFail="+otherFailed);
            log("--reporting Failure");
            command.receiveException(e);
            otherFailed = true;
          }
        }
      };
      
      objectStore.insert(obj, c);
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
          
          log("Object "+adc.objects[objDescIndex].key+" (#"+objDescIndex+") successfully retrieved from "+adc.key);

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
    log("AGGR: lookup("+id+", cache="+cache+")");
    
    objectStore.lookup(id, cache, new Continuation() {
      public void receiveResult(Object o) {
        if (o != null) {
          log("NL: Found in PAST: "+id);
          command.receiveResult(o);
        } else {
          AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(id);
          if (adc!=null) {
            log("NL: Must retrieve from aggregate");

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
    log("AGGR: lookup("+id+", version="+version+")");

    AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(new VersionKey(id, version));
    if (adc!=null) {
      log("VL: Retrieving from aggregate");

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
              log("VL: Found in VersioningPAST: "+id+"v"+version);
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
  
  public void lookupHandles(final Id id, final int max, final Continuation command) {
    log("AGGR: lookupHandles("+id+","+max+")");
    objectStore.lookupHandles(id, max, new Continuation() {
      public void receiveResult(Object o) {
        PastContentHandle[] result = (o instanceof PastContentHandle[]) ? ((PastContentHandle[])o) : new PastContentHandle[] {};
        boolean foundHandle = false;
        
        for (int i=0; i<result.length; i++)
          if (result[i] != null)
            foundHandle = true;

        if (foundHandle) {
          log("AGGR: lookupHandles("+id+","+max+") handled by PAST; ret="+o);
          command.receiveResult(o);
        } else {
          warn("AGGR: lookupHandles("+id+","+max+") failed, ret="+o+" -- restoring");

          AggregateDescriptor adc = (AggregateDescriptor) aggregateList.get(id);
          if (adc!=null) {
            log("AGGR: lookupHandles: Retrieving from aggregate");

            int objDescIndex = adc.lookupNewest(id);
            if (objDescIndex < 0) {
              warn("AGGR: lookupHandles: Aggregate found, but object not found in aggregate?!? -- aborted");
              command.receiveException(new AggregationException("Inconsistency detected in aggregate list -- try restarting the application"));
              return;
            }
        
            retrieveObjectFromAggregate(adc, objDescIndex, new Continuation() {
              public void receiveResult(Object o) {
                log("AGGR: lookupHandles: Retrieved from aggregate: "+id+", result="+o);
                /* re-inserted implicitly by retrieveObjectFromAggregate */
                objectStore.lookupHandles(id, max, command);
              }
              public void receiveException(Exception e) {
                warn("AGGR: lookupHandles: Cannot retrieve from aggregate, exception "+e);
                command.receiveException(e);
              }
            });
          } else {
            warn("AGGR: lookupHandles: "+id+" is neither in object store nor in aggregate list");
            /* Note that we have to give up here... even if the object has not been 
               aggregated, there is no efficient way to find out its version number.
               The user must call lookupVersion in this case. */
            command.receiveResult(null);
          }
        }
      }
      public void receiveException(Exception e) {
        warn("AGGR: Exception in lookupHandles: "+e);
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
    command.receiveResult(new Boolean(true));
    /* YYY */
  }
  
  public void flush(Continuation command) {
    command.receiveResult(new Boolean(true));
    /* YYY */
  }
  
  public void rollback(Id id, Continuation command) {
    command.receiveResult(new Boolean(true));
    /* YYY */
  }
  
  public void reset(Continuation command) {
    command.receiveResult(new Boolean(true));
    /* YYY */
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
    log("AGGR Received message " + msg + " with destination " + id + " from " + msg.getSource().getId());

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
}
