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
package rice.post.storage;

import java.io.*;
import java.math.*;
import java.security.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.util.*;

/**
 * This class is used internally by the storage package to
 * store data that is signed.
 * 
 * @version $Id$
 */
public class SignedData extends StorageServiceData {
  public static final short TYPE = 6;

  // serialver for backwards compatibility
  private static final long serialVersionUID = 7535493841770155095L;

  // The time that the data was stored.
  protected transient byte[] timestamp;

  // The signature used to sign the data.
  protected transient byte[] signature;
  
  /**
   * Builds a SignedData for a byte array given a timestamp.
   * The signature is the hash of the timestamp appended to the byte array,
   * signed with the user's private key.
   *
   * @param data The data to store
   * @param time The timestamp
   * @param credentials Credentials of the data
   */
  public SignedData(Id location, byte[] data, byte[] timestamp) {
    super(location, data);
    
    this.timestamp = timestamp;
    this.signature = null;
  }

  /**
   * Returns the internal timestamp of this version
   *
   * @return The timestamp when the data was stored
   */
  public byte[] getTimestamp() {
    return timestamp;
  }

  /**
   * @return The data and timestamp appended
   */
  public byte[] getDataAndTimestamp() {
    byte[] all = new byte[data.length + timestamp.length];
    System.arraycopy(data, 0, all, 0, data.length);
    System.arraycopy(timestamp, 0, all, data.length, timestamp.length);

    return all;
  }
    
  /**
   * @return The signature to verify the data and timestamp
   */
  public byte[] getSignature() {
    return signature;
  }

  protected void setSignature(byte[] sig) {
    signature = sig;
  }

  public boolean equals(Object o) {
    if (! (o instanceof SignedData))
      return false;

    SignedData signed = (SignedData) o;
    
    return (Arrays.equals(data, signed.getData()) &&
            Arrays.equals(timestamp, signed.getTimestamp()) &&
            Arrays.equals(signature, signed.getSignature()));
  }

  public String toString() {
    return "SignedData[" + data.length + "]";
  }

  /**
   * Checks if a insert operation should be allowed.  Invoked when a
   * Past node receives an insert request and it is a replica root for
   * the id; invoked on the object to be inserted.  This method
   * determines the effect of an insert operation on an object that
   * already exists: it computes the new value of the stored object,
   * as a function of the new and the existing object.
   *
   * @param id the key identifying the object
   * @param newObj the new object to be stored
   * @param existingObj the existing object stored on this node (null if no object associated with id is stored on this node)
   * @return null, if the operation is not allowed; else, the new
   * object to be stored on the local node.
   */
  public PastContent checkInsert(rice.p2p.commonapi.Id id, PastContent existingContent) throws PastException {
    return this;
  }

  /**
   * States if this content object is mutable. Mutable objects are not subject to dynamic caching in Past.
   *
   * @return true if this object is mutable, else false
   */
  public boolean isMutable() {
    return true;
  }
  
  /**
   * Returns the version number of this object
   *
   * @return The version number
   */
  public long getVersion() {
    return MathUtils.byteArrayToLong(timestamp);
  }
  
  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();

//    new Exception("Signed Data writeObject()").printStackTrace();
    oos.writeInt(timestamp.length);
    oos.write(timestamp);
    oos.writeInt(signature.length);
    oos.write(signature);
  }
  
  /**
    * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    timestamp = new byte[ois.readInt()];
    ois.readFully(timestamp, 0, timestamp.length);
    signature = new byte[ois.readInt()];
    ois.readFully(signature, 0, signature.length);
  }

  public short getType() {
    return TYPE;
  }
  
  public SignedData(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
//    System.out.println(toString()+".deserialize()");
    timestamp = new byte[buf.readInt()];
    buf.read(timestamp);
    signature = new byte[buf.readInt()];
    buf.read(signature);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
//    System.out.println(toString()+".serialize()");
    super.serialize(buf);
    
    buf.writeInt(timestamp.length);
    buf.write(timestamp, 0, timestamp.length);
    buf.writeInt(signature.length);
    buf.write(signature, 0, signature.length);    
  }
  
}
