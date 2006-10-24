package rice.p2p.splitstream;

import java.io.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.RawScribeContent;

/**
 * This represents data sent through scribe for splitstream during a
 * subscribe
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SplitStreamSubscribeContent implements RawScribeContent {
  public static final short TYPE = 2;
  
  /**
   * The first stage of the join process
   */
  public static int STAGE_NON_FINAL = -10;

  /**
   * The final stage of the join process
   */
  public static int STAGE_FINAL = -9;
  
  /**
   * The stage that the client attempting to join is in
   */
  protected int stage;

  /**
   * Constructor taking in a byte[]
   *
   * @param data The data for this content
   */
  public SplitStreamSubscribeContent(int stage) {
    this.stage = stage;
  }

  /**
    * Returns the data for this content
   *
   * @return The data for this content
   */
  public int getStage() {
    return stage;
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0);
    buf.writeInt(stage);
  }
  
  public SplitStreamSubscribeContent(InputBuffer buf) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        stage = buf.readInt();
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
}

