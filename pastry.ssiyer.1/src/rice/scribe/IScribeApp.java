package rice.scribe;

import rice.scribe.messaging.ScribeMessage;

/**
 * This Interface must be implemented by all applications using Scribe. The 
 * methods in this interface are called by Scribe whenever a particular event
 * happens in the scribe application layer.
 *
 * @author Romer Gil
 */

public interface IScribeApp
{
	
    /** Called by Scribe on a PUBLISH event when a message arrives to its 
     * destination
     * 
     * @param msg 
     * The message sent in the PUBLISH message.
     */
    public void receiveMessage( ScribeMessage msg );
    
    /** Called by Scribe before the node forwards a message to
     * its children in the multicast tree.
     * @param msg 
     * The message about to be forwarded.
     */
    public void forwardHandler( ScribeMessage msg );
    
    /** Invoked by Scribe after a new child is added to one of
     * the node's children tables.
     * @param msg 
     * The SUBSCRIBE message from the new child.
     */
    public void subscribeHandler( ScribeMessage msg );
    
    /** Invoked by Scribe just before the "repair" SUBSCRIBE message is sent
     * when a node suspects its parent is faulty.
     * @param msg 
     * The SUBSCRIBE message that is sent to repair the multicast tree.
     */
    public void faultHandler( ScribeMessage msg );
    
}
