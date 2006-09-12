
package rice.p2p.past.messaging;

import java.io.*;

import rice.*;
import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.util.rawserialization.*;

/**
 * @(#) ContinuationMessage.java
 *
 * This class the abstraction of a message used internally by Past which serves
 * as a continuation (for receiving the results of an operation).
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public abstract class ContinuationMessage extends PastMessage implements Continuation {

  static final long serialVersionUID = 1321112527034107161L; 
  
  // the response data
  protected Object response;

  // the response exception, if one is thrown
  protected Exception exception;
  
  /**
    * Constructor which takes a unique integer Id, as well as the
   * data to be stored
   *
   * @param uid The unique id
   * @param source The source handle
   * @param dest The destination address
   */
  protected ContinuationMessage(int uid, NodeHandle source, Id dest) {
    super(uid, source, dest); 
  }

  /**
    * Method which builds a response for this message, using the provided
   * object as a result.
   *
   * @param o The object argument
   */
  public void receiveResult(Object o) {
    setResponse();
    response = o;
  }

  /**
    * Method which builds a response for this message, using the provided
   * exception, which was thrown
   *
   * @param e The exception argument
   */
  public void receiveException(Exception e) {
    setResponse();
//    System.out.println("ContinuationMessage.receiveException("+e+")");
//    e.printStackTrace();
    exception = e;
  }

  /**
    * Method by which this message is supposed to return it's response.
   *
   * @param c The continuation to return the reponse to.
   */
  public void returnResponse(Continuation c, Environment env, String instance) {
    if (exception == null)
      c.receiveResult(response);
    else
      c.receiveException(exception);
  }

  /**
   * Returns the response
   *
   * @return The response
   */
  public Object getResponse() {
    return response;
  }
  
  /**
   * No response or exception.
   */
  public static byte S_EMPTY = 0;
  /**
   * Subclass handled serialization.
   */
  public static byte S_SUB = 1; 
  /**
   * Java Serialized Response
   */
  public static byte S_JAVA_RESPONSE = 3;
  /**
   * Java Serialized Exception
   */
  public static byte S_JAVA_EXCEPTION = 2;
  
  
  /**
   * The serialization stategy is that usually the subtype will have an optimal
   * serialization strategy, but sometimes we will have to revert to java serialization
   * 
   * @param buf
   * @param endpoint
   * @throws IOException
   */
  public ContinuationMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint); 
    serType = buf.readByte();
    if (serType > S_SUB) deserialize(buf, endpoint); // not empty, and not handled by the sub
  }
  
  /**
   * 
   * @param buf
   * @throws IOException
   */
  public void deserialize(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte[] array = new byte[buf.readInt()];
    buf.read(array);
    ObjectInputStream ois = new JavaDeserializer(new ByteArrayInputStream(array), endpoint);
    Object content;     
    try {
      content = ois.readObject();
      if (serType == S_JAVA_RESPONSE) {
        response = content;
      } else {
        exception = (Exception)content; 
      }
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Unknown class type in message - closing channel.", e);
    }          
  }

  /**
   * Deprecated to cause warnings.
   * 
   * use serialize(OutputBuffer buf, boolean javaSerialize)
   */
//  public void serialize(OutputBuffer buf) throws IOException {
//    throw new RuntimeException("Illegal call.  Must call serialize(OutputBuffer, boolean");
//  }
  
  public abstract void serialize(OutputBuffer buf) throws IOException;
  
  protected byte serType;
  
  /**
   * If you want this class to serialize itself, set javaSerialize to true,
   * otherwise, the subclass is expected to do an optimal serializatoin
   * 
   * @param buf
   * @param javaSerialize
   * @throws IOException
   */
  public void serialize(OutputBuffer buf, boolean javaSerialize) throws IOException {
//    System.out.println("ContinuationMessage<"+getClass().getName()+">.serialize("+javaSerialize+") resp:"+response+" exc:"+exception+" id:"+id+" src:"+source+" dst:"+dest);
    super.serialize(buf);
    
    serType = S_EMPTY;
    
    if (javaSerialize) {
      Object content = response;
      if (response != null) {
        serType = S_JAVA_RESPONSE;
      } else {
        serType = S_JAVA_EXCEPTION;
        content = exception;
      }
      
      if (content == null) {
        serType = S_EMPTY;
        buf.writeByte(serType);
      } else {      
//      if (true) throw new IOException("Test");
//        System.out.println("ContinuationMessage<"+getClass().getName()+">.serialize():"+serType+" "+response+" "+exception);
        try {
          buf.writeByte(serType);        
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(baos);
          
          // write out object and find its length
          oos.writeObject(content);
          oos.close();
          
          byte[] temp = baos.toByteArray();
          buf.writeInt(temp.length);
          buf.write(temp, 0, temp.length);
        } catch (IOException ioe) {
          throw new JavaSerializationException(content, ioe);
        }
      }
    } else {
      buf.writeByte(S_SUB); 
    }
  }
}

