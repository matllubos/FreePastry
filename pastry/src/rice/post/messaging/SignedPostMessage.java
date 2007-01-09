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
