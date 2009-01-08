/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.post.log;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.util.*;
import rice.post.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * Class which represents an encrypted and coalesced log in the POST system.
 * This class is designed so that applications can simply use this as the
 * log head, instead of the Log class, and the contents of the log will
 * automatically be encrypted and coalesced into the provided number of
 * discrete fragments.
 * 
 * @version $Id$
 */
@SuppressWarnings("unchecked")
public class CoalescedLog extends EncryptedLog {
  
  private static final long serialVersionUID = -8781290016130834529L;

  // the number of log entries to coalece into a single entry
  public static int COALESCE_NUM = 50;
  
  // the encrypted set of yet-to-be coalesced log entries
  protected transient byte[] cipherPending;
  
  // the decrypted list of yet-to-be-coalesed log entries
  protected transient CoalescedLogEntry pending;
  
  // the buffer of waiting add entry tasks
  protected transient Vector cbuffer;

  protected transient Logger logger;
  
  /**
   * Constructs a Log for use in POST, with the provided number of 
   * coalesced log entries.
   *
   * @param name Some unique identifier for this log
   * @param location The location of this log in PAST
   */
  public CoalescedLog(Object name, Id location, Post post, KeyPair keyPair) {
    super(name, location, post, keyPair);

    resetPending();
    this.cbuffer = new Vector();
  }
  
  /**
   * Constructs a Log for use in POST, with the provided number of 
   * coalesced log entries.
   *
   * @param name Some unique identifier for this log
   * @param location The location of this log in PAST
   */
  public CoalescedLog(Object name, Id location, Post post, KeyPair keyPair, byte[] cipherKey) {
    super(name, location, post, keyPair, cipherKey);

    resetPending();
    this.cbuffer = new Vector();
  }

  /**
   * This method appends an entry into the user's log, and updates the pointer 
   * to the top of the log to reflect the new object. This method returns a 
   * LogEntryReference which is a pointer to the LogEntry in PAST. Note that 
   * this method reinserts this Log into PAST in order to reflect the addition.
   *
   * Once this method is finished, it will call the command.receiveResult()
   * method with a LogEntryReference for the new entry, or it may call
   * receiveExcception if an exception occurred.
   *
   * @param entry The log entry to append to the log.
   * @param command The command to run once done
   */
  public void addLogEntry(final LogEntry entry, final Continuation command) {
    AddCoalescedLogEntryTask aclet = new AddCoalescedLogEntryTask(entry, command);
    boolean go = false;
    
    synchronized (cbuffer) {
      cbuffer.add(aclet);
      
      go = (cbuffer.size() == 1);
    }    
    
    if (go)
      aclet.go();
  }
  
  /**
   * This method returns a list of all the handles stored in the folder or
   * any subfolders.
   *
   * Returns a PastContentHandle[] containing all of 
   * the handles in to the provided continatuion.
   */
  public void getLogEntryReferences(final Set set, final LogEntry entry, Continuation command) {
    if (pending.contains(entry)) 
      command.receiveResult(Boolean.TRUE);
    else
      super.getLogEntryReferences(set, entry, command);
  }
  
  /**
   * This method returns a reference to the most recent entry in the log,
   * which can then be used to walk down the log.
   *
   * @return A reference to the top entry in the log.
   */
  public void getTopEntry(Continuation command) {
    pending.getPreviousEntry(null, command);
  }
  
  /**
   * This method returns the *actual* top entry in the log.
   *
   * @return A reference to the top entry in the log.
   */
  public void getActualTopEntry(Continuation command) {
    super.getTopEntry(command);
  }
  
  /**
   * Method which resets the nuber of entries to be zero.
   */
  protected void resetPending() {
    pending = new CoalescedLogEntry(new LogEntry[COALESCE_NUM]);
    regenerateCipherPending();
    
    pending.setParent(new PhantomLogEntry());
    pending.setPost(post);
  }
  
  /**
   * Method which regenerates the ciphertext of the number of log 
   * entries
   */
  protected void regenerateCipherPending() {
    try {
      byte[] data = SecurityUtils.serialize(pending);
      cipherPending = SecurityUtils.encryptSymmetric(data, key);
    } catch (IOException e) {
      if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(CoalescedLog.class, null);
      if (logger.level <= Logger.WARNING) logger.logException( "Exception " + e + " thrown while serializing/encrypting pending " + pending,e);
    }
  }
  
