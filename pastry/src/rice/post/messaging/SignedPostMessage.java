package rice.post.messaging;

import java.io.*;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

import rice.p2p.util.MathUtils;
import rice.p2p.util.SecurityUtils;

/**
 * This class is the representation of a PostMessage and
 * it's attached signature.  This class should be the
 * one which is sent across the wire.
 */
public final class SignedPostMessage implements Serializable {

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
    this.version = 1;
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
        return null;
      }
      return cachedMessage;
    }
    return message;
  }
  
  public byte[] getMessageBytes() {
    if (msg == null) {
      return msg;
    } else {
      try {
        msg = SecurityUtils.serialize(message);
      } catch (IOException e) {
        System.out.println("SignedPostMessage: Exception in getMessageBytes "+e);
        return new byte[0];
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
}
