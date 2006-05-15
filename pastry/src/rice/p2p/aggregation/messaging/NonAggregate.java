/*
 * Created on Apr 4, 2006
 */
package rice.p2p.aggregation.messaging;

import java.io.IOException;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.rawserialization.*;

/**
 * Just wraps a header in Past to know that it is something other than an Aggregate
 * 
 * @author Jeff Hoye
 */
public class NonAggregate implements RawPastContent {
  public static final short TYPE = 2;
  
  public RawPastContent content;

  public NonAggregate(PastContent content) {
    this(content instanceof RawPastContent ? (RawPastContent)content : new JavaSerializedPastContent(content));
  }

  public NonAggregate(RawPastContent subContent) {
    this.content = subContent;
  }
  
  public PastContent checkInsert(Id id, PastContent existingContent) throws PastException {
    content = (RawPastContent)content.checkInsert(id, ((NonAggregate)existingContent).content);
    return this;
  }

  public PastContentHandle getHandle(Past local) {
    return content.getHandle(local);
  }

  public Id getId() {
    return content.getId();
  }

  public boolean isMutable() {
    return content.isMutable();
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version        
    buf.writeShort(content.getType());
    content.serialize(buf);
  }
  
  public NonAggregate(InputBuffer buf, Endpoint endpoint, RawPastContent subContent, PastContentDeserializer pcd) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        short subType = buf.readShort();
        PastContent temp = pcd.deserializePastContent(buf, endpoint, subType);
        if (subType == 0) {
          this.content = new JavaSerializedPastContent(temp);
        } else {
          this.content = (RawPastContent)temp; 
        }
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
}
