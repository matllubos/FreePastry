/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.p2p.glacier;

import java.io.*;
import java.util.*;
import java.util.logging.* ;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.PastMessage;
import rice.p2p.replication.*;
import rice.p2p.replication.manager.*;
import rice.p2p.glacier.messaging.*;
import rice.p2p.glacier.*;
import rice.p2p.multiring.*;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.dist.DistPastryNode;
import rice.pastry.leafset.LeafSet;
import rice.persistence.*;

/**
 * @(#) GlacierImpl.java
 *
 * This is an implementation of the Glacier interface.
 *
 * @version $Id$
 * @author Andreas Haeberlen
 */
 
class InsertListEntry {
  public FragmentKey key;
  public Fragment[] fragments;
  public StorageManifest manifest;
  public boolean holderKnown[];
  public boolean receiptReceived[];
  public NodeHandle holder[];
  public long timeout;
  public boolean insertsSent;
  public NodeHandle localNodeHandle;
  
  public InsertListEntry(FragmentKey key, Fragment[] fragments, StorageManifest manifest, long timeout, int numInitialFragments, NodeHandle localNodeHandle)
  {
    this.key = key;
    this.fragments = fragments;
    this.manifest = manifest;
    this.timeout = timeout;
    this.insertsSent = false;
    this.localNodeHandle = localNodeHandle;
    this.holderKnown = new boolean[numInitialFragments];
    Arrays.fill(this.holderKnown, false);
    this.receiptReceived = new boolean[numInitialFragments];
    Arrays.fill(this.receiptReceived, false);
    this.holder = new NodeHandle[numInitialFragments];
    for (int i=0; i<numInitialFragments; i++)
      this.holder[i] = null;
  }
}

class HandoffListEntry {
  public StorageKey skey;
  public Id destination;
  public StorageManifest manifest;
  public Fragment fragment;
  public boolean isRestoredFragment;
  
  public HandoffListEntry(StorageKey skey, Id destination, boolean isRestoredFragment, Fragment fragment, StorageManifest manifest)
  {
    this.skey = skey;
    this.destination = destination;
    this.isRestoredFragment = isRestoredFragment;
    this.fragment = fragment;
    this.manifest = manifest;
  }
}

class RestoreListEntry {
  final static int stAskingPrimary = 1;
  final static int stWaitingForPrimary = 2;
  final static int stCollectingFragments = 3;
  final static int stRecoding = 4;
  final static int stCanceled = 5;
  
  public FragmentKey key;
  int fragmentID;
  int status;
  FileInfo fileInfo;
  int attemptsLeft;
  boolean checkedFragment[];
  Fragment haveFragment[];
  
  public RestoreListEntry(FragmentKey key, int fragmentID, int status, FileInfo fileInfo, int attemptsLeft, int numFragments)
  {
    this.key = key;
    this.fragmentID = fragmentID;
    this.status = status;
    this.fileInfo = fileInfo;
    this.attemptsLeft = attemptsLeft;
    this.checkedFragment = new boolean[numFragments];
    this.haveFragment = new Fragment[numFragments];
    for (int i=0; i<numFragments; i++) {
      this.checkedFragment[i] = false;
      this.haveFragment[i] = null;
    }
  }
  
  public void markChecked(int fid)
  {
    this.checkedFragment[fid] = true;
  }
  
  public void addFragment(int fid, Fragment frag)
  {
    this.haveFragment[fid] = frag;
  }
  
  public int numFragmentsAvailable()
  {
    int result = 0;
  
    for (int i=0; i<haveFragment.length; i++)
      if (haveFragment[i] != null)
        result ++;
    
    return result;
  }
}

public class GlacierImpl extends PastImpl implements Glacier, Past, Application, ReplicationManagerClient {

  private final char tiInsert = 1;
  private final char tiStatusCast = 2;
  private final char tiAudit = 3;
  private final char tiRestore = 4;
  private final char tiHandoff = 5;
  
  protected StorageManager storage;
  
  protected Node node;
  
  protected int numFragments;
  protected int numSurvivors;
  protected int numInitialFragments;
  
  protected ErasureCodec codec;
  protected GlacierState state;
  protected Hashtable insertList;
  protected Hashtable auditList;
  protected Hashtable handoffList;
  protected Hashtable restoreList;
  protected LinkedList stickyQueue;
  protected String configDir;

  private int id;

  private Hashtable timers;
  
  private MultiringIdFactory factory;

  private boolean insertTimeoutActive;

  private int SECONDS = 1000;
  private int MINUTES = 60*SECONDS;
  
  private int insertTimeout = 30*SECONDS;
//  private int statusCastInterval = 60*MINUTES;
//  private int statusCastMinDelay = 40*MINUTES;
  private int statusCastInterval = 5*MINUTES;
  private int statusCastMinDelay = 3*MINUTES;
  private int statusCastJitter = 20*MINUTES;

  private int maxConcurrentAudits = 3;
  private int auditTimeout = 20*SECONDS;
  private int restoreCycle = 20*SECONDS;
  private int handoffTimeout = 20*SECONDS;
  private int numIncludePreviousStatusCasts = 1;
  private int maxConcurrentRestores = 100;
  private float maxRestoreAttemptFactor = (float)2.0;
  private int deadHolderEntryTimeout = (int)(1.2*statusCastInterval); /* msec */
  private int uncertainHolderEntryTimeout = (int)(3.2*statusCastInterval); /* msec */
  private int certainHolderEntryTimeout = (int)(4.2*statusCastInterval);
  private int maxAuditAttempts = 2;
  private int stickyPacketLifetime = 3;
  private float maxRestoreFromFragmentFactor = (float)2.0;
  private String configFileName = ".glacier-config";
  private String stateFileName = ".glacier-state";
  private Date nextStatusCastDate;

  public GlacierImpl(Node node, String configDir, StorageManager pastManager, StorageManager glacierManager, int replicas, int numFragments, int numSurvivors, MultiringIdFactory factory, String instance) 
  {
    super(node, pastManager, replicas, instance);

    /* Initialize internal variables */

    this.storage = glacierManager;
    this.node = node;
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
    this.numInitialFragments = 2*numSurvivors;
    this.insertTimeoutActive = false;
    this.codec = new ErasureCodec(numFragments, numSurvivors);
    this.factory = factory;
    this.id = 0;

    /* Read Glacier's current state from the configuration file. If the file is
       not found, start with a blank configuration. */
    
    File configDirF = new File(this.configDir);
    if (!configDirF.exists())
      configDirF.mkdir();
    
    String configFilePath = configDir + "/" + configFileName;
    File configFile = new File(configFilePath);
    if (configFile.exists()) {
      try {
        FileInputStream fis = new FileInputStream(configFile);
        ObjectInputStream ois = new ObjectInputStream(fis);
        log("Reading configuration from "+configFilePath);
        state = (GlacierState)ois.readObject();
        ois.close();
      } catch (Exception e) {
        System.err.println("GlacierImpl cannot read configuration file: " + configFilePath);
        e.printStackTrace();
        System.exit(1);
      }
    } 
    
    if (state == null)
      state = new GlacierState();
    
    scheduleNextStatusCast();
  }

  private void assume(boolean val) throws Error
  {
    if (!val) {
      try {
        throw new Error("assertion failed");
      } catch (Error e) {
        warn("Assertion failed: "+e);
      }
    }
//      throw new Error("assertion failed");
  }

  private void panic(String s) throws Error
  {
    System.err.println("PANIC: "+s);
    throw new Error("Panic");
  }

