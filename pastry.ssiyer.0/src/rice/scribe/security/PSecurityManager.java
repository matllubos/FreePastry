package rice.scribe.security;

import rice.pastry.*;


/**
 * Implementation of a trivial security manager. It allows all accesses.
 *
 * @author Romer Gil
 */

public class PSecurityManager implements IScribeSecurityManager
{
    /**
     * Verify that the node handle has permission to create a topic.
     *
     * @param handle the node that will be verified
     * @param topicId the topic that the node is trying to access
     *
     * @return true if the node should have access, false otherwise
     */
    public boolean verifyCanCreate( NodeHandle handle, NodeId topicId ) {
	return true;
    }

    /**
     * Verify that the node handle has permission to publish to a topic.
     *
     * @param handle the node that will be verified
     * @param topicId the topic that the node is trying to access
     *
     * @return true if the node should have access, false otherwise
     */
    public boolean verifyCanPublish( NodeHandle handle, NodeId topicId ) {
	return true;
    }

    /**
     * Verify that the node handle has permission to subscribe to a topic.
     *
     * @param handle the node that will be verified
     * @param topicId the topic that the node is trying to access
     *
     * @return true if the node should have access, false otherwise
     */
    public boolean verifyCanSubscribe( NodeHandle handle, NodeId topicId ) {
	return true;
    }
}
