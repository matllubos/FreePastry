package rice.p2p.splitstream;

import java.io.IOException;
import java.util.StringTokenizer;

import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.rawserialization.RawScribeContent;

/**
 * This represents data sent through scribe for splitstream
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SplitStreamContent implements RawScribeContent {
  public static final short TYPE = 1;

  /**
   * The internal data - just the bytes
   */
  protected byte[] data;

  /**
   * Constructor taking in a byte[]
   *
   * @param data The data for this content
   */
  public SplitStreamContent(byte[] data) {
    this.data = data;
  }

  /**
   * Returns the data for this content
   *
   * @return The data for this content
   */
  public byte[] getData() {
    return data;
  }
/*
  public String toString() {


      //Byte bt = new Byte(data[0]);
      String ds = new String(data);
      StringTokenizer tk = new StringTokenizer(ds);
      String seqNumber = tk.nextToken();
      String sentTime = tk.nextToken();
//      Id stripeId = (rice.pastry.Id)(s.getStripeId().getId());
//      String str = stripeId.toString().substring(3, 4);
//      int recv_time = (int)Systemm.currentTimeMillis();
//      int diff;
////      char [] c = str.toString().toCharArray();
//      int stripe_int = c[0] - '0';
//      if(stripe_int > 9)
//    stripe_int = 10 + c[0] - 'A';
//      else
//    stripe_int = c[0] - '0';
      
      return seqNumber+"\t"+"\t"+sentTime;
  }
  
  */
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0);
    buf.writeInt(data.length);
    buf.write(data,0,data.length);
  }
  
  public SplitStreamContent(InputBuffer buf) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        data = new byte[buf.readInt()];
        buf.read(data);
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
}

