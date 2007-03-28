package rice.p2p.scribe;

import java.util.Collection;

import rice.Destructable;
import rice.environment.Environment;
import rice.p2p.commonapi.NodeHandle;

/**
 * Scribe functions not specific to serialization type.
 * 
 * @author Jeff Hoye
 *
 */
public interface BaseScribe extends Destructable {
  // ***************** Membership functions messages **************
  /**
   * Subscribes the given client to the provided topic.  Any message published
   * to the topic will be delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   */
  public void subscribe(Topic topic, ScribeClient client);

  /**
   * Unsubscribes the given client from the provided topic. 
   *
   * @param topic The topic to unsubscribe from
   * @param client The client to unsubscribe
   */
  public void unsubscribe(Topic topic, ScribeClient client);

  /**
   * Adds a child to the given topic
   * 
   * @param topic The topic to add the child to
   * @param child The child to add
   */
  public void addChild(Topic topic, NodeHandle child);

  /**
   * Removes a child from the given topic
   *
   * @param topic The topic to remove the child from
   * @param child The child to remove
   */
  public void removeChild(Topic topic, NodeHandle child);
      
  // *********************** Query functions *********************  
  /**
   * Returns whether or not this Scribe is the root for the given topic
   *
   * @param topic The topic in question
   * @return Whether or not we are currently the root
   */
  public boolean isRoot(Topic topic);
  
  /**
   * Returns the root of the topic, if we can determine it.
   * 
   * @param topic
   * @return null if beyound our knowledge range
   */
  public NodeHandle getRoot(Topic topic);
  
  /**
   * Returns the list of children for a given topic
   *
   * @deprecated use getChildrenOfTopic
   * @param topic The topic to return the children of
   * @return The children of the topic
   */
  public NodeHandle[] getChildren(Topic topic);
  public Collection<NodeHandle> getChildrenOfTopic(Topic topic);

  /**
   * Returns the parent node for a given topic
   * 
   * @param myTopic The topic to return the parent of
   * @return The parent of the topic
   */
  public NodeHandle getParent(Topic topic);
  
  /**
   * Returns the list of topics the given client is subscribed
   * to.
   *
   * @deprecated use getTopicsByClient()
   * @param client The client in question
   * @return The list of topics
   */
  public Topic[] getTopics(ScribeClient client);
  public Collection<Topic> getTopicsByClient(ScribeClient client);

  public int numChildren(Topic topic);


  Collection<ScribeClient> getClients(Topic topic);

  /**
   *  Returns true if there is a TopicManager object corresponding to this topic
   */
  public boolean containsTopic(Topic topic);

  public boolean containsChild(Topic topic, NodeHandle child);
  

  
  
  // ********************* Application management functions **************
  /**
   * Returns the current policy for this scribe object
   * 
   * @return The current policy for this scribe
   */
  public ScribePolicy getPolicy();

  /**
   * Sets the current policy for this scribe object
   *
   * @param policy The current policy for this scribe
   */
  public void setPolicy(ScribePolicy policy);

  
  public Environment getEnvironment();
  
}