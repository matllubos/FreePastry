package rice.post.storage;

import java.io.*;
import java.math.*;
import java.security.*;
import java.util.*;

import rice.post.security.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * This class is used internally by the storage package to
 * store data that is signed.
 * 
 * @version $Id$
 */
public class SignedData extends StorageServiceData {

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
   * Produces a handle for this content object. The handle is retrieved and returned to the
   * client as a result of the Past.lookupHandles() method.
   *
   * @param The local Past service which the content is on.
   * @return the handle
   */
  public PastContentHandle getHandle(Past local) {
    long time = (new BigInteger(1, timestamp)).longValue();
    return new StorageServiceDataHandle(local.getLocalNodeHandle(), location, time);
  }
  
  /**
    * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    
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
}
