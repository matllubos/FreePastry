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

import java.security.*;
import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;

/**
 * This class serves as a reference to a PostObject
 * stored in the Post system.  This class knows both the
 * location in the network and the encryption key of the
 * corresponding PostData object.
 *
 * This class has been extended to support multiple object locations
 * and keys, with each stored in ContentHash form.  This is to allow
 * very large objects to be transparently broken up into a group of
 * smaller objects, and each fragment is stored seperately. Note that
 * a readObject() method has been defined to support the automatic
 * migration of old-style references to the new style.
 * 
 * @version $Id$
 */
public class ContentHashReference implements Serializable {
  public static final short TYPE = 2;

  // serialver, for backwards compatibility
  private static final long serialVersionUID = 5215474536871804216L;
  
  /**
   * Location where this data is stored in PAST.
   */
  private Id[] locations;
  
  /**
   * Key used to sign the content hash.
   */
  private byte[][] keys;

  /**
   * Contructs a PostDataReference object given
   * the address and encryption key of the object.
   *
   * @param location The location in PAST of the PostData object
   * @param key The encryption key of the PostData object
   */
  public ContentHashReference(Id[] locations, byte[][] keys) {
    this.locations = locations;
    this.keys = keys;
  }

  /**
   * @return The locations of the data referenced by this object
   */
  public Id[] getLocations() {
    return locations;
  }

  /**
   * @return The encryption keys for the data
   */
  public byte[][] getKeys() {
    return keys;
  }
  
  public int hashCode() {
    int result = 383727781;
    
    for (int i=0; i<locations.length; i++)
      result ^= locations[i].hashCode();
    
    return result;
  }

  public boolean equals(Object o) {
    if (! (o instanceof ContentHashReference))
      return false;

    ContentHashReference ref = (ContentHashReference) o;

    return Arrays.equals(locations, ref.getLocations());
  }

  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append("[ContentHashRef ");
    
    for (int i=0; i<locations.length; i++)
      result.append(locations[i].toString());
    
    result.append(" ]");
    
    return result.toString();
  }
  
  /**
   * ReadObject overridden in order to support translation from
   * old -> new style references.  
   *
   * @param ois Object Input Stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ObjectInputStream.GetField gf = ois.readFields();
    
    if (! gf.defaulted("locations")) {
      this.locations = (Id[]) gf.get("locations", new Object());
      this.keys = (byte[][]) gf.get("keys", new Object());
    } else {
      if (gf.get("location", null) != null) {
        this.locations = new Id[] { (Id) gf.get("location", new Object()) };
        this.keys = new byte[][] { (byte[]) gf.get("key", new Object()) };
      } else {
        this.locations = new Id[0];
        this.keys = new byte[0][0];
      }
    }
  }
  
  public ContentHashReference(InputBuffer buf, Endpoint endpoint) throws IOException {
    locations = new Id[buf.readInt()]; 
    for (int i = 0; i < locations.length; i++) {
      locations[i] = endpoint.readId(buf, buf.readShort());
    }
    
    keys = new byte[buf.readInt()][];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = new byte[buf.readInt()];
      buf.read(keys[i]);
    }
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeInt(locations.length);
    for (int i = 0; i < locations.length; i++) {
      buf.writeShort(locations[i].getType());
      locations[i].serialize(buf);
    }
    
    buf.writeInt(keys.length);
    for (int i = 0; i < keys.length; i++) {
      buf.writeInt(keys[i].length);
      buf.write(keys[i], 0, keys[i].length);
    }
  }
}
