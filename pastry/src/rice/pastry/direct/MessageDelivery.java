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
package rice.pastry.direct;

import rice.environment.logging.Logger;
import rice.pastry.PastryNode;
import rice.pastry.messaging.Message;

/**
   * 
   * @version $Id: EuclideanNetwork.java 2561 2005-06-09 16:22:02Z jeffh $
   * @author amislove
   */
  class MessageDelivery implements Delivery {
    protected Message msg;
    protected DirectPastryNode node;    
    protected Logger logger;
    protected int seq;
    NetworkSimulator networkSimulator;
    DirectNodeHandle from;
    
    /**
     * Constructor for MessageDelivery.
     */
    public MessageDelivery(Message m, DirectPastryNode pn, DirectNodeHandle from, NetworkSimulator sim) {
      msg = m;
      node = pn;
      this.from = from;
      this.networkSimulator = sim;
      this.seq = pn.seq++;
      
      // Note: this is done to reduce memory thrashing.  There are a ton of strings created
      // in getLogger(), and this is a really temporary object.
      logger = pn.getLogger();
//      logger = pn.getEnvironment().getLogManager().getLogger(MessageDelivery.class, null);
    }

    public void deliver() {
      if (logger.level <= Logger.FINE) logger.log("MD: deliver "+msg+" to "+node);
      node.receiveMessage(msg);
      networkSimulator.notifySimulatorListenersReceived(msg, from, node.getLocalHandle());
      
//      if (isAlive(msg.getSenderId())) {
//        environment.getLogManager().getLogger(EuclideanNetwork.class, null).log(Logger.FINER, 
//            "delivering "+msg+" to " + node);
//        node.receiveMessage(msg);
//      } else {
//        environment.getLogManager().getLogger(EuclideanNetwork.class, null).log(Logger.INFO, 
//            "Cant deliver "+msg+" to " + node + "because it is not alive.");        
//      }
    } 
    
    public int getSeq() {
      return seq; 
    }
  }