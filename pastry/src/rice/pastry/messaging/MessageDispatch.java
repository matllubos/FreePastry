/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.pastry.messaging;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

import rice.p2p.commonapi.RouteMessage;

/**
 * An object which remembers the mapping from names to MessageReceivers
 * and dispatches messages by request.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class MessageDispatch {

  public static int BUFFER_SIZE = 32;

  // have modified from HashMap to HashMap to use the internal representation
  // of a LocalAddress.  Otherwise remote node cannot get its message delivered
  // because objects constructed differently are not mapped to the same value
  private HashMap addressBook;

  // a buffer of messages received before an application has been added to handle
  // the messages
  private Hashtable buffer;
  
  // the current count of the number of messages in the bufer
  private int bufferCount;

  /**
   * Constructor.
   */
  public MessageDispatch() {
    addressBook = new HashMap();
    buffer = new Hashtable();
    bufferCount = 0;
  }

  /**
   * Registers a receiver with the mail service.
   *
   * @param name a name for a receiver.
   * @param receiver the receiver.
   */
  public void registerReceiver(Address address, MessageReceiver receiver) {
    if (addressBook.get(address) != null) {
      System.out.println("ERROR - Registering receiver for already-registered address " + address);
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
   * @param msg the message.
   *
   * @return true if message could be dispatched, false otherwise.
   */
  public boolean dispatchMessage(Message msg) {
    MessageReceiver mr = (MessageReceiver) addressBook.get(msg.getDestination());
        
    if (mr != null) {
      Address address = msg.getDestination();
      mr.receiveMessage(msg); 
      deliverBuffered(address);
      return true;
    } else {
      if (bufferCount > BUFFER_SIZE) {
        System.out.println("Could not dispatch message " + msg + " because the application address " + msg.getDestination() + " was unknown.");
        System.out.println("Message is going to be dropped on the floor.");
      } else {
        if (msg.getDestination() != null) {
          Vector vector = (Vector) buffer.get(msg.getDestination());
          
          if (vector == null) {
            vector = new Vector();
            buffer.put(msg.getDestination(), vector);
          }
          
          vector.add(msg);
          bufferCount++;
        } else {
          System.out.println("Message "+msg+","+msg.getClass().getName()+" has no destination.");
          Thread.dumpStack();
        }
      }
      
      return false;
    }
  }
  
  protected void deliverBuffered(Address address) {
    // deliver any buffered messages
    Vector vector = (Vector) buffer.remove(address);
    
    if (vector != null) {
      MessageReceiver mr = (MessageReceiver) addressBook.get(address);
      
      for (int i=0; i<vector.size(); i++) {
        mr.receiveMessage((Message) vector.elementAt(i));
        bufferCount--;
      }
    } 
  }
}
