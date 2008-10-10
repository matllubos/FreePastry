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
package org.mpisws.p2p.transport.peerreview.audit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.mpisws.p2p.transport.util.Serializer;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.commonapi.rawserialization.RawSerializable;

/**
  Nodehandle logHandle
  long long firstSeq
  byte extInfoLen
  [extInfo follows]
  hash baseHash
  --entry begin--           // 1 or more of these entries follow
  char type
  char sizeCode             // 0=hashed, 1-FD=size, FE=32-bit size follows, FF=16-bit size follows
 {short/int size}
  char content[] 
  char nextSeqCode          // 0=+1, 1=(idx=0,us+=1), 2=(idx=0,us+=2), ..., FF=full seq  [does not exist for the last one]
 {long long seq}
  --entry end--
 * @author Jeff Hoye
 *
 */
public class LogSnippit<Handle extends RawSerializable> {
  Handle logHandle;
  byte[] baseHash;
  List<SnippitEntry> entries;
  
  public LogSnippit(Handle logHandle, byte[] baseHash, List<SnippitEntry> entries) {
    this.logHandle = logHandle;
    this.baseHash = baseHash;
    this.entries = entries;
  }
  
  public boolean equals(Object o) {
    LogSnippit<Handle> that = (LogSnippit<Handle>)o;
    if (!this.logHandle.equals(that.logHandle)) return false;
    if (!Arrays.equals(this.baseHash, that.baseHash)) return false;
    if (this.entries.size() != that.entries.size()) return false;
    Iterator<SnippitEntry> i1 = this.entries.iterator();
    Iterator<SnippitEntry> i2 = that.entries.iterator();
    while(i1.hasNext()) {
      if (!i1.next().equals(i2.next())) return false;
    }
    return true;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    logHandle.serialize(buf);
    buf.writeLong(entries.get(0).seq);
    buf.writeByte((byte)0);
    buf.write(baseHash, 0, baseHash.length);
    Iterator<SnippitEntry> i = entries.iterator();
    SnippitEntry prev = i.next();
    prev.serialize(buf, null);
    while(i.hasNext()) {
      SnippitEntry cur = i.next();
      cur.serialize(buf, prev);
      prev = cur;
    }
  }
  
  public LogSnippit(InputBuffer buf, Serializer<Handle> hSerializer, int hashSize) throws IOException {
    logHandle = hSerializer.deserialize(buf);
    long firstSeq = buf.readLong();
    if (buf.readByte() != 0) throw new IOException("Unexpected extInfo");
    baseHash = new byte[hashSize];
    buf.read(baseHash);
    entries = new ArrayList<SnippitEntry>();
    SnippitEntry prev = new SnippitEntry(buf,firstSeq,hashSize);
    entries.add(prev);
    while(buf.bytesRemaining() == -2 || buf.bytesRemaining() > 0) {
      prev = new SnippitEntry(buf,hashSize,prev);
      entries.add(prev);
    }      
  }
}
