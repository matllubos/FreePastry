package rice.scribe;

import rice.pastry.security.Credentials;
import rice.pastry.messaging.Message;
import rice.pastry.NodeId;

/**
 * This interface contains the functionality of Scribe.
 *
 * @author Romer Gil
 *
 */

public interface IScribe
{

    /**
     * Creates a topic if the credentials are valid.  Nodes must then subscribe
     * to this topic in order to get information published to it.
     *
     * @param    cred
     * The credentials of the entity creating the topic  
     *
     * @param    topicID       
     * The ID of the topic to be created
     */
    public void create( NodeId topicID, Credentials cred );
    
    /**
     * Subscribe to a particular topic.  When a node becomes subscribed to a 
     * particular topic, it receives all messages published to that topic.
     *
     * @param    cred
     * The credentials of the entity subscribing to the topic
     *
     * @param    topicID        
     * The ID of the topic to subscribe to.
     */
    public void subscribe( NodeId topicID, Credentials cred );
    
    /**
     * Unsubscribe from a topic.  After a node is unsubscribed from a topic, it
     * will no longer receive messages from that topic.
     *
     * @param    cred
     * The credentials of the entity unsubscribing from the topic
     *
     * @param    topicID        
     * The ID of the topic to be unsubscribed from.
     *
     */
    public void unsubscribe( NodeId topicID, Credentials cred );
    
    /**
     * Publish information to a topic.  Data will be delivered to All nodes 
     * that are subscribed to the topic.
     *
     * @param   cred
     * The credentials of the entity publishing to the topic.
     *
     * @param   topicID         
     * The ID of the topic to publish to.
     *
     * @param   msg           
     * The information that is to be published, encapsulated in a Message 
     * object.
     */
    public void publish( NodeId topicID, Object obj, Credentials cred );
    
    
    /**
     * Generate a unique id for the topic, which will determine its rendez-vous
     * point.
     *
     * @param topicName 
     * The name of the topic (unique to the local node)
     */
    public NodeId generateTopicId(String topicName);
    
}

