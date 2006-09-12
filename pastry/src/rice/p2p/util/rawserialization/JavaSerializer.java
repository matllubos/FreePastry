/*
 * Created on Sep 12, 2006
 */
package rice.p2p.util.rawserialization;

import java.io.*;

import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class JavaSerializer {

  public static void serialize(Message msg, OutputBuffer buf) throws IOException {
    try {
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
    } catch (IOException ioe) {
      throw new JavaSerializationException(msg, ioe);
    }

  }

}
