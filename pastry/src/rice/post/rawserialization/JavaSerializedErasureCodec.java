/*
 * Created on Apr 25, 2006
 */
package rice.post.rawserialization;

import java.io.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.glacier.*;
import rice.p2p.past.PastContent;
import rice.p2p.past.rawserialization.PastContentDeserializer;

/**
 * To support reverse compatability with on-disk Java Serialized data.
 * 
 * @author Jeff Hoye
 */
public class JavaSerializedErasureCodec extends ErasureCodec {

  boolean complainWhenSerialize = false;
  
  /**
   *
   * @param _numFragments
   * @param _numSurvivors
   * @param env
   * @param complainWhenSerialize will print a warning during serialization to show where new objects are being inserted into this
   * obsolete codec
   */
  public JavaSerializedErasureCodec(int _numFragments, int _numSurvivors, Environment env, boolean complainWhenSerialize) {
    super(_numFragments, _numSurvivors, env);
    this.complainWhenSerialize = complainWhenSerialize;
  }

  protected PastContent deserialize(byte[] bytes, Endpoint endpoint, PastContentDeserializer pcd) throws IOException, ClassNotFoundException {
    ByteArrayInputStream byteinput = new ByteArrayInputStream(bytes);
    ObjectInputStream objectInput = new ObjectInputStream(byteinput);
    // System.out.println(Systemm.currentTimeMillis()+" XXX after decode("+firstFrag.getPayload().length+" bytes per fragment) free="+Runtime.getRuntime().freeMemory()+" total="+Runtime.getRuntime().totalMemory());
    return (PastContent) objectInput.readObject();
  }
  

  
  public Fragment[] encodeObject(PastContent obj, boolean[] generateFragment) {
    if (complainWhenSerialize) {
      if (logger.level <= Logger.FINE) {
        logger.logException("JSEC.encodeObject("+obj+")", new Exception("Stack Trace"));
      } else {
        if (logger.level <= Logger.WARNING) logger.log("JSEC.encodeObject("+obj+")");
      }      
    }
    byte bytes[];

    try {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);

      objectStream.writeObject(obj);
      objectStream.flush();

      bytes = byteStream.toByteArray();
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING)
        logger.logException("encodeObject: "+obj, ioe);
      return null;
    }

    return encode(bytes, bytes.length, generateFragment);
  }

}
