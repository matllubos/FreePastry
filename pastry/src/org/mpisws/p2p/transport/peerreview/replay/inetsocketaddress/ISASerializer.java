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
