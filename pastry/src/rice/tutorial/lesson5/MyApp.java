/*
 * Created on Apr 15, 2005
 */
package rice.tutorial.lesson5;

import rice.environment.Environment;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.Node;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.RouteMessage;
import rice.tutorial.lesson3.MyMsg;

/**
 * This app shows how to trigger regularly scheduled
 * events on the FreePastry thread.
 * 
 * @author Jeff Hoye
 */
public class MyApp implements Application {
  
  /**
   * This is the task sends the MessageToSelf every 5 
   * seconds.  We keep it as a member variable to 
   * cancel later.
   */
  CancellableTask messageToSelfTask;
  
  /**
   * The Endpoint represents the underlieing node.  By making calls on the 
   * Endpoint, it assures that the message will be delivered to a MyApp on whichever
   * node the message is intended for.
   */
  protected Endpoint endpoint;

  protected Environment environment;
  
  /**
   * NOTE: It is unsafe to send this message to 
   * anyone but the localnode.  
   * 
   * The reason:
   * This is an inner class of MyApp, and therefore
   * has an automatic reference to
   * MyApp which is not serializable!  Do not 
   * attempt to send over the wire, you will get a 
   * NotSerializableException!
   */
  class MessageToSelf implements Message {
    public byte getPriority() {
      return MAX_PRIORITY;
    }    
  }
  
  public MyApp(Node node) {
    this.environment = node.getEnvironment();
    // We are only going to use one instance of this application on each PastryNode
    this.endpoint = node.buildEndpoint(this, "myinstance");
    
    endpoint.register();
    
    // Send MessageToSelf every 5 seconds, starting in 3 seconds
    messageToSelfTask = endpoint.scheduleMessage(new MessageToSelf(), 3000, 5000);
  }
  
  /**
   * Call this to cancel the task.
   */
  public void cancelTask() {
    messageToSelfTask.cancel(); 
  }

  
  
  /**
   * Called to route a message to the id
   */
  public void routeMyMsg(Id id) {
    System.out.println(this+" sending to "+id);    
    Message msg = new MyMsg(endpoint.getId(), id);
    endpoint.route(id, msg, null);
  }
  
  /**
   * Called to directly send a message to the nh
   */
  public void routeMyMsgDirect(NodeHandle nh) {
    System.out.println(this+" sending direct to "+nh);    
    Message msg = new MyMsg(endpoint.getId(), nh.getId());
    endpoint.route(null, msg, nh);
  }
    
  /**
   * Called when we receive a message.
   */
  public void deliver(Id id, Message message) {
    System.out.println(this+" received "+message);
    if (message instanceof MessageToSelf) {
      // This will get called every 5 seconds, on Pastry's thread.
      // Thus now we can assume we are on Pastry's thread.
      // TODO: whatever... send messages to other nodes? print out status?
      System.out.println("I got the MessageToSelf at time:"+environment.getTimeSource().currentTimeMillis());
    }
  }

  /**
   * Called when you hear about a new neighbor.
   * Don't worry about this method for now.
   */
  public void update(NodeHandle handle, boolean joined) {
  }
  
  /**
   * Called a message travels along your path.
   * Don't worry about this method for now.
   */
  public boolean forward(RouteMessage message) {
    return true;
  }
  
  public String toString() {
    return "MyApp "+endpoint.getId();
  }

}
