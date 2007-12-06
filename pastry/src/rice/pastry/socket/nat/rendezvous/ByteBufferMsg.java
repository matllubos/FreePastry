package rice.pastry.socket.nat.rendezvous;

import java.io.IOException;
import java.nio.ByteBuffer;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.messaging.Message;

public class ByteBufferMsg extends Message implements RawMessage {
  public static final short TYPE = 1;
  
  ByteBuffer buffer;
  
  public ByteBufferMsg(ByteBuffer buf, int priority, int dest) {
    super(dest);
    this.buffer = buf;
    setPriority(priority);
  }
  
  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.write(buffer.array(), buffer.position(), buffer.remaining());
  }

}