  private void checkStorage()
  {
    Enumeration enu = state.fileList.elements();
    while (enu.hasMoreElements()) {
      FileInfo fi = (FileInfo)enu.nextElement();
      for (int i=0; i<numFragments; i++) {
        for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
          if (fi.holderKnown[i][j] && !fi.holderDead[i][j] && (fi.holderId[i][j] == null)) {
            final StorageKey sk = new StorageKey(fi.key, i);
            
            storage.getObject(sk, new Continuation() {
              public void receiveResult(Object o) {
                if (o != null) {
                  if (o instanceof Fragment) {
                    log("Fragment "+sk+" found in local storage");
                  } else log("Fragment "+sk+" has wrong type");
                } else {
                  log("Fragment "+sk+" not found in local storage");
                }
              }
              public void receiveException(Exception e) {
                panic("Fetch returned exception "+e);
              }
            });
          }
        }
      }
    } 
  }

  public GlacierState getState()
  {
    return state;
  }

  public int getNumFragments()
  {
    return numFragments;
  }
  
  public Date getNextStatusCastDate()
  {
    return nextStatusCastDate;
  }

  private void scheduleNextStatusCast()
  {
    Calendar cal = Calendar.getInstance();
    Random rand = new Random();
    long nowMS, lastStatusCastMS, delayMS;

    /* Convert the Date values to milliseconds so that we can compute 
       differences easily */
    
    cal.setTime(new Date());
    nowMS = cal.getTimeInMillis();
    cal.setTime(state.lastStatusCast);
    lastStatusCastMS = cal.getTimeInMillis();

    /* The next status cast should happen after statusCastInterval
       milliseconds, plus or minus a certain jitter term (to prevent
       synchronization effects) */
    
    delayMS = (lastStatusCastMS+statusCastInterval) - nowMS;
    delayMS += (rand.nextInt(2*statusCastJitter) - statusCastJitter);
    
    /* There must be a minimum delay between two status casts */
    
    if (delayMS < statusCastMinDelay)
      delayMS = statusCastMinDelay;
    
    /* Schedule a TimeoutMessage as a reminder for the next status cast */
    
    addTimer((int)delayMS, tiStatusCast);
    cal.setTimeInMillis(nowMS + delayMS);
    nextStatusCastDate = cal.getTime();

    log("Scheduling next status cast at "+nextStatusCastDate+" (in "+delayMS+" msec)");
  }

  public void refreeze()
  {
    Date now = new Date();

    /* In the event of a refreeze (for testing purposes), we do
       the following:
       
          1) Clear the history
          2) Mark all local fragments as newly acquired
          3) Set all remote fragments to Uncertain
          
       This ensures that the system can be restarted after
       a global crash with partial data loss; the next series
       of status casts will change the surviving fragments
       back to Certain. */
    
    log("REFREEZING");
    state.history = new LinkedList();

    Enumeration enu = state.fileList.elements();
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo)enu.nextElement();
        
      for (int i=0; i<numFragments; i++) {
        for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j]) {
            if (!fileInfo.holderDead[i][j]) {
              if (fileInfo.holderId[i][j] == null) {
                recordNewFragmentEvent(fileInfo.key, i);
              } else {
                fileInfo.holderCertain[i][j] = false;
              }
              fileInfo.lastHeard[i][j] = now;
            } else fileInfo.holderKnown[i][j] = false;
          }
        }
      }
    }
    
    /* Also, reset the timer on all the known fragment holders
       to prevent fragments from being deleted just because their
       holder seems to have failed. If a holder is really down,
       its entry will eventually expire again. */
    
    enu = state.holderList.elements();
    while (enu.hasMoreElements()) {
      HolderInfo holderInfo = (HolderInfo)enu.nextElement();
      holderInfo.lastHeardOf = now;
    }
  }

  private void syncState()
  {
    /* Write the current state to the configuration file */
  
    String configFilePath = configDir + "/" + configFileName;
    try {
      FileOutputStream fos = new FileOutputStream(configFilePath);
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      oos.writeObject(state);
      oos.close();
    } catch (IOException ioe) {
      System.err.println("GlacerImpl cannot write to its configuration file: "+configFilePath+" ("+ioe+")");
      ioe.printStackTrace();
      System.exit(1);
    }

    /* Write a plain-text version for debugging purposes */
    
    String dumpFileName = configDir+"/"+stateFileName;
    try {
      PrintStream dumpFile = new PrintStream(new FileOutputStream(dumpFileName));
      Enumeration enu = state.fileList.elements();
    
      dumpFile.println("FileList at "+getLocalNodeHandle().getId()+" (at "+(new Date())+"):");
      while (enu.hasMoreElements()) {
        FileInfo fileInfo = (FileInfo)enu.nextElement();
        
        dumpFile.println(" - File "+fileInfo.key);
        for (int i=0; i<numFragments; i++) {
          for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
            if (fileInfo.holderKnown[i][j]) {
              String holderInfo = "(this node)";
              if (fileInfo.holderId[i][j] != null)
                holderInfo = "" + fileInfo.holderId[i][j];

              dumpFile.println("    * Fragment "+i+" at "+holderInfo
                +(fileInfo.holderCertain[i][j] ? " certainly " : " probably ")
                +(fileInfo.holderDead[i][j] ? "dead" : "alive")
                +" ("+fileInfo.lastHeard[i][j]+")"
              );
            }
          }
        }
      }
    } catch (IOException ioe) {
      System.err.println("GlacerImpl cannot write to its dump file: "+dumpFileName+" ("+ioe+")");
      ioe.printStackTrace();
      System.exit(1);
    }
  }
  
  private void log(String str)
  {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h+":"+m+":"+s+" @"+node.getId()+" "+str);
  }
  
  private void warn(String str)
  {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h+":"+m+":"+s+" @"+node.getId()+" *** WARNING *** "+str);
  }
  
  private void unusual(String str)
  {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date());
    int h = c.get(Calendar.HOUR);
    int m = c.get(Calendar.MINUTE);
    int s = c.get(Calendar.SECOND);
    System.out.println(h+":"+m+":"+s+" @"+node.getId()+" *** UNUSUAL *** "+str);
  }
  
  protected synchronized int getUID()
  {
    return this.id++;
  }

  private void addTimer(int timeoutMsec, char timeoutID)
  {
    assume(timeoutID < 10);

    /* We schedule a GlacierTimeoutMessage with the ID of the
       requested timer. This message will be delivered if the
       timer expires and it has not been removed in the meantime. */
  
    TimerTask timer = endpoint.scheduleMessage(new GlacierTimeoutMessage(timeoutID, getLocalNodeHandle()), timeoutMsec);
    timers.put(new Integer(timeoutID), timer);
  }
  
  private void removeTimer(int timeoutID)
  {
    TimerTask timer = (TimerTask) timers.remove(new Integer(timeoutID));
    
    if (timer != null)
      timer.cancel();
  }
  
  private void determineRestoreJobs()
  {
    assume(restoreList.isEmpty());

    /* If a file entry is still in the AuditList at this point,
       its primary replica has not responded to any of our 
       queries (we retry several times if there is no response). 
       In this case, the object is most likely lost, and we
       must recover it from its fragments. */

    Enumeration enu = auditList.elements();
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo)enu.nextElement();
      unusual("No audit response, must restore "+fileInfo.key);

      /* Add an entry to the RestoreList */

      final RestoreListEntry rle = new RestoreListEntry(
        fileInfo.key, 0, RestoreListEntry.stWaitingForPrimary,
        fileInfo, 0, numFragments
      );
        
      /* There must be at least one fragment available locally
         (otherwise Glacier would not know about this file).
         Add these fragments to the RestoreList entry. */
        
      for (int i=0; i<numFragments; i++) {
        for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] && (fileInfo.holderId[i][j] == null)) {
            final int thisFragment = i;
            rle.markChecked(thisFragment);
            storage.getObject(new StorageKey(fileInfo.key, i), new Continuation() {
              public void receiveResult(Object o) {
                log("Read and added fragment "+rle.key+":"+thisFragment);
                rle.addFragment(thisFragment, (Fragment)o);
              }  
              public void receiveException(Exception e) {
                warn("Cannot read fragment: "+rle.key+":"+thisFragment+", exception "+e);
                e.printStackTrace();
              }
            });
          }
        }
      }

      restoreList.put(fileInfo.key, rle);
    }
    
    /* Clear the AuditList; every entry should have been dealt 
       with now */
    
    auditList.clear();
    
    /* Check whether we need to restore any individual fragments. */
    
    if (restoreList.size() < maxConcurrentRestores) {
      Vector restoreCandidates = new Vector();
      Random rand = new Random();
      boolean missingFragment[] = new boolean[numFragments];
      Enumeration enu2 = state.fileList.elements();

      /* Build a list of missing fragments. The list should contain
         at most one missing fragment for each file. If more than
         one fragment is missing, we choose a random one to minimize
         collisions. */

      while (enu2.hasMoreElements()) {
        FileInfo fileInfo = (FileInfo)enu2.nextElement();
        int liveFragments = 0;

        /* Count the live fragments */

        for (int i=0; i<numFragments; i++) {
          missingFragment[i] = !fileInfo.anyLiveHolder(i);
          if (!missingFragment[i])
            liveFragments ++;
        }
          
        /* Recovery only makes sense if we know enough live fragments */
          
        if ((liveFragments >= numSurvivors) && (liveFragments < numFragments)) {
          int fragmentID;
          do {
            fragmentID = rand.nextInt(numFragments);
          } while (!missingFragment[fragmentID]);
        
          RestoreListEntry rle = new RestoreListEntry(
            fileInfo.key, fragmentID, RestoreListEntry.stAskingPrimary,
            fileInfo, (int)(maxRestoreAttemptFactor * numFragments),
            numFragments
          );
        
          restoreCandidates.add(rle);
        }
      }
    
      /* Pick random entries from the list of missing fragments and add
         them to the RestoreList. Note that at this point, the RestoreList
         may already contain entries for lost replicas. */
    
      while ((restoreList.size() < maxConcurrentRestores) && (restoreCandidates.size()>0)) {
        int index = rand.nextInt(restoreCandidates.size());
        final RestoreListEntry rle = (RestoreListEntry)restoreCandidates.elementAt(index);
        FileInfo fileInfo = rle.fileInfo;

        /* Get a random entry */

        restoreCandidates.removeElementAt(index);
        log("decides to restore "+fileInfo.key+":"+rle.fragmentID);

        /* Load any fragments that may be available locally, and mark
           them as checked (so that they are not requested from other
           holders later). */

        for (int i=0; i<numFragments; i++) {
          for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
            if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] && (fileInfo.holderId[i][j] == null)) {
              final int thisFragment = i;
              rle.markChecked(thisFragment);
              storage.getObject(new StorageKey(fileInfo.key, i), new Continuation() {
                public void receiveResult(Object o) {
                  log("Read and added fragment "+rle.key+":"+thisFragment);
                  rle.addFragment(thisFragment, (Fragment)o);
                }  
                public void receiveException(Exception e) {
                  warn("Cannot read fragment: "+rle.key+":"+thisFragment+", exception "+e);
                  e.printStackTrace();
                }
              });
            }
          }
        }

        /* Add the entry to the RestoreList */

        restoreList.put(fileInfo.key, rle);
      }
      
      restoreCandidates.removeAllElements();
    }
  }
  
  private boolean restoreStep()
  {
    log("RestoreStep");

    /* Try to make progress on each entry in the RestoreList */

    Enumeration enu = restoreList.elements();
    int idx = 0;
    while (enu.hasMoreElements()) {
      RestoreListEntry rle = (RestoreListEntry)enu.nextElement();
      boolean madeProgress = false;
      
      log("Processing key "+rle.key+":"+rle.fragmentID+" (#"+(idx++)+")");
      
      /* If the entry is the AskingPrimary state, no request has been
         sent yet. We send a request for the fragment to the primary
         replica. */
      
      if (rle.status == RestoreListEntry.stAskingPrimary) {
        rle.status = RestoreListEntry.stWaitingForPrimary;
        rle.attemptsLeft = maxAuditAttempts;
        madeProgress = true;
      }
      
      /* If the entry is in the WaitingForPrimary state, at least one
         request has been sent, but no answer has been received so far.
         If the maximum number of requests has not yet been exceeded,
         we send another request; otherwise the primary replica is 
         probably dead, and we recover it from the fragments. */
      
      if (rle.status == RestoreListEntry.stWaitingForPrimary) {
      
        /* If possible, retry */
      
        if (rle.attemptsLeft > 0) {
          log("Sending audit for "+rle.key+" ("+(rle.attemptsLeft-1)+" attempts left)");
          endpoint.route(
            rle.key.getId(), 
            new GlacierFetchMessage(
              getUID(), rle.key, rle.fragmentID,
              getLocalNodeHandle(), rle.key.getId()
            ),
            null
          );
          rle.attemptsLeft --;
        } else {
        
          /* If the primary appears to be dead, count the number of
             live fragments */
        
          int numFragmentsKnown = 0;
          for (int i=0; i<numFragments; i++)
            if (rle.fileInfo.anyLiveHolder(i))
              numFragmentsKnown ++;
              
          /* If we know enough live fragments, begin restoring the
             object; otherwise cancel this entry, since we cannot
             succeed at this time. In the rare event where enough
             fragments are available locally, we can recover the
             object directly. */
              
          if (numFragmentsKnown >= numSurvivors) {
            if (rle.numFragmentsAvailable() >= numSurvivors) {
              unusual("Primary seems to have failed, restoring "+rle.key+":"+rle.fragmentID+" from fragments available locally ("+rle.numFragmentsAvailable()+")");
              rle.status = RestoreListEntry.stRecoding;
            } else {
              unusual("Primary seems to have failed, attempting to restore "+rle.key+":"+rle.fragmentID+" from fragments");
              rle.status = RestoreListEntry.stCollectingFragments;
              rle.attemptsLeft = (int)(maxRestoreFromFragmentFactor * numSurvivors);
            }
          } else rle.status = RestoreListEntry.stCanceled;
        }
        
        madeProgress = true;
      }
      
      /* If the entry is in the CollectingFragments state, the primary
         has failed, but there are not enough fragments available yet
         to restore the object. We pick a random fragment holder that
         has not been asked yet and send a request for its fragment. 
         If we exceed the maximum number of requests, or if all fragment
         holders have been tried, we give up and set the entry to
         Cancelled. */ 
      
      if (rle.status == RestoreListEntry.stCollectingFragments) {
        if (rle.attemptsLeft > 0) {
          int[] candidateIndex = new int[numFragments * FileInfo.maxHoldersPerFragment];
          int[] candidateSubindex = new int[numFragments * FileInfo.maxHoldersPerFragment];
          int numCandidates = 0;
        
          /* Build a list of fragments we might request */
        
          for (int i=0; i<numFragments; i++) {
            if ((rle.haveFragment[i] == null) && !rle.checkedFragment[i]) {
              for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
                if (rle.fileInfo.holderKnown[i][j] && !rle.fileInfo.holderDead[i][j] && (rle.fileInfo.holderId[i][j] != null)) {
                  candidateIndex[numCandidates] = i;
                  candidateSubindex[numCandidates] = j;
                  numCandidates ++;
                }
              }
            }
          }
        
          /* If there is at least one such fragment, pick a random one... */
        
          if (numCandidates >= 1) {
            int index = (new Random()).nextInt(numCandidates);
            Id theHolder = rle.fileInfo.holderId[candidateIndex[index]][candidateSubindex[index]];
          
            rle.checkedFragment[candidateIndex[index]] = true;
            rle.attemptsLeft --;
            log("RESTORE fetching fragment #"+candidateIndex[index]+" for "+rle.key+":"+rle.fragmentID+" (attempts left: "+rle.attemptsLeft+")");
          
            /* ... and send a request to its holder... */
          
            endpoint.route(
              theHolder, 
              new GlacierFetchMessage(
                getUID(), rle.key, candidateIndex[index],
                getLocalNodeHandle(), theHolder
              ),
              null
            );
          } else {
          
            /* ... otherwise give up and cancel this entry. */
          
            unusual("RESTORE giving up on "+rle.key+":"+rle.fragmentID+", no more candidates");
            rle.status = RestoreListEntry.stCanceled;
          }
        } else {
        
          /* The entry is also canceled if we exceed the maximum number
             of requests. */
        
          unusual("RESTORE giving up on "+rle.key+":"+rle.fragmentID+", attempt limit reached");
            rle.status = RestoreListEntry.stCanceled;
        }
        
        madeProgress = true;
      }

      /* If the entry is in the Recoding state, the primary replica is
         dead, but we have found enough fragments (locally or remotely)
         to recover the object. We invoke the erasure codec and then
         hand the restored object over to PAST. */

      if (rle.status == RestoreListEntry.stRecoding) {
        log("RESTORE Recoding "+rle.key);
        assume(rle.numFragmentsAvailable() >= numSurvivors);
        
        Fragment[] material = new Fragment[numSurvivors];
        int index = 0;
        
        for (int i=0; i<numFragments; i++)
          if ((rle.haveFragment[i] != null) && (index<numSurvivors))
            material[index++] = rle.haveFragment[i];
            
        log("Decode object: "+rle.key);
        Object o = codec.decode(material);
        log("Decode complete: "+rle.key);
        
        boolean proceed = true;
        if (o == null) {
          warn("Decoder failed to decode "+rle.key);
          proceed = false;
        } else if (!(o instanceof PastContent)) {
          warn("Decoder delivered something other than PastContent");
          proceed = false;
        }
         
        if (proceed) { 
          final RestoreListEntry frle = rle;
          super.insert((PastContent)o, new Continuation() {
            public void receive(Object result) throws Exception {
              warn("receive() "+result+" -- unexpected, ignored (key="+frle.key+")");
            }
            public void receiveException(Exception e) {
              warn("receiveException() "+e+" -- unexpected, ignored (key="+frle.key+")");
            }
            public void receiveResult(Object o) {
              log("Primary reinserted ok: "+frle.key);
            }
          });
        }

        /* We do NOT re-insert the fragment we originally wanted to restore. If this is
          added, make sure that the case of missing audit responses is handled properly
          (currently these entries pretend to be restoring fragment #0) */
        
        rle.status = RestoreListEntry.stCanceled;
        madeProgress = true;
      }
      
      if (!madeProgress) {
        warn("Did NOT make progress on RLE entry with status "+rle.status);
        panic("NO PROGRESS");
      }
    }

    /* Finally, we remove all canceled entries from the RestoreList.
       This could have done earlier, but the Java spec is a bit unclear
       about removing entries while an Enumeration is being used.
       Better be on the safe side... */

    enu = restoreList.elements();
    while (enu.hasMoreElements()) {
      RestoreListEntry rle = (RestoreListEntry)enu.nextElement();
      if (rle.status == RestoreListEntry.stCanceled) {
        log("Entry "+rle.key+":"+rle.fragmentID+" canceled, removing...");
        restoreList.remove(rle.key);
        enu = restoreList.elements();
      }
    }
    
    return !restoreList.isEmpty();
  }

  private boolean betterThan(Id current, Id candidate, Id reference)
  {
    /* Determine if <candidate> is closer to <reference> than <current>.
       This is more complicated that it seems, because Id might actually
       be a pastry.Id (simulator) or a RingId (ePost deployment) */
  
    if (current instanceof rice.pastry.Id) {
      return (((rice.pastry.Id)current).distance((rice.pastry.Id)reference).compareTo(((rice.pastry.Id)candidate).distance((rice.pastry.Id)reference))>0);
    } else {
      rice.pastry.Id pCurrent = (rice.pastry.Id)((RingId)current).getId();
      rice.pastry.Id pCandidate = (rice.pastry.Id)((RingId)candidate).getId();
      rice.pastry.Id pReference = (rice.pastry.Id)((RingId)reference).getId();
      return betterThan(pCurrent, pCandidate, pReference);
    }
  }

  private void determineHandoffJobs()
  {
    /* First, determine the leaf set of this node */
  
    LeafSet leafSet;
    if (node instanceof MultiringNode)
      leafSet = ((DistPastryNode)((MultiringNode)node).getNode()).getLeafSet();
      else leafSet = ((PastryNode)node).getLeafSet();

    int leafSetSize = leafSet.size();

    /* Look at each local fragment and check whether the nodeID of a
       leaf set member is closer to its 'final destination' than ours.
       If so, we need to hand it over to that leaf set member. */

    Enumeration enu = state.fileList.elements();
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo)enu.nextElement();
      
      for (int i=0; i<numFragments; i++) {
        boolean shouldMove = false;
        Id best = getLocalNodeHandle().getId();
        Id fkey = getFragmentLocation(fileInfo.key.getId(), i);
        
        if (node instanceof MultiringNode)
          best = factory.buildRingId(((MultiringNode)node).getRingId(), ((RingId)(node.getId())).getId());
        
        for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] && (fileInfo.holderId[i][j] == null)) {
            for (int k=0; k<leafSetSize; k++) {
              NodeHandle nh = leafSet.get(k);
              if (nh!=null) {
                Id nhId = nh.getId();
                if (node instanceof MultiringNode)
                  nhId = factory.buildRingId(((MultiringNode)node).getRingId(), nh.getId());
              
                if (betterThan(best, nhId, fkey)) {
                  best = nhId;
                  shouldMove = true;
                }
              }
            }
          }
        }
        
        /* If there is a better node, load the fragment from disk and 
           add it to the HandoffList. */
        
        if (shouldMove) {
          log(fileInfo.key.getId()+":"+i+" ("+fkey+") should move from "+getLocalNodeHandle()+" to "+best);
          final StorageKey skey = new StorageKey(fileInfo.key, i);
          final Id nbest = best;
          final FileInfo nfileInfo = fileInfo;
          storage.getObject(skey, new Continuation() {
            public void receiveResult(Object o) {
              log("PUT for "+skey);
              handoffList.put(skey, new HandoffListEntry(skey, nbest, false, (Fragment)o, nfileInfo.manifest));
            }
            public void receiveException(Exception e) {
              warn("Cannot read fragment: "+skey+", exception "+e);
              e.printStackTrace();
            }
          });
          break;
        }
      }
    }
  }
  
  private void triggerHandoffs()
  {
    /* For each entry in the HandoffList, send a QueryMessage to the
       destination. When a negative response is received, the HandoffList
       will be checked, and the fragment will actually be handed over. */
  
    Enumeration enu = handoffList.elements();
    while (enu.hasMoreElements()) {
      HandoffListEntry hle = (HandoffListEntry)enu.nextElement();
      log("Attempting to handoff "+hle.skey+" to "+hle.destination);

      endpoint.route(
        hle.destination, 
        new GlacierQueryMessage(
          getUID(), hle.skey.getFragmentKey(),
          hle.skey.getFragmentID(), getLocalNodeHandle(), 
          hle.destination
        ),
        null
      );
    } 
  }
  
  private void pruneHistory()
  {
    boolean reachedEnd = false;
    while (!reachedEnd) {
      try {
        HistoryEvent hev = (HistoryEvent)state.history.getFirst();
        if (hev.sequenceNo < (state.currentSequenceNo-numIncludePreviousStatusCasts))
          state.history.removeFirst();
        else
          reachedEnd = true;
      } catch (NoSuchElementException nsee) {
        reachedEnd = true;
      }
    }
  }
  
  private void statusCast()
  {
    log("StatusCast #"+state.currentSequenceNo);

    int numHolders = state.holderList.size();
    Id holder[] = new Id[numHolders];
    HolderInfo holderInfo[] = new HolderInfo[numHolders];
    Vector holderLog[] = new Vector[numHolders];
    boolean holderGetsFullList[] = new boolean[numHolders];

    /* Load the holder entries from the HolderList, and determine whether
       the holder gets a diff or the full list. */
    
    Enumeration enu = state.holderList.elements();
    boolean fullListNeeded = false;
    int index = 0;
    while (enu.hasMoreElements()) {
      HolderInfo hi = (HolderInfo)enu.nextElement();
      holder[index] = hi.nodeID;
      holderLog[index] = new Vector();
      holderInfo[index] = hi;
      holderGetsFullList[index] = (hi.lastAckedSequenceNo == -1) || 
        (hi.lastAckedSequenceNo < (state.currentSequenceNo - numIncludePreviousStatusCasts -1));
      fullListNeeded |= holderGetsFullList[index];
      index ++;
    }
   
    /* For each history entry, find out to which holders it is relevant,
       and add it to the corresponding holderLogs. */
   
    if (!state.history.isEmpty()) {
      ListIterator li = state.history.listIterator(0);
      while (li.hasNext()) {
        HistoryEvent event = (HistoryEvent)li.next();
        log("Parsing " + event);
        
        FileInfo fileInfo = (FileInfo)state.fileList.get(event.key);
        if (fileInfo != null) {
          for (int i=0; i<numHolders; i++) {
            if (!holderGetsFullList[i]) {
              boolean relevant = false;
              for (int j=0; j<numFragments; j++) {
                for (int k=0; k<FileInfo.maxHoldersPerFragment; k++) {
                  if (fileInfo.holderKnown[j][k] &&
                    !fileInfo.holderDead[j][k] &&
                    (fileInfo.holderId[j][k] != null))
                    if (holder[i].equals(fileInfo.holderId[j][k]))
                      relevant = true;
                }
              }
                    
              if (relevant)
                holderLog[i].add(event);
            }
          }
        } else warn("File record disappeared?!?");
      }
    }
    
    /* For those holders who get a full list, generate that list.
       These lists are specific to each holder because they only
       include relevant entries. */
    
    Enumeration enf = state.fileList.elements();
    while (enf.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo)enf.nextElement();
      for (int i=0; i<numHolders; i++) {
        if (holderGetsFullList[i]) {
          boolean relevant = false;
          for (int j=0; j<numFragments; j++) {
            for (int k=0; k<FileInfo.maxHoldersPerFragment; k++) {
              if (fileInfo.holderKnown[j][k] &&
                !fileInfo.holderDead[j][k] &&
                (fileInfo.holderId[j][k] != null))
                if (holder[i].equals(fileInfo.holderId[j][k]))
                  relevant = true;
            }
          }
          
          /* If the file is relevant, add an entry for EACH fragment! */
          
          if (relevant) {
            for (int j=0; j<numFragments; j++) {
              for (int k=0; k<FileInfo.maxHoldersPerFragment; k++) {
                if (fileInfo.holderKnown[j][k] && !fileInfo.holderDead[j][k]) {
                  holderLog[i].add(
                    new HistoryEvent(
                      (fileInfo.holderId[j][k] == null) ? HistoryEvent.evtAcquired : HistoryEvent.evtNewHolder,
                      fileInfo.key,
                      j,
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
                      
    /* Send a StatusMessage to each holder */
                      
    for (int i=0; i<numHolders; i++) {
      log("Sending to holder "+holder[i]+": (full="+holderGetsFullList[i]+")");

      HistoryEvent[] evtList = new HistoryEvent[holderLog[i].size()];
      Enumeration enup = holderLog[i].elements();
      int idx = 0;
      
      while (enup.hasMoreElements()) {
        HistoryEvent evt = (HistoryEvent)enup.nextElement();
        log("tells "+holder[i]+" "+evt);
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
    
    /* Increment our local sequence number, and remove old entries from
       the history */
    
    state.currentSequenceNo ++;
    pruneHistory();
  }
  
  private Id getFragmentLocation(Id objectKey, int fragmentNr)
  {
    if (objectKey instanceof rice.pastry.Id) {
      rice.pastry.Id realId = (rice.pastry.Id)objectKey;
      double f = 0;
      double d;
    
      d = 0.5;
      for (int i=0; i<realId.IdBitLength; i++) {
        if (realId.getDigit((realId.IdBitLength-1-i), 1) > 0)
          f += d;
        d /= 2;
      }
    
      f += (1.0+fragmentNr)/(numFragments+1.0);
      while (f>=1)
        f -= 1;
      
      rice.pastry.Id result = new rice.pastry.Id();
      d = 0.5;
      for (int i=0; i<realId.IdBitLength; i++) {
        if (f>=d) {
          result.setBit((realId.IdBitLength-1-i), 1);
          f -= d;
        }
        d /= 2;
      }
    
      return result;
    } else {
      RingId rok = (RingId)objectKey;
      return factory.buildRingId(rok.getRingId(), getFragmentLocation(rok.getId(), fragmentNr));
    }
  }

  private void auditPrimaryReplicas()
  {
    Random rand = new Random();
    
    /* Pick a few random entries from the fileList and them to the AuditList */
    
    if (!state.fileList.isEmpty()) {
      for (int i=0; i<maxConcurrentAudits; i++) {
        int index = rand.nextInt(state.fileList.size());
        Enumeration enum = state.fileList.elements();
        while ((--index) > 0)
          enum.nextElement();
        
        FileInfo fileInfo = (FileInfo)enum.nextElement();
        if (!auditList.containsKey(fileInfo.key)) {
          log("decides to audit "+fileInfo.key);
          auditList.put(fileInfo.key, fileInfo);
        }
      }
    }
    
    /* Make PAST send LookupHandle messages to the primary replicas, and
       remove the entries from the AuditList when we get a response */
    
    Enumeration enum = auditList.keys();
    while (enum.hasMoreElements()) {
      final FragmentKey key = (FragmentKey)enum.nextElement();
      
      lookupHandles(key.getId(), 1, new Continuation() {
        public void receiveResult(Object o) {
          PastContentHandle[] handles = (PastContentHandle[]) o;
          if ((handles.length>0) && (handles[0] != null)) {
            auditList.remove(key);
            log("Got audit response from "+key);
          }
        }
        public void receiveException(Exception e) {
          warn("receiveException("+e+") during audit -- unexpected, ignored (key="+key+")");
        }
      });
    }
  }

  private void expireOldPackets()
  {
    ListIterator sqi = stickyQueue.listIterator(0);

    while (sqi.hasNext()) {
      GlacierStatusMessage lsm = (GlacierStatusMessage) sqi.next();
      lsm.remainingLifetime --;
      if (lsm.remainingLifetime <= 0) {
        log("STICKY expired packet "+lsm);
        sqi.remove();
      }
    }
  }

  private void expireOldHolders()
  {
    Vector expireList = new Vector();

    Calendar cal = Calendar.getInstance();
    cal.setTime(new Date());
    long tnow = cal.getTimeInMillis();
    
    Enumeration e1 = state.holderList.elements();
    while (e1.hasMoreElements()) {
      HolderInfo hle = (HolderInfo)e1.nextElement();
      hle.numReferences = 0;
      hle.numLiveReferences = 0;

      cal.setTime(hle.lastHeardOf);
      long tdelay = tnow - cal.getTimeInMillis();
      if (tdelay > certainHolderEntryTimeout) {
        log("Expiring HOLDER "+hle.nodeID+" (last heard of "+hle.lastHeardOf+")");
        expireList.add(hle.nodeID);
      }
    }
    
    Enumeration e2 = state.fileList.elements();
    while (e2.hasMoreElements()) {
      FileInfo fi = (FileInfo)e2.nextElement();
      for (int i=0; i<numFragments; i++) {
        for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
          if (fi.holderKnown[i][j]) {
            if ((fi.holderId[i][j]!=null) && (expireList.contains(fi.holderId[i][j]))) {
              log("Expiring OLD HOLDER entry for "+fi.key+":"+i+" at "+fi.holderId[i][j]);
              fi.holderKnown[i][j] = false;
            } else {
              cal.setTime(fi.lastHeard[i][j]);
              long tdelay = tnow - cal.getTimeInMillis();
              if (fi.holderDead[i][j]) {
                if (tdelay > deadHolderEntryTimeout) {
                  log("Expiring DEAD entry for "+fi.key+":"+i+" at "+fi.holderId[i][j]);
                  fi.holderKnown[i][j] = false;
                }
              } else if (!fi.holderCertain[i][j]) {
                if (tdelay > uncertainHolderEntryTimeout) {
                  log("Expiring UNCERTAIN entry for "+fi.key+":"+i+" at "+fi.holderId[i][j]);
                  fi.holderKnown[i][j] = false;
                }
              }
            }
          }
          
          if (fi.holderKnown[i][j] && (fi.holderId[i][j] != null)) {
            HolderInfo hle = (HolderInfo)state.holderList.get(fi.holderId[i][j]);
            if (hle != null) {
              hle.numReferences ++;
              if (!fi.holderDead[i][j])
                hle.numLiveReferences ++;
            }
          }
        }
      }
    }
    
    Enumeration e3 = state.holderList.elements();
    while (e3.hasMoreElements()) {
      HolderInfo hle = (HolderInfo)e3.nextElement();
      log("HOLDER "+hle.nodeID+": "+hle.numReferences+" total, "+hle.numLiveReferences+" live");
      if ((hle.numReferences == 0) && (!expireList.contains(hle.nodeID)))
        expireList.add(hle.nodeID);
    }
    
    Enumeration e4 = expireList.elements();
    while (e4.hasMoreElements()) {
      Object key = e4.nextElement();
      state.holderList.remove(key);
      log("Expiring holder entry for "+key);
    }
  }
  
  private void timerExpired(char timerID)
  {
    log("TIMER EXPIRED: #"+(int)timerID);

    switch (timerID) {
      case tiInsert :
      {
        warn("INSERT TIMER EXPIRED ??!?");
        /* FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME */
        /* FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME */
        /* FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME */
        break;
      }
      case tiStatusCast :
      {
        log("Timeout: StatusCast - auditing...");
        auditPrimaryReplicas();
        addTimer(auditTimeout, tiAudit);
        break;
      }
      case tiAudit :
      {
        log("Timeout: Audit - determining restore jobs...");
        expireOldHolders();
        expireOldPackets();
        determineRestoreJobs();
        /* NO break here! */
      }
      case tiRestore :
      {
        if (restoreStep()) {
          addTimer(restoreCycle, tiRestore);
        } else {
          determineHandoffJobs();
          triggerHandoffs();
          addTimer(handoffTimeout, tiHandoff);
        }
        break;
      }
      case tiHandoff :
      {
        if (!handoffList.isEmpty()) {
          HandoffListEntry hle = (HandoffListEntry)(handoffList.elements().nextElement());
          warn("Handoff not successful: "+hle.skey+" to "+hle.destination);
        }
        statusCast();
        state.lastStatusCast = new Date();
        syncState();
        scheduleNextStatusCast();
        break;
      }
      default:
      {
        System.err.println("Unknown timer expired: "+(int)timerID);
        System.exit(1);
      }
    }
  }

  private synchronized void insertStep(InsertListEntry ile)
  {
    System.out.print(ile.key+" knows ");
    for (int i=0; i<numInitialFragments; i++)
      if (ile.holderKnown[i])
        System.out.print(i+" ");
    System.out.println();
    
    boolean knowsAllHolders = true;
    for (int i=0; i<numInitialFragments; i++)
      if (!ile.holderKnown[i])
        knowsAllHolders = false;
        
    if (knowsAllHolders) {
      if (!ile.insertsSent) {
        Id[] knownHolders = new Id[numInitialFragments];
        int[] knownFragmentIDs = new int[numInitialFragments];
        boolean[] knownHolderCertain = new boolean[numInitialFragments];
        for (int i=0; i<numInitialFragments; i++) {
          knownHolders[i] = ile.holder[i].getId();
          knownFragmentIDs[i] = i;
          knownHolderCertain[i] = true;
        }

        ile.insertsSent = true; /* Multithreaded? */
        
        for (int i=0; i<numInitialFragments; i++) {
          if (!ile.receiptReceived[i]) {
            log("Sending insert request for "+ile.key+":"+i+" to "+ile.holder[i].getId());
            endpoint.route(
/*              ile.holder[i].getId(),  */
              null,
              new GlacierInsertMessage(
                getUID(), ile.key, i, ile.manifest, ile.fragments[i], 
                knownHolders, knownFragmentIDs, knownHolderCertain,
                getLocalNodeHandle(), ile.holder[i].getId()
              ),
              ile.holder[i]
            );
          }
        }
      }
      
      boolean hasAllReceipts = true;
      for (int i=0; i<numInitialFragments; i++)
        if (!ile.receiptReceived[i])
          hasAllReceipts = false;
          
      if (hasAllReceipts) {
        insertList.remove(ile.key);
        log("Finished inserting "+ile.key);
        if (insertList.isEmpty()) {
          insertTimeoutActive = false;
          removeTimer(tiInsert);
        }
      }
    }
  }
    
  public void insert(final PastContent obj, final Continuation command)
  {
    log("Insert "+obj+" "+command+" (mutable="+obj.isMutable()+")");

    super.insert(obj, command);

    if (!obj.isMutable()) {
      if (!insertTimeoutActive) {
        insertTimeoutActive = true;
        addTimer(insertTimeout, tiInsert);
      }
    
      log("Encode object: "+obj);
      Fragment[] fragments = codec.encode(obj);
      log("Completed: "+obj);
      
      if (fragments != null) {
        int[] fragmentHash = new int[numFragments];
    
        for (int i=0; i<numFragments; i++)
          fragmentHash[i] = fragments[i].hashCode();
    
        FragmentKey fkey = new FragmentKey(node.getId(), obj.getId());
        StorageManifest manifest = new StorageManifest(fkey, fragmentHash);

        log("Creating new ILE at "+fkey);

        InsertListEntry ile = new InsertListEntry(
          fkey, fragments, manifest, 
          System.currentTimeMillis() + insertTimeout,
          numInitialFragments, getLocalNodeHandle()
        );

        if (!insertList.containsKey(fkey)) {
          insertList.put(fkey, ile);
          syncState();
    
          for (int i=0; i<numInitialFragments; i++) {
            Id fragmentLoc = getFragmentLocation(obj.getId(), i);
            endpoint.route(
              fragmentLoc, 
              new GlacierQueryMessage(getUID(), fkey, i, getLocalNodeHandle(), fragmentLoc), 
              null
            );
          }
        } else {
          warn("Immutable object inserted a second time: "+fkey);
        }         
      } else {
        warn("Cannot encode object -- too large?");
      }
    }
  }

  private void addOrUpdateHolder(FileInfo fileInfo, int fragmentID, Id newHolder, boolean alive, boolean overrideDead, boolean certain)
  {
    log("AOUH "+fileInfo.key+":"+fragmentID+" H "+newHolder+" alive "+alive+" certain "+certain+" override "+overrideDead);
  
    for (int k=0; k<FileInfo.maxHoldersPerFragment; k++) {
      if (fileInfo.holderKnown[fragmentID][k] && (fileInfo.holderId[fragmentID][k] != null)) {
        if (fileInfo.holderId[fragmentID][k].equals(newHolder)) {
          HolderInfo currentHolder = (HolderInfo)state.holderList.get(fileInfo.holderId[fragmentID][k]);
          assume(currentHolder != null);
        
          /* The new holder is already there; all we need to do is to update it */
          
          if (fileInfo.holderDead[fragmentID][k]) {
            /* The holder is there, but marked as dead -- revive only if override */
            
            if (alive && overrideDead) {
              log("Reviving");
              fileInfo.holderDead[fragmentID][k] = false;
              fileInfo.holderCertain[fragmentID][k] = certain;
              currentHolder.numLiveReferences ++;
              recordHolderUpdateEvent(fileInfo.key, fragmentID, newHolder);
            }
          } else {
            /* The holder is there and alive */
            
            if (!alive) {
              log("Killing");
              fileInfo.holderDead[fragmentID][k] = true;
              fileInfo.holderCertain[fragmentID][k] = certain;
              currentHolder.numLiveReferences --;
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
     
    /* The holder is not there yet -- we learned something new! */
              
    int slot = fileInfo.getNextAvailableSlotFor(fragmentID);
    if (slot<0)
      panic("No more holder slots in addOrUpdateHolder");
      
    if (fileInfo.holderKnown[fragmentID][slot] && (fileInfo.holderId[fragmentID][slot] != null))
      removeHolderReference(fileInfo.holderId[fragmentID][slot]);
      
    log("Adding as new holder");
    
    fileInfo.holderKnown[fragmentID][slot] = true;
    fileInfo.holderId[fragmentID][slot] = addHolderReference(newHolder);
    fileInfo.holderDead[fragmentID][slot] = !alive;
    fileInfo.holderCertain[fragmentID][slot] = certain;
    fileInfo.lastHeard[fragmentID][slot] = new Date();

    if (alive) {
      HolderInfo currentHolder = (HolderInfo)state.holderList.get(fileInfo.holderId[fragmentID][slot]);
      assume(currentHolder != null);
      currentHolder.numLiveReferences ++;
    }
  }

  private void addFragmentNews(FragmentKey key, int fragmentID, Id newHolder, int eventType, Id sender)
  {
    assume((0<=fragmentID) && (fragmentID<numFragments));
    log("News on "+key+":"+fragmentID+": Sender "+sender+" says "+HistoryEvent.eventName(eventType)+" "+newHolder);
    
    if (newHolder.equals(getLocalNodeHandle().getId()))
      return;
      
    FileInfo fileInfo = (FileInfo)state.fileList.get(key);
    if (fileInfo == null) {
      log("addFragmentNews cannot find the file in question -- ignoring");
      return;
    }
    
    if (fileInfo.haveFragment(fragmentID)) {
      warn("Got told about my own fragment -- ignoring");
      return;
    }
    
    /* Update policy:
       - ACQUIRED: Always added, always live (unless already present)
       - MIGRATED: Source added dead, destination added live
       - NEW HOLDER: Added live if not present (dead or alive)
    */
    
    switch (eventType) {
      case HistoryEvent.evtAcquired :
        addOrUpdateHolder(fileInfo, fragmentID, newHolder, true, true, true);
        break;
      case HistoryEvent.evtHandedOff :
        addOrUpdateHolder(fileInfo, fragmentID, sender, false, false, true);
        addOrUpdateHolder(fileInfo, fragmentID, newHolder, true, true, false);
        break;
      case HistoryEvent.evtNewHolder :
        addOrUpdateHolder(fileInfo, fragmentID, newHolder, true, false, false);
        break;
      default :
        panic("Unknown event type in addFragmentNews: "+eventType);
    }
  }

  private void recordNewFragmentEvent(FragmentKey key, int fragmentID)
  {
    state.history.addLast(new HistoryEvent(HistoryEvent.evtAcquired, key, fragmentID, getLocalNodeHandle().getId(), state.currentSequenceNo));
  }

  private void recordHolderUpdateEvent(FragmentKey key, int fragmentID, Id newHolder)
  {
    state.history.addLast(new HistoryEvent(HistoryEvent.evtNewHolder, key, fragmentID, newHolder, state.currentSequenceNo));
  }

  private void recordMigratedFragmentEvent(FragmentKey key, int fragmentID, Id newHolder)
  {
    if (!newHolder.equals(getLocalNodeHandle().getId()))
      state.history.addLast(new HistoryEvent(HistoryEvent.evtHandedOff, key, fragmentID, newHolder, state.currentSequenceNo));
    else
      log("MIGRATED TO MYSELF -- IGNORING");
  }

  private Id addHolderReference(Id nodeID) 
  {
    HolderInfo holderInfo = (HolderInfo)state.holderList.get(nodeID);
    if (holderInfo == null) {
      holderInfo = new HolderInfo(nodeID, new Date(), -1, -1);
      state.holderList.put(nodeID, holderInfo);
    }
    
    holderInfo.numReferences ++;
    return nodeID;
  }

  private void removeHolderReference(Id nodeID)
  {
    HolderInfo holderInfo = (HolderInfo)state.holderList.get(nodeID);
    assume(holderInfo != null);
    
    holderInfo.numReferences --;
    if (holderInfo.numReferences <= 0)
      state.holderList.remove(nodeID);
  }

  private synchronized void markNewFragmentStored(FragmentKey key, int fragmentID, StorageManifest manifest)
  {
    assume((0<=fragmentID) && (fragmentID<numFragments));
  
    FileInfo fileInfo = (FileInfo)state.fileList.get(key);
    
    log("Marking new fragment as stored: "+key+":"+fragmentID);
    
    if (fileInfo == null) {
      fileInfo = new FileInfo(key, manifest, numFragments);
      state.fileList.put(key, fileInfo);
    } 
    
    assume(fileInfo.key.equals(key));
    int slot = fileInfo.getNextAvailableSlotFor(fragmentID);
    if (slot < 0)
      panic("No more holder slots");
      
    if (fileInfo.holderKnown[fragmentID][slot] && (fileInfo.holderId[fragmentID][slot] != null))
      removeHolderReference(fileInfo.holderId[fragmentID][slot]);
    
    fileInfo.holderKnown[fragmentID][slot] = true;
    fileInfo.holderDead[fragmentID][slot] = false;
    fileInfo.holderCertain[fragmentID][slot] = true;
    fileInfo.lastHeard[fragmentID][slot] = new Date();
    fileInfo.holderId[fragmentID][slot] = null;

    recordNewFragmentEvent(key, fragmentID);
  }
  
  private void dumpFileList()
  {
    Enumeration enu = state.fileList.elements();
    
    log("FileList at "+getLocalNodeHandle().getId()+":");
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo)enu.nextElement();
        
      log(" - File "+fileInfo.key);
      for (int i=0; i<numFragments; i++) {
        for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j]) {
            String holderInfo = "(this node)";
            if (fileInfo.holderId[i][j] != null)
              holderInfo = "" + fileInfo.holderId[i][j];

            log("    * Fragment "+i+" at "+holderInfo
              +(fileInfo.holderCertain[i][j] ? " certainly " : " probably ")
              +(fileInfo.holderDead[i][j] ? "dead" : "alive")
              +" ("+fileInfo.lastHeard[i][j]+")"
            );
          }
        }
      }
    }
  }

  private void refreshConfirmedFragmentsFor(Id holder)
  {
    Enumeration enu = state.fileList.elements();
    Date now = new Date();
    
    while (enu.hasMoreElements()) {
      FileInfo fileInfo = (FileInfo)enu.nextElement();
      for (int i=0; i<numFragments; i++) {
        for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
          if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] && fileInfo.holderCertain[i][j] && (fileInfo.holderId[i][j] != null)) {
            if (fileInfo.holderId[i][j].equals(holder)) 
              fileInfo.lastHeard[i][j] = now;
          }
        }
      }
    }
  }

  public void update(NodeHandle handle, boolean joined)
  {
    ListIterator sqi = stickyQueue.listIterator(0);
      
    log("UPDATE "+handle+" joined="+joined);
    
    if (!joined)
      return;
    
    while (sqi.hasNext()) {
      GlacierStatusMessage lsm = (GlacierStatusMessage) sqi.next();
      log("STICKY checking "+lsm.getDestination()+"/"+handle.getId()+" -- "+lsm);
      if (lsm.getDestination().equals(handle.getId())) {
        log("STICKY delivering "+lsm);
        endpoint.route(
/*           lsm.getDestination(),  */
          null,
          lsm,
          handle
        );
        
        sqi.remove();
      }
    }
  }

  /** DELIVER ***********************************************************************************************/

  public void deliver(Id id, Message message) {
    if (message instanceof PastMessage) {
      super.deliver(id, message);
      return;
    }
  
    final GlacierMessage msg = (GlacierMessage) message;
    log("Received message " + msg + " with destination " + id + " from "+msg.getSource().getId());
   
    if (msg instanceof GlacierQueryMessage) {
      GlacierQueryMessage gqm = (GlacierQueryMessage)msg;
      log("Queried for "+gqm.getKey()+":"+gqm.getFragmentID());
  
      boolean haveIt = storage.exists(new StorageKey(gqm.getKey(), gqm.getFragmentID()));
      if (haveIt) {
        log("EXISTS: "+gqm.getKey()+":"+gqm.getFragmentID());
      } else {
        log("DOES NOT EXIST: "+gqm.getKey()+":"+gqm.getFragmentID());
      }
  
      endpoint.route(
        gqm.getSource().getId(), 
        new GlacierResponseMessage(gqm.getUID(), gqm.getKey(), gqm.getFragmentID(), haveIt, getLocalNodeHandle(), gqm.getSource().getId()),
        gqm.getSource()
      );
    } else if (msg instanceof GlacierTimeoutMessage) {
      GlacierTimeoutMessage gtm = (GlacierTimeoutMessage) msg;
      timerExpired((char)gtm.getUID());
      syncState();
      return;
    } else if (msg instanceof GlacierResponseMessage) {
      GlacierResponseMessage grm = (GlacierResponseMessage) msg;
      log("Response for "+grm.getKey()+":"+grm.getFragmentID()+" ("+grm.getHaveIt()+")");
      
      if (insertList.containsKey(grm.getKey())) {
        InsertListEntry ile = (InsertListEntry)insertList.get(grm.getKey());
        if (grm.getFragmentID() >= numInitialFragments) 
          panic("Fragment ID too large in insert response");
          
        ile.holderKnown[grm.getFragmentID()] = true;
        ile.holder[grm.getFragmentID()] = grm.getSource();
        if (grm.getHaveIt())
          ile.receiptReceived[grm.getFragmentID()] = true;
          
        insertStep(ile);
        syncState();
        return;
      }

      StorageKey skey = new StorageKey(grm.getKey(), grm.getFragmentID());

      if (handoffList.containsKey(skey)) {
        HandoffListEntry hle = (HandoffListEntry)handoffList.get(skey);
        
        if (!grm.getHaveIt()) {
          FileInfo fileInfo = (FileInfo)state.fileList.get(skey.getFragmentKey());

          int numKnownHolders = 0;
          for (int i=0; i<numFragments; i++)
            for (int j=0; j<FileInfo.maxHoldersPerFragment; j++)
              if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] &&
                ((fileInfo.holderId[i][j]!=null) || (i!=skey.getFragmentID())))
                numKnownHolders ++;
                
          /* We tell him all we know... but certainly not about the very fragment we are
            handing off ... */
                
          Id[] knownHolders = new Id[numKnownHolders];
          int[] knownFragmentIDs = new int[numKnownHolders];
          boolean[] knownHolderCertain = new boolean[numKnownHolders];
          int index = 0;
          for (int i=0; i<numFragments; i++) {
            for (int j=0; j<FileInfo.maxHoldersPerFragment; j++) {
              if (fileInfo.holderKnown[i][j] && !fileInfo.holderDead[i][j] &&
                ((fileInfo.holderId[i][j]!=null) || (i!=skey.getFragmentID()))) {
                knownHolders[index] = fileInfo.holderId[i][j];
                knownHolderCertain[index] = fileInfo.holderCertain[i][j];
                if (knownHolders[index] == null)
                  knownHolders[index] = getLocalNodeHandle().getId();
                knownFragmentIDs[index] = i;
                index ++;
              }
            }
          }

          endpoint.route(
/*            grm.getSource().getId(),  */
            null,
            new GlacierInsertMessage(
              getUID(), skey.getFragmentKey(), skey.getFragmentID(), 
              hle.manifest, hle.fragment, 
              knownHolders, knownFragmentIDs, knownHolderCertain,
              getLocalNodeHandle(), grm.getSource().getId()
            ),
            grm.getSource()
          );
        } else {
          handoffList.remove(skey);
          
          if (!hle.isRestoredFragment) {
            warn("Collision during handoff -- deleting my own copy");

            FileInfo fileInfo = (FileInfo)state.fileList.get(skey.getFragmentKey());
            for (int k=0; k<FileInfo.maxHoldersPerFragment; k++) {
              if (fileInfo.holderKnown[hle.skey.getFragmentID()][k] &&
                (fileInfo.holderId[hle.skey.getFragmentID()][k] == null))
                fileInfo.holderDead[hle.skey.getFragmentID()][k] = true;
            }
                
            final StorageKey sk3 = skey;
            storage.unstore(skey, new Continuation() {
              public void receiveResult(Object o) {
                log("Successfully unstored "+sk3+" after collision");
              }
              public void receiveException(Exception e) {
                warn("receiveException("+e+") during collision -- unexpected, ignored (sk3="+sk3+")");
              }
            });
          } else {
            warn("He already has what I was going to restore!!!");
            addFragmentNews(
              skey.getFragmentKey(), skey.getFragmentID(), grm.getSource().getId(), 
              HistoryEvent.evtAcquired, null
            );
          }
        }
        return;
      }

      warn("Unexpected GlacierResponseMessage");
    } else if (msg instanceof GlacierInsertMessage) {
      final GlacierInsertMessage gim = (GlacierInsertMessage) msg;
      log("Insert request for "+gim.getKey()+":"+gim.getFragmentID());
      
      StorageKey skey = new StorageKey(gim.getKey(), gim.getFragmentID());
      if (!storage.exists(skey)) {
        log("STORING "+skey);

        markNewFragmentStored(gim.getKey(), gim.getFragmentID(), gim.getStorageManifest());
        assume(gim.knownHolderFragmentID != null);
        assume(gim.knownHolder != null);
        for (int i=0; i<gim.knownHolder.length; i++) {
          addFragmentNews(
            gim.getKey(), gim.knownHolderFragmentID[i], gim.knownHolder[i], 
            (gim.knownHolderCertain[i]) ? HistoryEvent.evtAcquired : HistoryEvent.evtNewHolder,
            null
          );
        }          
        
        /* Record that the sender does NOT have the fragment any more; even a 
          CONFIRMED entry MUST be removed */
          
        FileInfo fileInfo = (FileInfo)state.fileList.get(gim.getKey());
        if (fileInfo != null) {
          for (int i=0; i<FileInfo.maxHoldersPerFragment; i++)
            if (fileInfo.holderKnown[gim.getFragmentID()][i] &&
              !fileInfo.holderDead[gim.getFragmentID()][i] &&
              (fileInfo.holderId[gim.getFragmentID()][i] != null))
              if (fileInfo.holderId[gim.getFragmentID()][i].equals(gim.getSource().getId())) {
                log("KILLED PREVIOUS HOLDER");
                fileInfo.holderDead[gim.getFragmentID()][i] = true;
              }
        }

        storage.store(skey, gim.getFragment(), new Continuation() {
          public void receiveResult(Object o) {
            endpoint.route(
              gim.getSource().getId(), 
              new GlacierReceiptMessage(gim.getUID(), gim.getKey(), gim.getFragmentID(), getLocalNodeHandle(), gim.getSource().getId()),
              gim.getSource()
            );
          }
          public void receiveException(Exception e) {
            warn("receiveException("+e+") during GlacierInsert -- unexpected, ignored (key="+gim.getKey()+")");
          }
        });

        syncState();
      } else {
        log("Collision on insert for "+skey+", sending receipt only");
        
        endpoint.route(
          gim.getSource().getId(), 
          new GlacierReceiptMessage(gim.getUID(), gim.getKey(), gim.getFragmentID(), getLocalNodeHandle(), gim.getSource().getId()),
          gim.getSource()
        );
      }
      
      return;
    } else if (msg instanceof GlacierReceiptMessage) {
      GlacierReceiptMessage grm = (GlacierReceiptMessage) msg;
      log("Receipt for "+grm.getKey()+":"+grm.getFragmentID());

      if (insertList.containsKey(grm.getKey())) {
        InsertListEntry ile = (InsertListEntry)insertList.get(grm.getKey());
        if (grm.getFragmentID() >= numInitialFragments) 
          panic("Fragment ID too large in insert receipt");
          
        assume(ile.holderKnown[grm.getFragmentID()]);
        ile.receiptReceived[grm.getFragmentID()] = true;
        insertStep(ile);
        syncState();
        return;
      }

      StorageKey skey = new StorageKey(grm.getKey(), grm.getFragmentID());
      if (handoffList.containsKey(skey)) {
        HandoffListEntry hle = (HandoffListEntry)handoffList.get(skey);
        
        if (!hle.isRestoredFragment) {
          int fragmentID = grm.getFragmentID();
          log("Handoff successful for "+skey);
          recordMigratedFragmentEvent(grm.getKey(), fragmentID, grm.getSource().getId());

          FileInfo fileInfo = (FileInfo)state.fileList.get(skey.getFragmentKey());
          for (int i=0; i<FileInfo.maxHoldersPerFragment; i++)
            if (fileInfo.holderKnown[fragmentID][i] &&
              !fileInfo.holderDead[fragmentID][i] &&
              (fileInfo.holderId[fragmentID][i] == null))
              fileInfo.holderKnown[fragmentID][i] = false;

          handoffList.remove(skey);              
          addFragmentNews(grm.getKey(), fragmentID, grm.getSource().getId(), HistoryEvent.evtAcquired, grm.getSource().getId());

          final StorageKey sk3 = skey;
          storage.unstore(skey, new Continuation() {
            public void receiveResult(Object o) {
              log("Successfully unstored "+sk3);
            }
            public void receiveException(Exception e) {
              warn("receiveException("+e+") during handoff -- unexpected, ignored (sk3="+sk3+")");
            }
          });
        } else {
          log("Restore successful for "+skey);
          handoffList.remove(skey);
          recordMigratedFragmentEvent(grm.getKey(), grm.getFragmentID(), grm.getSource().getId());
          addFragmentNews(grm.getKey(), grm.getFragmentID(), grm.getSource().getId(), HistoryEvent.evtAcquired, grm.getSource().getId());
        }
        
        return;
      }
      
      warn("Unexpected GlacierReceiptMessage -- discarded");
      return;
    } else if (msg instanceof GlacierStickyMessage) {
      GlacierStickyMessage gsm = (GlacierStickyMessage) msg;
      log("STICKY Received "+gsm);
      
      GlacierStatusMessage esm = (GlacierStatusMessage) gsm.message;
      ListIterator sqi = stickyQueue.listIterator(0);
      esm.remainingLifetime = stickyPacketLifetime;
      
      while (sqi.hasNext()) {
        GlacierStatusMessage lsm = (GlacierStatusMessage) sqi.next();
        if (lsm.getSource().getId().equals(esm.getSource().getId()) && lsm.getDestination().equals(esm.getDestination())) {
          if (lsm.sequenceNo >= esm.sequenceNo) {
            log("STICKY Already got "+lsm);
            return;
          }
        
          log("STICKY Replacing "+lsm+" with "+esm);  
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
      log("Received status report #"+gsm.sequenceNo+" from "+gsm.getSource().getId()+" ("+gsm.events.length+" events)");

      if (!gsm.getDestination().equals(getLocalNodeHandle().getId())) {
        log("STICKY received status cast for "+gsm.getDestination()+", forwarding...");

        LeafSet leafSet;
        if (node instanceof MultiringNode)
          leafSet = ((DistPastryNode)((MultiringNode)node).getNode()).getLeafSet();
          else leafSet = ((PastryNode)node).getLeafSet();

        for (int k=0; k<leafSet.size(); k++) {
          if (leafSet.get(k) != null) {
            Id nhId = leafSet.get(k).getId();
            if (node instanceof MultiringNode)
              nhId = factory.buildRingId(((MultiringNode)node).getRingId(), nhId);
              
            log("STICKY forwarding to "+nhId);
            endpoint.route(
              nhId,
              new GlacierStickyMessage(getUID(), gsm.getSource().getId(), gsm, getLocalNodeHandle(), nhId),
              leafSet.get(k)
            );
          }
        }
          
        return;
      }

      HolderInfo holderInfo = (HolderInfo)state.holderList.get(gsm.getSource().getId());
      if (holderInfo == null) {
        warn("I don't know this guy");
        return;
      }

      /* If it's a full report, we should probably make all references
        to this holder uncertain here, so that we resynch completely 
        after a packet loss. */
      
      for (int i=0; i<gsm.events.length; i++) {
        if (gsm.events[i].sequenceNo > holderInfo.lastReceivedSequenceNo)
          addFragmentNews(gsm.events[i].key, gsm.events[i].fragmentID, gsm.events[i].holder, gsm.events[i].type, gsm.getSource().getId());
      }
      
      refreshConfirmedFragmentsFor(gsm.getSource().getId());
      
      holderInfo.lastAckedSequenceNo = gsm.ackSequenceNo;
      holderInfo.lastHeardOf = new Date();
      if (!gsm.isFullList && (gsm.sequenceNo > (holderInfo.lastReceivedSequenceNo + numIncludePreviousStatusCasts))) {
        holderInfo.lastReceivedSequenceNo = -1;
        warn("Lost too many status packets... resynching");
      } else {
        log("OK. Going from sequence no #"+holderInfo.lastReceivedSequenceNo+" to #"+gsm.sequenceNo);
        holderInfo.lastReceivedSequenceNo = gsm.sequenceNo;
      }
    } else if (msg instanceof GlacierFetchMessage) {
      final GlacierFetchMessage gfm = (GlacierFetchMessage) msg;
      final StorageManager pastStore = super.storage;
      log("Received fetch for "+gfm.getKey()+":"+gfm.getFragmentID());

      storage.getObject(new StorageKey(gfm.getKey(), gfm.getFragmentID()), new Continuation() {
        public void receiveResult(Object o) {
          if (o != null) {
            log("Fragment found, returning...");
            endpoint.route(
/*              gfm.getSource().getId(),  */
                null,
                new GlacierDataMessage(
                  getUID(), gfm.getKey(), gfm.getFragmentID(),
                  (Fragment)o,
                  getLocalNodeHandle(), gfm.getSource().getId()
                ),
              gfm.getSource()
            );
          } else {
            log("Fragment not found - but maybe we have the original?");
            pastStore.getObject(gfm.getKey().getId(), new Continuation() {
              public void receiveResult(Object o) {
                if (o != null) {
                  log("Original found. Recoding...");
                  Fragment[] frags = codec.encode((Serializable)o);
                  log("Fragments recoded ok. Returning fragment...");
                  endpoint.route(
/*                    gfm.getSource().getId(),  */
                    null,
                    new GlacierDataMessage(
                      getUID(), gfm.getKey(), gfm.getFragmentID(),
                      frags[gfm.getFragmentID()],
                      getLocalNodeHandle(), gfm.getSource().getId()
                    ),
                    gfm.getSource()
                  );
                } else {
                  log("Original not found either");
                }
              }
              public void receiveException(Exception e) {
                warn("storage.getObject("+gfm.getKey()+") returned exception "+e);
                e.printStackTrace();
              }
            });
          }
        }
        public void receiveException(Exception e) {
          warn("Fetch("+gfm.getKey()+") returned exception "+e);
        }
      });
    } else if (msg instanceof GlacierDataMessage) {
      final GlacierDataMessage gdm = (GlacierDataMessage) msg;
      RestoreListEntry rle = (RestoreListEntry)restoreList.get(gdm.getKey());
      if (rle != null) {
        if (rle.status == RestoreListEntry.stWaitingForPrimary) {
          assume(rle.fragmentID == gdm.getFragmentID());
          log("Preparing for handoff: "+gdm.getKey()+":"+gdm.getFragmentID());
        
          /* check against manifest here! */
          StorageKey skey = new StorageKey(rle.key, rle.fragmentID);
          handoffList.put(skey,
            new HandoffListEntry(
              skey, getFragmentLocation(rle.key.getId(), rle.fragmentID), true, gdm.getFragment(), rle.fileInfo.manifest
            )
          );
          restoreList.remove(gdm.getKey());
          return;
        } else if (rle.status == RestoreListEntry.stCollectingFragments) {
          rle.haveFragment[gdm.getFragmentID()] = gdm.getFragment();
          log("RESTORE got another puzzle piece: "+rle.key+":"+rle.fragmentID+", now have "+rle.numFragmentsAvailable()+"/"+numSurvivors);
          if (rle.numFragmentsAvailable() >= numSurvivors) {
            log("RESTORE enough puzzle pieces, ready to recode "+rle.key+":"+rle.fragmentID);
            rle.status = RestoreListEntry.stRecoding;
          }
          return;
        } else {
          warn("Unknown RLE status "+rle.status+" when receiving DataMessage");
        }
      }

      warn("Unexpected GlacierDataMessage -- discarded");
      return;
    } else {
      panic("GLACIER ERROR - Received message " + msg + " of unknown type.");
    }
  }
}
