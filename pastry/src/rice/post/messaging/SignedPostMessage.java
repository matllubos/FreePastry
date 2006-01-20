package rice.post.messaging;

import java.io.*;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

import rice.p2p.util.MathUtils;
import rice.p2p.util.SecurityUtils;
import rice.post.*;
import rice.post.security.*;

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
  
  private byte[] msg;

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
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender
   */
  public PostMessage getMessage() {
    if (cachedMessage != null)
      return cachedMessage;
    return message;
  }
  
  public byte[] getMessageBytes() {
    return msg;
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

  private void readObject(java.io.ObjectInputStream in)
    throws IOException, ClassNotFoundException 
  {
    in.defaultReadObject();
    if ((msg == null) && (message == null)) {
      throw new IOException("can't deserialze signedPostMessage with both fields null");
    }
    if (msg == null) {
      // old-style message
      System.out.println("SignedPostMessage: Old-style message: "+message);
      msg = SecurityUtils.serialize(message);
      cachedMessage = message;
    } else {
      cachedMessage = (PostMessage)SecurityUtils.deserialize(msg);
      System.out.println("SignedPostMessage: New-style message: "+cachedMessage);
    }
  }
}
