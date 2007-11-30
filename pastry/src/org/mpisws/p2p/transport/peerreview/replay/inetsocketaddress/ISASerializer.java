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
package org.mpisws.p2p.transport.peerreview.replay.inetsocketaddress;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.peerreview.replay.IdentifierSerializer;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class ISASerializer implements IdentifierSerializer<InetSocketAddress> {

  public ByteBuffer serialize(InetSocketAddress i) {
    byte[] output = new byte[i.getAddress().getAddress().length+2]; // may be IPV4...
    ByteBuffer ret = ByteBuffer.wrap(output);
    ret.put(i.getAddress().getAddress());
    ret.putShort((short)i.getPort());
    ret.flip();
    return ret;
  }

  public InetSocketAddress deserialize(InputBuffer buf) throws IOException {
    byte[] addr = new byte[4];
    buf.read(addr);
    return new InetSocketAddress(InetAddress.getByAddress(addr), buf.readShort());
  }

  public void serialize(InetSocketAddress i, OutputBuffer buf) throws IOException {
    ByteBuffer bb = serialize(i);
    buf.write(bb.array(), bb.position(), bb.remaining());
  }    
}
