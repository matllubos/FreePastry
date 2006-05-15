
package rice.pastry.messaging;

import java.util.*;

import rice.environment.logging.Logger;
import rice.pastry.*;
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

  private int bufferSize;

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
  
  protected Logger logger;
  
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
    this.logger = pn.getEnvironment().getLogManager().getLogger(getClass(), null);    
  }

  /**
   * Registers a receiver with the mail service.
   *
   * @param name a name for a receiver.
   * @param receiver the receiver.
   */
  public void registerReceiver(int address, PastryAppl receiver) {
    // the stack trace is to figure out who registered for what, it is not an error
    
    if (logger.level <= Logger.FINE) logger.log(
        "Registering "+receiver+" for address " + address);
    if (logger.level <= Logger.FINEST) logger.logException(
        "Registering receiver for address " + address, new Exception("stack trace"));
    if (addressBook.get(new Integer(address)) != null) {
      throw new IllegalArgumentException("Registering receiver for already-registered address " + address);
//      if (logger.level <= Logger.SEVERE) logger.logException(
//          "ERROR - Registering receiver for already-registered address " + address, new Exception("stack trace"));
    }

    addressBook.put(new Integer(address), receiver);
  }
  
  public PastryAppl getDestination(Message msg) {
    return getDestinationByAddress(msg.getDestination());    
  }

  public PastryAppl getDestinationByAddress(int addr) {
    PastryAppl mr = (PastryAppl) addressBook.get(new Integer(addr));    
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
    if (msg.getDestination() == 0) {
      Logger logger = localNode.getEnvironment().getLogManager().getLogger(MessageDispatch.class, null);
      if (logger.level <= Logger.WARNING) logger.logException(
          "Message "+msg+","+msg.getClass().getName()+" has no destination.", new Exception("Stack Trace"));
      return false;
    }
    // NOTE: There is no saftey issue with calling localNode.isReady() because this is on the 
    // PastryThread, and the only way to set a node ready is also on the ready thread.
    PastryAppl mr = (PastryAppl) addressBook.get(new Integer(msg.getDestination()));

    if (mr == null) {
      if (logger.level <= Logger.WARNING) logger.log(
          "Dropping message " + msg + " because the application address " + msg.getDestination() + " is unknown.");
      return false;
    } else {
      mr.receiveMessage(msg); 
      return true;
    }
  }  
  
  public boolean dispatchMessage(RawMessageDelivery msg) {
    if (msg.getAddress() == 0) {
      Logger logger = localNode.getEnvironment().getLogManager().getLogger(MessageDispatch.class, null);
      if (logger.level <= Logger.WARNING) logger.logException(
          "Message "+msg+","+msg.getClass().getName()+" has no destination.", new Exception("Stack Trace"));
      return false;
    }
    // NOTE: There is no saftey issue with calling localNode.isReady() because this is on the 
    // PastryThread, and the only way to set a node ready is also on the ready thread.
    PastryAppl mr = (PastryAppl) addressBook.get(new Integer(msg.getAddress()));

    if (mr == null) {
      if (logger.level <= Logger.WARNING) logger.log(
          "Dropping message " + msg + " because the application address " + msg.getAddress() + " is unknown.");
      return false;
    } else {
      mr.receiveMessageInternal(msg); 
      return true;
    }
  }  
  
  public void destroy() {
    Iterator i = addressBook.values().iterator();
    while(i.hasNext()) {
      PastryAppl mr = (PastryAppl)i.next();
      mr.destroy(); 
    }      
    addressBook.clear();
  }
}
