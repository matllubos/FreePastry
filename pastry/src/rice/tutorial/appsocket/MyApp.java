/*
 * Created on Feb 15, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package rice.tutorial.appsocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.appsocket.*;

/**
 * A very simple application.
 * 
 * @author Jeff Hoye
 */
public class MyApp implements Application {
  /**
   * The Endpoint represents the underlieing node.  By making calls on the 
   * Endpoint, it assures that the message will be delivered to a MyApp on whichever
   * node the message is intended for.
   */
  protected Endpoint endpoint;
  
  /**
   * The node we were constructed on.
   */
  protected Node node;

  ByteBuffer[] outs;
  ByteBuffer out;
  
  ByteBuffer[] ins;
  ByteBuffer in;
  
  int MSG_LENGTH;
  
  public MyApp(Node node, final IdFactory factory) {
    // We are only going to use one instance of this application on each PastryNode
    this.endpoint = node.buildEndpoint(this, "myinstance");
    this.node = node;
    
    MSG_LENGTH = node.getLocalNodeHandle().getId().toByteArray().length;
    outs = new ByteBuffer[1];    
    out = ByteBuffer.wrap(node.getLocalNodeHandle().getId().toByteArray());
    outs[0] = out;
    
    ins = new ByteBuffer[1];
    in = ByteBuffer.allocate(MSG_LENGTH);
    ins[0] = in;
    
    // example receiver interface
    endpoint.accept(new AppSocketReceiver() {

      /**
       * Called if we have a problem.
       */
      public void receiveException(AppSocket socket, Exception e) {
        e.printStackTrace();
      }
    
      public void receiveSelectResult(AppSocket socket, boolean canRead, boolean canWrite) {
        in.clear();
        try {
          long ret = socket.read(ins, 0, ins.length);    
          
          if (ret != MSG_LENGTH) {
            // if you sent any kind of long message, you would need to handle this case better
            System.out.println("Error, we only received part of a message."+ret+" from "+socket);
            return;
          }
            
          System.out.println(MyApp.this.node.getLocalNodeHandle()+" Received message from "+factory.buildId(in.array()));        
        } catch (IOException ioe) {
          ioe.printStackTrace(); 
        }
        // only need to do this if expecting more messages
//        socket.register(true, false, 3000, this);        
      }
    
      /**
       * When we accept a new socket.
       */
      public void receiveSocket(AppSocket socket) {
//        System.out.println("Accepted socket: "+socket);
        socket.register(true, false, 30000, this);
        
        // it's critical to call this to be able to accept multiple times
        endpoint.accept(this);
      }
    
    });
    
    endpoint.register();
  }

  /**
   * Getter for the node.
   */
  public Node getNode() {
    return node;
  }
  
  /**
   * Called to directly send a message to the nh
   */
  public void sendMyMsgDirect(NodeHandle nh) {
    System.out.println(this+" opening to "+nh);    
    endpoint.connect(nh, new AppSocketReceiver() {

      /**
       * Called if there is a problem.
       */
      public void receiveException(AppSocket socket, Exception e) {
        e.printStackTrace();
      }
      
      /**
       * Example of how to write some bytes
       */
      public void receiveSelectResult(AppSocket socket, boolean canRead, boolean canWrite) {   
        try {
          long ret = socket.write(outs,0,outs.length);        
//          System.out.println("WROTE:"+ret+" to "+socket); 
          // see if we are done
          if (!out.hasRemaining()) {
            socket.close();           
            out.clear();
          } else {
            // keep writing
            socket.register(false, true, 30000, this); 
          }
        } catch (IOException ioe) {
          ioe.printStackTrace(); 
        }
      }
    
      /**
       * Called when the socket comes available.
       */
      public void receiveSocket(AppSocket socket) {
//        System.out.println("Connected socket: "+socket);
        socket.register(false, true, 30000, this);
      }    
    }, 30000);
  }
    
  /**
   * Called when we receive a message.
   */
  public void deliver(Id id, Message message) {
    System.out.println(this+" received "+message);
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
