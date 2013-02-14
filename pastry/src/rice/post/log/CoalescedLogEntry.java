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

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.p2p.util.*;
import rice.post.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * Class for all entries in the encrypted log.  Applications should *NOT* use
 * or extend this class, as it is internal to the log package.
 * 
 * @version $Id$
 */
@SuppressWarnings("unchecked")
final class CoalescedLogEntry extends LogEntry {
  
  // the enclosed log entry
  protected LogEntry[] entries;
  
  /**
   * Constructs a LogEntry
   */
  public CoalescedLogEntry(LogEntry[] entries) {
    this.entries = entries;
  }
  
  /**
   * Returns the enclosed entry
   *
   * @return The enclosed entry
   */
  public LogEntry[] getEntries() {
    return entries;
  }
  
  /**
   * Returns whether or not this coaleseced log entry contains
   * the provided entry
   *
   * @param entry The entry to search for
   * @return Whetehr or not this entry contains it
   */
  protected boolean contains(LogEntry entry) {
    if (entry == null)
      return false;
    
    for (int i=0; i<entries.length; i++)
      if ((entries[i] != null) && (entries[i].equals(entry)))
        return true;
    
    return false;
  }
  
  /**
   * Method which returns the number of current log entries
   *
   * @return The number of yet-to-be-coalesced log entries
   */
  protected int getNumEntries() {
    for (int i=0; i<entries.length; i++) 
      if (entries[i] == null)
        return i;
    
    return entries.length;
  }
  
  /**
   * Method which appends an entry to this coalesed log entry
   *
   * @param entry The entry to append
   */
  protected void appendEntry(LogEntry entry) {
    entries[getNumEntries()] = entry;
    entry.setParent(new PhantomLogEntry(entry));
  }
  
  /**
   * Method which removes an entry which failed on the sync().  SHOULD ONLY
   * BE USED BY COALESED LOG ENTRY!
   *
   * @param entry The entry to append
   */
  protected void removeEntry(LogEntry entry) {
    int i=0;
    
    for (; i<entries.length; i++) 
      if (entries[i] == entry) 
        break;
    
    if (i == entries.length)
      return;
    
    for (; i<entries.length-1; i++) 
      entries[i] = entries[i+1];
    
    entries[i] = null;
  }
  
  /**
   * Method which returns the previous entry for the given entry
   *
   * @param entry The entry to fetch the previous entry for
   * @command The command to return the result to
   */
  protected void getPreviousEntry(LogEntry entry, Continuation command) {
    if (entry == entries[0]) {
      getPreviousEntry(new StandardContinuation(command) {
        public void receiveResult(Object o) {
          if (o instanceof CoalescedLogEntry) {
            LogEntry[] otherEntries = ((CoalescedLogEntry) o).getEntries();
            parent.receiveResult(otherEntries[otherEntries.length-1]);
          } else {
            parent.receiveResult(o);
          }
        }
      });
    } else {
      for (int i=1; i<entries.length; i++) 
        if (entries[i] == entry) {
          command.receiveResult(entries[i-1]);
          return;
        }
          
      if (entry == null) {
        command.receiveResult(entries[entries.length-1]);
        return;
      }
      
      command.receiveException(new IllegalArgumentException("ERROR: Could not find previous entry for " + entry));
    } 
  }
  
  /**
   * Method which returns the previous entry for the given entry
   *
   * @param entry The entry to fetch the previous entry for
   * @command The command to return the result to
   */
  protected LogEntry getCachedPreviousEntry(LogEntry entry) {
    if (entry == entries[0]) {
      LogEntry nEntry = getCachedPreviousEntry();
    
      if (nEntry instanceof CoalescedLogEntry) {
        LogEntry[] otherEntries = ((CoalescedLogEntry) nEntry).getEntries();
        return otherEntries[otherEntries.length-1];
      } else {
        return nEntry;
      }
    } else {
      for (int i=1; i<entries.length; i++) 
        if (entries[i] == entry) 
          return entries[i-1];
          
      if (entry == null) 
        return entries[entries.length-1];
          
      throw new IllegalArgumentException("ERROR: Could not find previous entry for " + entry);
    } 
  }
  
  /**
   * Called upon deserialization, which tells the log entries who their parent
   * is
   *
   * @param ois The input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    for (int i=0; i<entries.length; i++) 
      if (entries[i] != null)
        entries[i].setParent(new PhantomLogEntry(entries[i]));
  }
  
  /**
   * Internal class for bookkeeping log entries
   */
  protected class PhantomLogEntry extends LogEntry {
    
    // the wrapped entry
    protected LogEntry entry;
    
    /**
     * Constructor which takes the wrapped entry
     */
    public PhantomLogEntry(LogEntry entry) {
      this.entry = entry;
    } 
    
    /**
     * Returns the reference to the previous entry in the log
     *
     * @return A reference to the previous log entry
     */
    public LogEntryReference getPreviousEntryReference() {
      if (entry == entries[0]) 
        return CoalescedLogEntry.this.getPreviousEntryReference();
      
      return null;
    }  
    
    /**
     * Returns the cached previous entry, if it exists and is in memory.
     * Otherwise, it returns null.
     *
     * @return The cached previous entry
     */
    public LogEntry getCachedPreviousEntry() {
      return CoalescedLogEntry.this.getCachedPreviousEntry(entry);
    }
    
    /**
     * Returns whether or not this log entry has a previous log entry
     *
     * @return Whether or not this log entry has a previous
     */
    public boolean hasPreviousEntry() {
      if (entry == entries[0])
        return (CoalescedLogEntry.this.getPreviousEntryReference() != null);
      else
        return true;
    }
    
    /**
     * Method which redirects the getPreviousEntry back
     * to the Coalesed entry
     *
     * @param command The command to return the result to.
     */
    public void getPreviousEntry(Continuation command) {
      CoalescedLogEntry.this.getPreviousEntry(entry, command);
    }
  }
}

