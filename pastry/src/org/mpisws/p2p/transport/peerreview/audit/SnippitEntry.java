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

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.util.MathUtils;

/**
 *   
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
public class SnippitEntry {

  byte type;
  long seq;
  boolean isHash = false;
  byte[] content; // may be a hash
  
  public void serialize(OutputBuffer buf, SnippitEntry prev) throws IOException {
    if (prev != null) {
      encodeSeq(buf, prev.seq);
    }
    buf.writeByte(type);
    encodeSize(buf);
    buf.write(content, 0, content.length);
  }
  
  public SnippitEntry build(InputBuffer buf, int hashSize, SnippitEntry prev) throws IOException {
    long seq = decodeSeq(buf, prev.seq);
    return build(buf,seq, hashSize);
  }
  
  public SnippitEntry build(InputBuffer buf, long seq, int hashSize) throws IOException {
    byte type = buf.readByte();
    int size = decodeSize(buf);
    boolean isHash = false;
    if (size == 0) {
      isHash = true;
      size = hashSize;
    }
    byte[] content = new byte[size];
    buf.read(content);
    return new SnippitEntry(type,seq,isHash,content);
  }
  
  protected long decodeSeq(InputBuffer buf, long prevSeq) throws IOException {
    byte b = buf.readByte();
    if (b == (byte)0) return prevSeq+1;
    if (b == (byte)0xFF) return buf.readLong();
    long howMuchBigger = MathUtils.uByteToInt(b)*1000;
    long prevSeqUS = ((prevSeq/1000)*1000);
    return prevSeqUS+howMuchBigger;
  }
  
  protected void encodeSeq(OutputBuffer buf, long prevSeq) throws IOException {
    if (seq == prevSeq+1) {
      buf.writeByte((byte)0);
      return;
    }
    
    // If I end with 1000
    if ((seq/1000)*1000 == seq) {
      long foo = ((seq/1000) - (prevSeq/1000));
      if (foo < 0xFF) {
        if (foo < 0) throw new IOException("bug in encodeSeq.  foo:"+foo+" seq:"+seq+" prevSeq:"+prevSeq);
        buf.writeByte((byte)foo);
      }
    }
    
    buf.writeByte((byte)0xFF);
    buf.writeLong(seq);    
  }
  
  public byte getSizeCode() {
    if (isHash) return 0;
    if (content.length < 254) {
      return (byte)content.length; // hope this works...
    }
    if (content.length < 65535) return (byte)0xFF;
    // technically, it could be twice this big, but I don't think java arrays can be so large, if we fix this, also do so in decodeSize
    if (content.length > Integer.MAX_VALUE) throw new RuntimeException("content is too long:"+content.length);
    return (byte)0xFE;
  }
  
  public void encodeSize(OutputBuffer buf) throws IOException {
    if (isHash) {
      buf.writeByte((byte)0);
      return;
    }
    if (content.length < 254) {
      buf.writeByte((byte)content.length);
      return;
    }    
    if (content.length < 65535) {
      buf.writeByte((byte)0xFF);
      buf.writeShort((short)content.length);
      return;
    }
    // technically, it could be twice this big, but I don't think java arrays can be so large, if we fix this, also do so in decodeSize
    if (content.length > Integer.MAX_VALUE) throw new RuntimeException("content is too long:"+content.length);
    buf.writeByte((byte)0xFE);
    buf.writeInt(content.length);
  }
  
  public int decodeSize(InputBuffer buf) throws IOException {
    byte b = buf.readByte();
    if (b == (byte)0xFF) {
      return MathUtils.uShortToInt(buf.readShort());
    }
    if (b == (byte)0xFE) return buf.readInt();
    return MathUtils.uByteToInt(b);
  }

  public SnippitEntry(byte type, long seq, boolean isHash, byte[] content) {
    super();
    this.type = type;
    this.seq = seq;
    this.isHash = isHash;
    this.content = content;
  }
}
