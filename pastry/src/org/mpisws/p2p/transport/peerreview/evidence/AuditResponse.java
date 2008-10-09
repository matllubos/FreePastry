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
package org.mpisws.p2p.transport.peerreview.evidence;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.peerreview.audit.LogSnippit;
import org.mpisws.p2p.transport.peerreview.infostore.Evidence;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

/**
 * Snippit is the contents (everything but the type)
 * 
RESP_AUDIT
  byte type
  nodehandle myHandle
  long long firstSeq
  byte extInfoLen  // always 0 in first version
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
public class AuditResponse implements Evidence {

  public AuditResponse(ByteBuffer byteBuffer) {
    throw new RuntimeException("implement");
//  int readptr = 0;
//  readByte(payload, (unsigned int*)&readptr); /* RESP_AUDIT */
//  NodeHandle *subjectHandle = peerreview->readNodeHandle(payload, (unsigned int*)&readptr, payloadLen);
//  readptr += sizeof(long long);
//  readptr += 1 + payload[readptr];
//  readptr += hashSizeBytes;

  }

  public short getEvidenceType() {
    return RESP_AUDIT;
  }

  
  public void serialize(OutputBuffer buf) throws IOException {
    throw new RuntimeException("implement");
  }

  public LogSnippit getLogSnippit() {
    throw new RuntimeException("implement");
  }

}
