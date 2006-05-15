/*
 * Created on Mar 23, 2006
 */
package rice.p2p.past.rawserialization;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.past.PastContentHandle;

public class JavaSerializedPastContentHandle implements RawPastContentHandle {
  public static final short TYPE = 0;
  
  public PastContentHandle handle;
  
  public JavaSerializedPastContentHandle(PastContentHandle handle) {
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
//    System.out.println("JavaSerializedPastContentHandle.serialize() "+handle+" length:"+temp.length);
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
}
