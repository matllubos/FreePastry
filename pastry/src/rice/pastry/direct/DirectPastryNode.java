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

package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.join.*;
import rice.pastry.client.*;

import java.util.*;

/**
 * Direct pastry node. Subclasses PastryNode, and does about nothing else.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

public class DirectPastryNode extends PastryNode
{
    private NetworkSimulator simulator;

    public DirectPastryNode(NodeId id, NetworkSimulator sim) {
	super(id);
	simulator = sim;
    }

    public void setDirectElements(/* simulator */) { }

    public void doneNode(NodeHandle bootstrap) {
	initiateJoin(bootstrap);
    }

    /**
     * Called from PastryNode after the join succeeds.
     */
    protected final void nodeIsReady() { }


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
	return sm;
    }
}

