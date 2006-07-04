/*
 * Created on Feb 21, 2006
 */
package rice.pastry.messaging;

import java.io.*;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.util.rawserialization.JavaSerializationException;
import rice.pastry.messaging.*;

/**
 * Wrapper that converts rice.pastry.messaging.Message to rice.pastry.messageing.PRawMessage
 * 
 * @author Jeff Hoye
 */
public class PJavaSerializedMessage extends PRawMessage {

  Message msg;
  
//  Exception constructionStack;
  
  public PJavaSerializedMessage(Message msg) {
    super(msg.getDestination());
    this.msg = msg;
    if (msg == null) throw new RuntimeException("msg cannot be null");
//    constructionStack = new Exception("Stack Trace: msg:"+msg);
  }

  public short getType() {
    return 0;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
  
      // write out object and find its length
      oos.writeObject(msg);
      oos.close();
      
      byte[] temp = baos.toByteArray();
      buf.write(temp, 0, temp.length);
//    System.out.println("PJavaSerializedMessage.serialize() "+msg+" length:"+temp.length);
//    new Exception("Stack Trace").printStackTrace();
//    constructionStack.printStackTrace();
    } catch (IOException ioe) {
      throw new JavaSerializationException("Error serializing "+msg, ioe);
    }
  }
  
  public Message getMessage() {
    return msg; 
  }
}
