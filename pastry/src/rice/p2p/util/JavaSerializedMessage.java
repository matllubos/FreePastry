/*
 * Created on Feb 21, 2006
 */
package rice.p2p.util;

import java.io.*;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.messaging.*;
import rice.p2p.commonapi.Message;

/**
 * Wrapper that converts rice.pastry.messaging.Message to rice.pastry.messageing.PRawMessage
 * 
 * @author Jeff Hoye
 */
public class JavaSerializedMessage implements RawMessage {

  Message msg;
  
//  Exception constructionStack;
  
  public JavaSerializedMessage(Message msg) {
    this.msg = msg;
    if (msg == null) throw new RuntimeException("msg cannot be null");
//    constructionStack = new Exception("Stack Trace: msg:"+msg).printStackTrace();
  }

  public short getType() {
    return 0;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    // write out object and find its length
    oos.writeObject(msg);
    oos.close();
    
    byte[] temp = baos.toByteArray();
    buf.write(temp, 0, temp.length);
//    System.out.println("JavaSerializedMessage.serailize() "+msg+" length:"+temp.length);
//    new Exception("Stack Trace").printStackTrace();
//    constructionStack.printStackTrace();
  }
  
  public Message getMessage() {
    return msg; 
  }

  public byte getPriority() {
    return msg.getPriority();
  }
  
  public String toString() {
    return "JavaSerializedMessage["+msg+"]"; 
  }
}
