
package rice.pastry.messaging;

import java.util.*;

import rice.environment.logging.Logger;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;

/**
 * An object which remembers the mapping from names to MessageReceivers
 * and dispatches messages by request.
 * 
 * For consistent routing, modified to only deliver messages to applications 
 * if the PastryNode.isReady().  It will still deliver messages to any non-PastryAppl
 * because these "services" may be needed to boot the node into the ring.  Any 
 * messages to a PastryAppl will be buffered until the node goes ready.
 * 
 * TODO:  We need to make it explicit which apps can receive messages before
 * PastryNode.isReady().
 * 
 * @version $Id$
 *
 * @author Jeff Hoye
 * @author Andrew Ladd
 */

public class MessageDispatch {

  private int bufferSize = 32;

  // have modified from HashMap to HashMap to use the internal representation
  // of a LocalAddress.  Otherwise remote node cannot get its message delivered
  // because objects constructed differently are not mapped to the same value
  private HashMap addressBook;

  // a buffer of messages received before an application has been added to handle
  // the messages
  private Hashtable buffer;
  
  // the current count of the number of messages in the bufer
  private int bufferCount;

  protected PastryNode localNode;
  
  /**
   * If the node is not ready, we do not deliver messages 
   * to applications.  What should we do with these messages?
   * Buffer or Drop?
   * 
   * true will buffer the messages that should be delivered
   * false will drop them and print a message
   */
  private boolean bufferIfNotReady;
  
  public static final String BUFFER_IF_NOT_READY_PARAM = "pastry_messageDispatch_bufferIfNotReady";
  public static final String BUFFER_SIZE_PARAM = "pastry_messageDispatch_bufferSize";
  
  
  /**
   * Constructor.
   */
  public MessageDispatch(PastryNode pn) {
    bufferIfNotReady = pn.getEnvironment().getParameters().getBoolean(BUFFER_IF_NOT_READY_PARAM);
    bufferSize = pn.getEnvironment().getParameters().getInt(BUFFER_SIZE_PARAM);
    addressBook = new HashMap();
    buffer = new Hashtable();
    bufferCount = 0;
    this.localNode = pn;
  }

  /**
   * Registers a receiver with the mail service.
   *
   * @param name a name for a receiver.
   * @param receiver the receiver.
   */
  public void registerReceiver(Address address, MessageReceiver receiver) {
    if (addressBook.get(address) != null) {
      localNode.getEnvironment().getLogManager().getLogger(MessageDispatch.class, null).log(Logger.SEVERE,
          "ERROR - Registering receiver for already-registered address " + address);
    }

    addressBook.put(address, receiver);
  }
  
  public MessageReceiver getDestination(Message msg) {
    MessageReceiver mr = (MessageReceiver) addressBook.get(msg.getDestination());    
    return mr;
  }

  public MessageReceiver getDestinationByAddress(Address addr) {
    MessageReceiver mr = (MessageReceiver) addressBook.get(addr);    
    return mr;
  }

  /**
   * Dispatches a message to the appropriate receiver.
   * 
   * It will buffer the message under the following conditions:
   *   1) The MessageReceiver is not yet registered.
   *   2) The MessageReceiver is a PastryAppl, and localNode.isReady() == false
   *
   * @param msg the message.
   *
   * @return true if message could be dispatched, false otherwise.
   */
  public boolean dispatchMessage(Message msg) {
    if (msg.getDestination() == null) {
      localNode.getEnvironment().getLogManager().getLogger(MessageDispatch.class, null).log(Logger.WARNING,
          "Message "+msg+","+msg.getClass().getName()+" has no destination.");
      Thread.dumpStack();
      return false;
    }
    // NOTE: There is no saftey issue with calling localNode.isReady() because this is on the 
    // PastryThread, and the only way to set a node ready is also on the ready thread.
    MessageReceiver mr = (MessageReceiver) addressBook.get(msg.getDestination());
        
    if ((mr != null) && (!(mr instanceof PastryAppl) || localNode.isReady())) {
      Address address = msg.getDestination();
      // note we want to deliver the buffered messages first, otherwise we 
      // can get out of order messages
      deliverBuffered(address);
      mr.receiveMessage(msg); 
      return true;
    } else {
      // we should consider buffering the message
      if ((bufferCount <= bufferSize) && // we have enough memory to buffer
          (localNode.isReady() || bufferIfNotReady)) { // the node is ready, or we are supposed to buffer if not ready
        // buffer
        Vector vector = (Vector) buffer.get(msg.getDestination());
        
        if (vector == null) {
          vector = new Vector();
          buffer.put(msg.getDestination(), vector);
        }
        
        localNode.getEnvironment().getLogManager().getLogger(MessageDispatch.class, null).log(Logger.INFO,
            "Buffering message " + msg + " because the application address " + msg.getDestination() + " is unknown." + "Message will be delivered when the an application with that address is registered.");
        
        vector.add(msg);
        bufferCount++;
      } else { 
        // give an excuse
        if (localNode.isReady()) {
          localNode.getEnvironment().getLogManager().getLogger(MessageDispatch.class, null).log(Logger.WARNING,
              "Could not dispatch message " + msg + " because the application address " + msg.getDestination() + " was unknown." + "Message is going to be dropped on the floor.");
        } else {
          localNode.getEnvironment().getLogManager().getLogger(MessageDispatch.class, null).log(Logger.WARNING,
              "Could not dispatch message " + msg + " because the pastry node is not yet ready." + "Message is going to be dropped on the floor.");          
        }
      }    
      return false;
    }
  }  
  
  /**
   * Deliveres all buffered messages for the address.
   * 
   * Unless:
   *   1)  The MR for the address is still null.
   *   2)  The MR is a PastryAppl and localNode.isReady() == false
   * 
   * @param address
   */
  protected void deliverBuffered(Address address) {
    // deliver any buffered messages
    MessageReceiver mr = (MessageReceiver) addressBook.get(address);
    if (mr != null) {    
      if (!(mr instanceof PastryAppl) || localNode.isReady()) {
        Vector vector = (Vector) buffer.remove(address);
        
        if (vector != null) {
          
          for (int i=0; i<vector.size(); i++) {
            mr.receiveMessage((Message) vector.elementAt(i));
            bufferCount--;
          }
        } 
      }
    }
  }
  
  /**
   * Called when PastryNode.isReady() becomes true.  Delivers all buffered
   * messages.
   */
  public void deliverAllBufferedMessages() {
    // need to clone the buffer table because it may change during 
    // the loop
    Iterator i = ((Hashtable)(buffer.clone())).keySet().iterator();
    while(i.hasNext()) {
      Address addr = (Address)i.next(); 
      deliverBuffered(addr);
    }
  }
}
