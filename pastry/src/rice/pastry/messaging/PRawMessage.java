/*
 * Created on Feb 17, 2006
 */
package rice.pastry.messaging;

import java.io.IOException;
import java.util.Date;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.RawMessage;

/**
 * Adapts Message to a RawMessage
 * 
 * Adds the "sender" to the RawMessage
 * @author Jeff Hoye
 */
public abstract class PRawMessage extends Message implements RawMessage {

  public PRawMessage(int address) {
    this(address, null);
  }
  public PRawMessage(int address, Date timestamp) {
    super(address, timestamp); 
  }  
}
