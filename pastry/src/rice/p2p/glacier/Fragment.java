package rice.p2p.glacier;

import java.io.*;

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
}

