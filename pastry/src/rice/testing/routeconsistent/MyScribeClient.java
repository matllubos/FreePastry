/*
 * Created on May 4, 2005
 */
package rice.testing.routeconsistent;

import java.util.Random;

import rice.environment.Environment;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.Topic;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;

/**
 * We implement Application to receive regular timed messages (see lesson5).
 * We implement ScribeClient to receive scribe messages (called ScribeContent).
 * 
 * @author Jeff Hoye
 */
public class MyScribeClient implements ScribeClient, Application {

  /**
   * Used to randomly accept an anycast.
   */
  Random rng = new Random();
  /**
   * The message sequence number.  Will be incremented after each send.
   */
  int PseqNum = 0;
  int AseqNum = 0;
  
  /**
   * This task kicks off publishing and anycasting.
   * We hold it around in case we ever want to cancel the publishTask.
   */
  CancellableTask publishTask;
  
  /** 
   * My handle to a scribe impl.
   */
  Scribe myScribe;
  
  /**
   * The only topic this appl is subscribing to.
   */
  Topic myTopic;

  /**
   * The Endpoint represents the underlieing node.  By making calls on the 
   * Endpoint, it assures that the message will be delivered to a MyApp on whichever
   * node the message is intended for.
   */
  protected Endpoint endpoint;

  public PastryNode node;
  
  public Environment environment;
  
  /**
   * @param node the PastryNode
   */
  public MyScribeClient(PastryNode node) {
    this.node = node;
    this.environment = node.getEnvironment();
    // you should recognize this from lesson 3
    this.endpoint = node.registerApplication(this, "myinstance");
    // construct Scribe
    myScribe = new ScribeImpl(node,"lesson6instance");
    // construct the topic
    myTopic = new Topic(new PastryIdFactory(), "example topic");
    System.out.println("myTopic = "+myTopic);
  }
  
  /**
   * Subscribes to myTopic.
   */
  public void subscribe() {
    myScribe.subscribe(myTopic, this); 
  }
  
  /**
   * Starts the publish task.
   */
  public void startPublishTask() {
    publishTask = endpoint.scheduleMessage(new PublishContent(), 60000, 60000);    
  }
  
  
  /**
   * Part of the Application interface.  Will receive PublishContent every so often.
   */
  public void deliver(Id id, Message message) {
    if (message instanceof PublishContent) {
      sendMulticast();
      sendAnycast();
    }
  }
  
  /**
   * Sends the multicast message.
   */
  public void sendMulticast() {
    System.out.println("MSC:"+environment.getTimeSource().currentTimeMillis()+" Node "+endpoint.getLocalNodeHandle()+" broadcasting "+PseqNum);
    MyScribeContent myMessage = new MyScribeContent(endpoint.getLocalNodeHandle(), PseqNum, false);
    myScribe.publish(myTopic, myMessage); 
    PseqNum++;
  }

  /**
   * Called whenever we receive a published message.
   */
  public void deliver(Topic topic, ScribeContent content) {
    System.out.println("MSC:"+environment.getTimeSource().currentTimeMillis()+" MyScribeClient.deliver("+topic+","+content+")");
  }

  /**
   * Sends an anycast message.
   */
  public void sendAnycast() {
    System.out.println("Node "+endpoint.getLocalNodeHandle()+" anycasting "+AseqNum);
    MyScribeContent myMessage = new MyScribeContent(endpoint.getLocalNodeHandle(), AseqNum, true);
    myScribe.anycast(myTopic, myMessage); 
    AseqNum++;
  }
  
  /**
   * Called when we receive an anycast.  If we return
   * false, it will be delivered elsewhere.  Returning true
   * stops the message here.
   */
  public boolean anycast(Topic topic, ScribeContent content) {
    boolean returnValue = rng.nextInt(30) == 0;
    System.out.println("MyScribeClient.anycast("+topic+","+content+"):"+returnValue);
    return returnValue;
  }

  public void childAdded(Topic topic, NodeHandle child) {
//    System.out.println("MyScribeClient.childAdded("+topic+","+child+")");
  }

  public void childRemoved(Topic topic, NodeHandle child) {
//    System.out.println("MyScribeClient.childRemoved("+topic+","+child+")");
  }

  public void subscribeFailed(Topic topic) {
//    System.out.println("MyScribeClient.childFailed("+topic+")");
  }

  public boolean forward(RouteMessage message) {
    return true;
  }


  public void update(NodeHandle handle, boolean joined) {
    
  }

  class PublishContent implements Message {
    public int getPriority() {
      return 0;
    }
  }

  
  /************ Some passthrough accessors for the myScribe *************/
  public boolean isRoot() {
    return myScribe.isRoot(myTopic);
  }
  
  public NodeHandle getParent() {
    // NOTE: Was just added to the Scribe interface.  May need to cast myScribe to a
    // ScribeImpl if using 1.4.1_01 or older.
    // return ((ScribeImpl)myScribe).getParent(myTopic); 
    return myScribe.getParent(myTopic); 
  }
  
  public NodeHandle[] getChildren() {
    return myScribe.getChildren(myTopic); 
  }
  
}
