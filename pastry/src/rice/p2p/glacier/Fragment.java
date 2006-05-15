package rice.p2p.glacier;

import java.io.*;

import rice.p2p.commonapi.rawserialization.*;

public class Fragment implements Serializable {
  private static final long serialVersionUID = -809948154556539350L;
  
  public transient byte payload[];

  public Fragment(int _size) {
    payload = new byte[_size];
  }
  
  public byte[] getPayload() {
    return payload;
  }
  
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    oos.writeInt(payload.length);
    oos.write(payload);
  }
  
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    payload = new byte[ois.readInt()];
    ois.readFully(payload, 0, payload.length);
  }
  
  public Fragment(InputBuffer buf) throws IOException {
    payload = new byte[buf.readInt()];
    buf.read(payload, 0, payload.length);
    
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeInt(payload.length);
    buf.write(payload, 0, payload.length);    
  }

  
}

