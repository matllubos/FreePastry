
package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Interface to an object which is simulating the network.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public interface NetworkSimulator 
{
  
    /**
     * Registers a node handle with the simulator.
     *
     * @param nh the node handle to register.
     */

    public void registerNodeId(DirectNodeHandle nh);

    /**
     * Checks to see if a node id is alive.
     *
     * @param nid a node id.
     *
     * @return true if alive, false otherwise.
     */

    public boolean isAlive(NodeId nid);

    /**
     * Determines proximity between two nodes.
     *
     * @param a a node id.
     * @param b another node id.
     *
     * @return proximity of b to a.
     */

    public int proximity(NodeId a, NodeId b);

    /**
     * Deliver message.
     *
     * @param msg message to deliver.
     * @param node the Pastry node to deliver it to.
     */

    public void deliverMessage(Message msg, PastryNode node);

    /**
     * Simulate one message delivery.
     *
     * @return true if a message was delivered, false otherwise.
     */

    public boolean simulate();


    public void setTestRecord( TestRecord tr );
    public TestRecord getTestRecord();
    public DirectNodeHandle getClosest(NodeId nid);
    public void setAlive(NodeId nid, boolean alive);

}
