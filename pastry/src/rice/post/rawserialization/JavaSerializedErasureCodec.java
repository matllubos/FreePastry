/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
