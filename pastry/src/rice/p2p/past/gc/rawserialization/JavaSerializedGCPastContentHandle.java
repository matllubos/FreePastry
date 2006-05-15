/*
 * Created on Mar 23, 2006
 */
package rice.p2p.past.gc.rawserialization;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.past.PastContentHandle;
import rice.p2p.past.gc.GCPastContentHandle;

public class JavaSerializedGCPastContentHandle implements RawGCPastContentHandle {
  public static final short TYPE = 0;
  
  public GCPastContentHandle handle;
  
  public JavaSerializedGCPastContentHandle(GCPastContentHandle handle) {
    this.handle = handle;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    // write out object and find its length
    oos.writeObject(handle);
    oos.close();
    
    byte[] temp = baos.toByteArray();
    buf.writeInt(temp.length);
    buf.write(temp, 0, temp.length);
//    System.out.println("JavaSerializedGCPastContentHandle.serialize() "+handle+" length:"+temp.length);
//    new Exception("Stack Trace").printStackTrace();
  }

  public short getType() {
    return TYPE;
  }
  
  public String toString() {
    return "JSPCH ["+handle+"]"; 
  }

  public Id getId() {
    return handle.getId();
  }

  public NodeHandle getNodeHandle() {
    return handle.getNodeHandle();
  }
  
  public PastContentHandle getPCH() {
    return handle; 
  }

  public long getVersion() {
    return handle.getVersion();
  }

  public long getExpiration() {
    return handle.getExpiration();
  }
}
