package rice.p2p.scribe.rawserialization;

import java.util.Collection;

import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.Topic;
import rice.p2p.scribe.Scribe.BaseScribe;

/**
 * Scribe that uses RawSerialization for the Clients
 * 
 * @author Jeff Hoye
 *
 */
public interface RawScribe extends BaseScribe {
  // ***************** Membership functions messages **************
  /**
   * Subscribes the given client to the provided topic.  Any message published
   * to the topic will be delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   * @param content The content to include in the subscribe
   */
  public void subscribe(Topic topic, ScribeClient client, RawScribeContent content);

  /**
   * Subscribes the given client to the provided topic.  Any message published
   * to the topic will be delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   * @param content The content to include in the subscribe
   * @param hint The first hop of the message ( Helpful to implement a centralized solution)
   */
  public void subscribe(Topic topic, ScribeClient client, RawScribeContent content, NodeHandle hint);

  /**
   * Subscribe to multiple topics.
   * 
   * @param topics
   * @param client
   * @param content
   * @param hint
   */
  public void subscribe(Collection<Topic> topics, ScribeClient client, RawScribeContent content, NodeHandle hint);

  // ***************** Messaging functions ****************
  /**
   * Publishes the given message to the topic.
   *
   * @param topic The topic to publish to
   * @param content The content to publish
   */
  public void publish(Topic topic, RawScribeContent content);

  /**
   * Anycasts the given content to a member of the given topic
   *
   * @param topic The topic to anycast to
   * @param content The content to anycast
   */
  public void anycast(Topic topic, RawScribeContent content);

  /**
   * Anycasts the given content to a member of the given topic
   * 
   * The hint helps us to implement centralized algorithms where the hint is the 
   * cachedRoot for the topic. Additionally it enables us to do more fancy 
   * anycasts that explore more portions of the Scribe tree
   *
   * @param topic The topic to anycast to
   * @param content The content to anycast
   * @param hint the first hop of the Anycast
   */
  public void anycast(Topic topic, RawScribeContent content, NodeHandle hint);
     


  // ********************* Application management functions **************
  public void setContentDeserializer(ScribeContentDeserializer deserializer);
  public ScribeContentDeserializer getContentDeserializer();

}