  /**
   * Method which deserializes the encrypted yet-to-be-coalesed log entries
   * once the key has been retrieved.
   */
  protected void retrievePending() {
    if (cipherPending != null) {
      try {
        byte[] data = SecurityUtils.decryptSymmetric(cipherPending, key);
        pending = (CoalescedLogEntry) SecurityUtils.deserialize(data);
        pending.setParent(new PhantomLogEntry());
      } catch (IOException e) {
        if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(CoalescedLog.class, null);
        if (logger.level <= Logger.WARNING) logger.logException( "Exception " + e + " thrown while deserializing/decrypting pending " + pending,e);
      } catch (ClassNotFoundException e) {
        if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(CoalescedLog.class, null);
        if (logger.level <= Logger.WARNING) logger.logException( "Exception " + e + " thrown while deserializing/decrypting pending " + pending,e);
      }
    } else {
      if (logger == null) logger = post.getEnvironment().getLogManager().getLogger(CoalescedLog.class, null);
      if (logger.level <= Logger.WARNING) logger.log( "Found null cipher pending - resetting pending on log " + this);
      resetPending();
    }
  }
  
  /**
   * Method which deserializes the list of yet-to-be-coalesed entries when
   * the coalesced log is read off of the wire.
   *
   * @param keyPair The keypair for this log
   */
  public void setKeyPair(KeyPair keyPair) {
    super.setKeyPair(keyPair);
   
    retrievePending();
  }
  
  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    
    oos.writeInt(cipherPending.length);
    oos.write(cipherPending);
  }
  
  /**
    * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    if (cipherPending == null) {
      cipherPending = new byte[ois.readInt()];
      ois.readFully(cipherPending, 0, cipherPending.length);
    }
    
    this.cbuffer = new Vector();
  }
  
  /**
    * Internal class for bookkeeping log entries
   */
  protected class PhantomLogEntry extends LogEntry {
    
    /**
     * Constructor which takes the wrapped entry
     */
    public PhantomLogEntry() {
    }
    
    /**
     * Returns the reference to the previous entry in the log
     *
     * @return A reference to the previous log entry
     */
    public LogEntryReference getPreviousEntryReference() {
      if (topEntryReferences != null)
          return topEntryReferences[0];
      else  
        return topEntryReference;
    }
    
    /**
     * Returns whether or not this log entry has a previous log entry
     *
     * @return Whether or not this log entry has a previous
     */
    public boolean hasPreviousEntry() {
      return (topEntryReferences != null) || (topEntryReference != null);
    }    
    
    /**
     * Returns the cached previous entry, if it exists and is in memory.
     * Otherwise, it returns null.
     *
     * @return The cached previous entry
     */
    public LogEntry getCachedPreviousEntry() {
      if (topEntry == null)
        return null;
      else
        return ((EncryptedLogEntry) topEntry).entry;
    }
      
    /**
     * Method which redirects the getPreviousEntry back
     * to the Coalesed entry
     *
     * @param command The command to return the result to.
     */
    public void getPreviousEntry(Continuation command) {
      getActualTopEntry(command);
    }
  }

  public String toString() {
    return "CoalescedLog[" + name + "]";
  }
  
  /**
   * This class encapsulates the logic needed to add a log entry to
   * the current coalesced log.
   */
  protected class AddCoalescedLogEntryTask {
        
    protected LogEntry entry;
    protected Continuation command;
    
    /**
     * This construct will build an object which will call the given
     * command once processing has been completed, and will provide
     * a result.
     *
     * @param entry The log entry to add
     * @param command The command to call
     */
    protected AddCoalescedLogEntryTask(LogEntry entry, Continuation command) {
      this.entry = entry;
      this.command = command;
    }
    
    protected void go() {
      pending.appendEntry(entry);
      regenerateCipherPending();
      
      if (pending.getNumEntries() == pending.getEntries().length) {
        final CoalescedLogEntry temp = pending;
        resetPending();
        
        CoalescedLog.super.addLogEntry(temp, new Continuation() {
          public void receiveResult(Object o) {
            command.receiveResult(o);
            
            notifyNext();
          }
          
          public void receiveException(Exception e) {
            temp.removeEntry(entry);
            pending = temp;
            command.receiveException(e);
            
            notifyNext();
          }
        });
      } else {
        sync(new Continuation() {
          public void receiveResult(Object o) {
            command.receiveResult(o);
            
            notifyNext();
          }
          
          public void receiveException(Exception e) {
            pending.removeEntry(entry);
            command.receiveException(e);
            
            notifyNext();
          }
        });
      }
    }
    
    protected void notifyNext() {
      AddCoalescedLogEntryTask task = null;
      
      synchronized (cbuffer) {
        if ((cbuffer.size() > 0) && (cbuffer.get(0) == this)) {
          cbuffer.remove(0);
          
          if (cbuffer.size() > 0) 
            task = (AddCoalescedLogEntryTask) cbuffer.get(0);
        }
      }
      
      if (task != null)
        task.go();
    }      
  }  
}

