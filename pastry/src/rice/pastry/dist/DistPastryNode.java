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

package rice.pastry.dist;

import java.util.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

/**
 * Class which represents the abstraction of a "real" pastry node. Designed
 * to be extended by the protocol implementation (i.e. RMI or Socket) desired.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public abstract class DistPastryNode extends PastryNode {

    /**
     * Period (in seconds) at which the leafset and routeset maintenance tasks, respectively, are invoked.
     * 0 means never.
     */
    protected int leafSetMaintFreq, routeSetMaintFreq;
    private Timer timer;


    /**
     * Constructor, with NodeId. Need to set the node's ID before this node
     * is inserted as localHandle.localNode.
     */
    protected DistPastryNode(NodeId id) {
      super(id);
      timer = new Timer();
    }

    /**
     * Method which returns the Dist for this Pastry
     * node.
     *
     * @return The node handle pool for this pastry node.
     */
    public abstract DistNodeHandlePool getNodeHandlePool();


    /**
     * Called after the node is initialized.
     *
     * @param hp Node handle pool
     */
    public void doneNode(NodeHandle bootstrap) {

	if (leafSetMaintFreq > 0) {
	    // schedule the leafset maintenance event
	    scheduleMsgAtFixedRate(new InitiateLeafSetMaintenance(), 
				   leafSetMaintFreq*1000, leafSetMaintFreq*1000);
	}
	if (routeSetMaintFreq > 0) {
	    // schedule the routeset maintenance event
	    scheduleMsgAtFixedRate(new InitiateRouteSetMaintenance(), 
				   routeSetMaintFreq*1000, routeSetMaintFreq*1000);
	}
    }


    /**
     * Method which kills a PastryNode (used only for testing).
     */
    public abstract void kill();


   /**
     * Schedule the specified message to be sent to the local node after a specified delay.
     * Useful to provide timeouts.
     *
     * @param msg a message that will be delivered to the local node after the specified delay
     * @param delay time in milliseconds before message is to be delivered
     * @return the scheduled event object; can be used to cancel the message
     */
    public ScheduledMessage scheduleMsg(Message msg, long delay) {
	ScheduledMessage sm = new ScheduledMessage(this, msg);
	timer.schedule(sm, delay);
	return sm;
    }


    /**
     * Schedule the specified message for repeated fixed-delay delivery to the local node,  
     * beginning after the specified delay. Subsequent executions take place at approximately regular 
     * intervals separated by the specified period. Useful to initiate periodic tasks.
     *
     * @param msg a message that will be delivered to the local node after the specified delay
     * @param delay time in milliseconds before message is to be delivered
     * @param period time in milliseconds between successive message deliveries
     * @return the scheduled event object; can be used to cancel the message 
     */
    public ScheduledMessage scheduleMsg(Message msg, long delay, long period) {
	ScheduledMessage sm = new ScheduledMessage(this, msg);
	timer.schedule(sm, delay, period);
	return sm;
    }


    /**
     * Schedule the specified message for repeated fixed-rate delivery to the local node,  
     * beginning after the specified delay. Subsequent executions take place at approximately regular 
     * intervals, separated by the specified period.
     *
     * @param msg a message that will be delivered to the local node after the specified delay
     * @param delay time in milliseconds before  message is to be delivered
     * @param period time in milliseconds between successive message deliveries
     * @return the scheduled event object; can be used to cancel the message 
     */
    public ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay, long period) {
	ScheduledMessage sm = new ScheduledMessage(this, msg);
	timer.scheduleAtFixedRate(sm, delay, period);
	return sm;
    }

}


