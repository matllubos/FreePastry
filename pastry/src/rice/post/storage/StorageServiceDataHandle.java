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

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.multiring.MultiringNodeHandle;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.past.gc.rawserialization.RawGCPastContentHandle;

/**
 * This class is the class which serves a reference to objects stored in past.
 * It is currently not used.
 *
 * @version $Id$
 */
class StorageServiceDataHandle implements RawGCPastContentHandle {
  public static final short TYPE = 8;
  
  
  // serialver for backwards compatibility
  private static final long serialVersionUID = -4110663990885843864L;

  // the location where the data is stored
  protected Id id;

  // the handle where the data is locationed
  protected NodeHandle handle;
  
  // the time at which the handle was created
  protected long timestamp;
  
  // the version number of this data handle
  protected long version;
  
  // the time at which this handle expires
  protected long expiration;

  /**
   * Contstructor
   *
   * @param id The id
   * @param handle The handle where the data is
   * @param timestamp The time at which the handle was created
   * @param version The version number of the object
   * @param expiration The expiration time of the object
   */
  public StorageServiceDataHandle(NodeHandle handle, Id id, long version, long expiration, Environment env) {
    this.id = id;
    this.handle = handle;
    this.timestamp = env.getTimeSource().currentTimeMillis();
    this.version = version;
    this.expiration = expiration;
  }
  
  /**
   * get the id of the PastContent object associated with this handle
   * @return the id
   */
  public Id getId() {
    return id;
  }

  /**
   * get the NodeHandle of the Past node on which the object associated with this handle is stored
   * @return the handle
   */
  public NodeHandle getNodeHandle() {
    return handle;
  }

  /**
   * Returns the timestamp of this handle
   *
   * @return The timestamp for thsi handle
   */
  public long getTimestamp() {
    return timestamp;
  }
  
  /**
   * Returns the version number associated with this PastContentHandle - 
   * version numbers are designed to be monotonically increasing numbers which
   * signify different versions of the same object.
   *
   * @return The version number of this object
   */
  public long getVersion() {
    return version;
  }
  
  /**
    * Returns the current expiration time of this object.
   *
   * @return The current expiration time of this object
   */
  public long getExpiration() {
    return expiration;
  }

  public short getType() {
    return TYPE;
  }

  public StorageServiceDataHandle(InputBuffer buf, Endpoint endpoint) throws IOException {
    timestamp = buf.readLong();
    version = buf.readLong();
    expiration = buf.readLong();

    id = endpoint.readId(buf, buf.readShort());
    
    handle = endpoint.readNodeHandle(buf);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeLong(timestamp);
    buf.writeLong(version);
    buf.writeLong(expiration);
    
    buf.writeShort(id.getType());
    id.serialize(buf);  
    
    handle.serialize(buf);
  }

}

