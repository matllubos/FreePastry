package rice.post.messaging;

import java.io.*;

import rice.post.*;

/**
 * This is abstraction of all messages in the Post system.  This
 * class is responsiable for maintaining the invariant that all
 * messages in Post are signed.  This class does this by the
 * following method:  the class has a transient signature object.
 * Whenever the class is serialized, this signature object is
 * not written (since it is transient).  However, sending the
 * message to another user requires that the signature be
 * sent too.  This is accomplished by calling the prepareForSend
 * method, which makes the next serialization include the signature.
 *
 * Thus, the message should be processed as follows:
 *
 * 1. Build message, and get it ready for sending.
 * 2. Serialize the message to a byte[].
 * 3. Sign this byte[] and call setSignature()
 * 4. Call prepareForSend()
 * 5. Send the message across the wire.
 *
 * Verifying the message should be done as follows:
 *
 * 1. Serialize te message to a byte[]
 * 2. Retrieve the attached signature
 * 3. Verify the signature.
 *
 */
public abstract class PostMessage implements Serializable {

  // the sender of this PostMessage
  private PostEntityAddress sender;

  // internal state-keeping for signature production
  private boolean aboutToBeWritten = false;

  // the signature for this message
  private transient byte[] signature;

  /**
   * Constructs a PostMessage given the name of the
   * sender.
   *
   * @param sender The sender of this message.
   */
  public PostMessage(PostEntityAddress sender) {
    if (sender == null) {
      throw new IllegalArgumentException("Attempt to build PostMessage with null sender!");
    }
    
    this.sender = sender;
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender
   */
  public final PostEntityAddress getSender() {
    return sender;
  }
  
  /**
   * Returns the signature for this message, or null
   * if the message has not yet been signed.
   *
   * @return The signature, or null if not yet signed.
   */
  public final byte[] getSignature() {
    return signature;
  }

  /**
   * Sets this message's signature, if one has not already
   * been assigned.
   *
   * @param signature The signature for this message.
   */
  public final void setSignature(byte[] signature) {
    if (signature != null) {
      this.signature = signature;
    }
  }

  /**
   * Tells this message that the next serialization will
   * need to include the signature. Calling this method is
   * necessary before sending this message across the
   * network.
   */
  public final void prepareForSend() {
    aboutToBeWritten = true;
  }
  
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();

    if (aboutToBeWritten) {
      oos.writeInt(signature.length);
      oos.write(signature);
    }
  }

  private void readObject(ObjectInputStream ois)
    throws IOException, ClassNotFoundException {

    ois.defaultReadObject();

    if (aboutToBeWritten) {
      signature = new byte[ois.readInt()];
      ois.read(signature, 0, signature.length);
    }
    
    aboutToBeWritten = false;
  }
}
