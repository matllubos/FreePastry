
package rice.p2p.scribe.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.ScribeContentDeserializer;

/**
 * @(#) MaintenanceMessage.java
 *
 * The maintenance message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class MaintenanceMessage extends ScribeMessage {
  public static final short TYPE = 7;
 
  /**
   * Constructor 
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public MaintenanceMessage() {
    super((NodeHandle)null, null);
  }
    
  public String toString() {
    return "MaintenanceMessage";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    throw new RuntimeException("serialize() not supported in MaintenanceMessage"); 
  }  
}

