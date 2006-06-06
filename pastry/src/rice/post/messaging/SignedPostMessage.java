package rice.post.messaging;

import java.io.*;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.util.MathUtils;
import rice.p2p.util.SecurityUtils;

/**
 * This class is the representation of a PostMessage and
 * it's attached signature.  This class should be the
 * one which is sent across the wire.
 */
public final class SignedPostMessage implements Serializable {
  public static final short TYPE = 13;

  // the PostMessage
  /**
   * Can't mess with this field in the deserialization or we
   * risk breaking PresenceMessages even more than they already
   * are for older versions of ePOST.
   * @deprecated
   */
  private PostMessage message;
  
  private transient PostMessage cachedMessage;
  
  private transient byte[] msg;
  
  private int version;
  private static int VERSION = 1;

  // the signature for this message
  private byte[] signature;
  
  static final long serialVersionUID = 7465703610144475310L;

  /**
   * Constructs a SignedPostMessage given the message and
   * siganture
   *
   * @param sender The sender of this message.
   */
  public SignedPostMessage(PostMessage message, PrivateKey key) throws IOException {
    this.message = message;
    this.cachedMessage = message;
    this.msg = SecurityUtils.serialize(message);
    this.signature = SecurityUtils.sign(msg, key);
    this.version = VERSION;
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender
   */
  public PostMessage getMessage() {
    if (cachedMessage != null) {
      return cachedMessage;
    } else if (msg != null) {
      try {
        cachedMessage = (PostMessage) SecurityUtils.deserialize(msg);
      } catch (Exception e) {
        System.out.println("SignedPostMessage: Exception in getMessage "+e);
        e.printStackTrace(System.out);
        return null;
      }
      return cachedMessage;
    }
    return message;
  }
  
  public byte[] getMessageBytes() {
    if (msg != null) {
      return msg;
    } else {
      try {
        msg = SecurityUtils.serialize(message);
      } catch (IOException e) {
        System.out.println("SignedPostMessage: Exception in getMessageBytes "+e);
        e.printStackTrace(System.out);
        return null;
      }
      return msg;
    }
  }

  /**
    * Returns the signature for this message, or null
   * if the message has not yet been signed.
   *
   * @return The signature, or null if not yet signed.
   */
  public byte[] getSignature() {
    return signature;
  }

  public boolean verify(PublicKey key) {
    return SecurityUtils.verify(getMessageBytes(), getSignature(), key);
  }
  
  public boolean equals(Object o) {
    if (o instanceof SignedPostMessage) {
      SignedPostMessage spm = (SignedPostMessage) o;
      return (getMessage().equals(spm.getMessage()) &&
              Arrays.equals(signature, spm.getSignature()));
    }

    return false;
  }
  
  public void dump() {
    if (msg != null)
      System.out.println("SignedPostMessage: msg: "+MathUtils.toHex(msg));
    if (signature != null)
      System.out.println("SignedPostMessage: signature: "+MathUtils.toHex(signature));
    if (message != null)
      System.out.println("SignedPostMessage: message: "+ message);
    if (cachedMessage != null)
      System.out.println("SignedPostMessage: cachedMessage: "+ cachedMessage);
  }

  public String toString() {
    return "[SPM " + getMessage() + "]";
  }

  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    if (msg == null) {
      if (version == 0) {
        // old-style; promote to newest-style
        version = VERSION;
        msg = SecurityUtils.serialize(message);
      } else {
        throw new IOException("SignedPostMessage is new style, but has no msg field!");
      }
    } else {
      // has msg field, but was not base64 serialized
      if (version == 0)
        version = VERSION;
    }
    
    oos.defaultWriteObject();

    oos.writeInt(msg.length);
    oos.write(msg);
  }
  
  /**
    * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    if (version != 0) {
      msg = new byte[ois.readInt()];
      ois.readFully(msg, 0, msg.length);
    }
  }

  public SignedPostMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    version = buf.readInt();
    int sigLength = buf.readInt();
    signature = new byte[sigLength];
    buf.read(signature);
    message = PostMessage.build(buf, endpoint, buf.readShort());
    
    if (version != 0) {
      msg = new byte[buf.readInt()];
      buf.read(msg);
    }    
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeInt(version);
    buf.writeInt(signature.length);
    buf.write(signature,0,signature.length);
    buf.writeShort(message.getType());
    message.serialize(buf);
    
    if (msg == null) {
      if (version == 0) {
        // old-style; promote to newest-style
        version = VERSION;
        msg = SecurityUtils.serialize(message);
      } else {
        throw new IOException("SignedPostMessage is new style, but has no msg field!");
      }
    } else {
      // has msg field, but was not base64 serialized
      if (version == 0)
        version = VERSION;
    }
    

    buf.writeInt(msg.length);
    buf.write(msg, 0, msg.length);
  }
}
