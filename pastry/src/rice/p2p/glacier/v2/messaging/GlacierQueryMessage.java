package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class GlacierQueryMessage extends GlacierMessage {
  public static final short TYPE = 5;

  protected FragmentKey keys[];

  public GlacierQueryMessage(int uid, FragmentKey keys[], NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.keys = keys;
  }

  public FragmentKey getKey(int index) {
    return keys[index];
  }

  public int numKeys() {
    return keys.length;
  }

  public String toString() {
    return "[GlacierQuery for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf); 
    buf.writeInt(keys.length);
    for (int i = 0; i < keys.length; i++) {
      keys[i].serialize(buf); 
    }
  }
  
  public static GlacierQueryMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierQueryMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierQueryMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    keys = new FragmentKey[buf.readInt()];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = new FragmentKey(buf, endpoint); 
    }
  }
}

