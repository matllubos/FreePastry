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
import java.security.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.past.gc.rawserialization.RawGCPastContent;

/**
 * This class is the abstraction of a class used by the storage package to
 * store data.
 *
 * @version $Id$
 */
abstract class StorageServiceData implements RawGCPastContent {
  
  // serialver for backwards compatibility
  private static final long serialVersionUID = 2882784831315993461L;
  
  // default version number for objects which don't have different versions
  public static final long NO_VERSION = 0L;

  // The data stored in this content hash object.
  protected transient byte[] data;

  // The location where the data is stored
  protected Id location;

  /**
   * Builds a StorageServiceData from a location and a byte array
   *
   * @param location The location
   * @param data The data to store
   */
  public StorageServiceData(Id location, byte[] data) {
    this.location = location;
    this.data = data;
  }
  
  /**
   * Returns the metadata which should be stored with this object.  Allows applications
   * to add arbitrary items into the object's metadata.
   *
   * @param The local GCPast service which the content is on.
   * @return the handle
   */
  public GCPastMetadata getMetadata(long expiration) {
    return new GCPastMetadata(expiration);
  }

  /**
   * Returns the location of this data
   *
   * @return The location of this data.
   */
  public rice.p2p.commonapi.Id getId() {
    return location;
  }

  /**
   * Returns the internal array of data
   *
   * @return The byte array of actual data.
   */
  public byte[] getData() {
    return data;
  }
  
  /**
   * Returns the version number of this object
   *
   * @return The version number
   */
  public long getVersion() {
    return NO_VERSION;
  }
  
  /**
    * Produces a handle for this content object. The handle is retrieved and returned to the
   * client as a result of the Past.lookupHandles() method.
   *
   * @param The local Past service which the content is on.
   * @return the handle
   */
  public PastContentHandle getHandle(Past local) {
    return new StorageServiceDataHandle(local.getLocalNodeHandle(), location, getVersion(), GCPastImpl.DEFAULT_EXPIRATION, local.getEnvironment());
  }
  
  /**
   * Produces a handle for this content object. The handle is retrieved and returned to the
   * client as a result of the Past.lookupHandles() method.
   *
   * @param The local Past service which the content is on.
   * @return the handle
   */
  public GCPastContentHandle getHandle(GCPast local, long expiration) {
    return new StorageServiceDataHandle(local.getLocalNodeHandle(), location, getVersion(), expiration, local.getEnvironment());
  }

  /**
   * Force subclasses to override equals
   *
   * @return Whether this and o are equal
   */
  public abstract boolean equals(Object o);
  
  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    
    oos.writeInt(data.length);
    oos.write(data);
  }
  
  /**
   * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    data = new byte[ois.readInt()];
    ois.readFully(data, 0, data.length);
  }
  
  
  public StorageServiceData(InputBuffer buf, Endpoint endpoint) throws IOException {
    location = endpoint.readId(buf, buf.readShort()); 
    
    data = new byte[buf.readInt()];
    buf.read(data);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(location.getType());
    location.serialize(buf);
    
    buf.writeInt(data.length);
    buf.write(data,0,data.length);
  }
}
