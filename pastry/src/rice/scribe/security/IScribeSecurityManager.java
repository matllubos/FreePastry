package rice.scribe.security;

import rice.pastry.*;


/**
 * The Scribe security manager interface.
 *
 * @author Romer Gil
 */

public interface IScribeSecurityManager
{
    /**
     * Verify that the node handle has permission to create a topic.
     *
     * @param handle the node that will be verified
     * @param topicId the topic that the node is trying to access
     *
     * @return true if the node should have access, false otherwise
     */
    public boolean verifyCanCreate( NodeHandle handle, NodeId topicId );

    /**
     * Verify that the node handle has permission to publish to a topic.
     *
     * @param handle the node that will be verified
     * @param topicId the topic that the node is trying to access
     *
     * @return true if the node should have access, false otherwise
     */
    public boolean verifyCanPublish( NodeHandle handle, NodeId topicId );

    /**
     * Verify that the node handle has permission to subscribe to a topic.
     *
     * @param handle the node that will be verified
     * @param topicId the topic that the node is trying to access
     *
     * @return true if the node should have access, false otherwise
     */
    public boolean verifyCanSubscribe( NodeHandle handle, NodeId topicId );
}
