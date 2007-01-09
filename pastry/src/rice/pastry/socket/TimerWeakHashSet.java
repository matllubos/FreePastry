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
/*
 * Created on Dec 16, 2006
 */
package rice.pastry.socket;

import java.lang.ref.*;
import java.util.HashMap;
import java.util.Iterator;

import rice.environment.logging.Logger;
import rice.pastry.socket.SocketSourceRouteManager.AddressManager;
import rice.selector.*;

public class TimerWeakHashSet implements WeakHashSet {

  static ReferenceQueue queue = new ReferenceQueue();
  
  static {
    Thread expunger = new Thread(new Runnable() {
    
      public void run() {
        try {
          for (;;) {
            SNHWeakReference wr = (SNHWeakReference)queue.remove(); 
            wr.expunge();
          }
        } catch (InterruptedException ie) {
          System.out.println("Warning, shutting down TimerWeakHashSet!!!");
          ie.printStackTrace();
        }
      }
    
    },"TimerWeakHashSetExpunger");
    
    expunger.setDaemon(true);    
    expunger.start();
  }
  
  private class SNHWeakReference extends WeakReference {
    EpochInetSocketAddress eaddress;
//    String refString; // for debugging
    
    public SNHWeakReference(SocketNodeHandle referent) {
      super(referent, queue);
      eaddress = referent.eaddress;
//      refString = referent.toString();
    }
     
    public void expunge() {
//      logger.log("expunging "+refString);
      // there a synchronization problem here:
      // Is it possible that we were in the queue, and we got replaced
      synchronized(hashMap) {
        SNHWeakReference that = hashMap.remove(eaddress);      
        if (that != null && that != this) {
          // put it back if it's valid
          if (that.getSNH() != null)
            hashMap.put(that.eaddress, that);
        }
      }
          
    }

    public int hashCode() {
      return eaddress.hashCode(); 
    }
    
    public boolean equals(Object o) {      
      EpochInetSocketAddress eisa; 
      if (o instanceof SocketNodeHandle) {
        eisa = ((SocketNodeHandle)o).eaddress;
      } else {
        eisa = (EpochInetSocketAddress)o; 
      }
     
      return (eaddress.equals(eisa));
    } 
    
    public SocketNodeHandle getSNH() {
      return (SocketNodeHandle)get(); 
    }
  }

  public static class HardLinkTimerTask extends TimerTask {
    Object hardLink;
    public HardLinkTimerTask(Object hardLink) {
      this.hardLink = hardLink;
    }
    public void run() {
      // do nothing, just expire, taking the hard link away with you
    }
  }  
  
  SocketPastryNode spn;
  HashMap<EpochInetSocketAddress,SNHWeakReference> hashMap;  
  Logger logger;
  
  int defaultDelay;
  Timer timer;
  
  /**
   * Minimum time to hold the item.
   * 
   * @param timeout
   */
  public TimerWeakHashSet(int timeout, SocketPastryNode spn) {
    this.timer = spn.getEnvironment().getSelectorManager().getTimer();
    this.defaultDelay = timeout;
    this.spn = spn;
    hashMap = new HashMap<EpochInetSocketAddress, SNHWeakReference>();
    logger = spn.getEnvironment().getLogManager().getLogger(getClass(),null);
  }
  
  public SocketNodeHandle coalesce(SocketNodeHandle snh) {
    SocketNodeHandle ret = null;
    synchronized(hashMap) {
      SNHWeakReference wr = hashMap.get(snh.eaddress);
      if (wr != null)
        ret = wr.getSNH();
      
      if (ret == null) {
        ret = snh;
        hashMap.put(snh.eaddress, new SNHWeakReference(snh)); 
        snh.setLocalNode(spn);
        refresh(snh);
      } else {
      // inflates a stub NodeHandle
        if (ret.getNodeId() == null) {
          ret.setNodeId(snh.getNodeId());
        }
      }
    }
    return ret;
  }
  
//  public void put(SocketNodeHandle snh) {
//    synchronized(hashSet) {
//       
//      new SNHWeakReference(snh);
//    }
//  }

  public void refresh(Object key) {
    refresh(key, defaultDelay);
  }
  
  public void refresh(Object key, int delay) {      
    timer.schedule(
        new HardLinkTimerTask(key), delay);
  }
  
  public SocketNodeHandle get(EpochInetSocketAddress eisa) {
    SNHWeakReference wr = hashMap.get(eisa);
    if (wr == null) return null;
    return wr.getSNH();
  }

  /**
   * 
   * @return the best source route for each known EpochInetSocketAddress, keyed by the EISA
   */
  public HashMap getBest() {
    HashMap result = new HashMap();
    
    synchronized(hashMap) {
      Iterator<EpochInetSocketAddress> i = hashMap.keySet().iterator();
      
      while (i.hasNext()) {
        EpochInetSocketAddress addr = i.next();
        
        SNHWeakReference wr = hashMap.get(addr);
        if (wr != null) {
          SocketNodeHandle snh = wr.getSNH();
          if (snh != null) {
            AddressManager am = snh.addressManager;
            if (am != null) {              
              if (am.getLiveness() < SocketNodeHandle.LIVENESS_DEAD)
                result.put(addr, am.best);
            }
          }           
        }
      }
    }      
    return result;
  }

  
}
