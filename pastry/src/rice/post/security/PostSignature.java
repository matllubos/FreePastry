package rice.post.security;

import java.io.*;

/**
 * This class is a wrapper for a signature in the POST system.
 *
 * @version $Id$
 */
public class PostSignature implements Serializable {

  private byte[] sig;

  /**
   * Builds a signature from a given byte[]
   *
   * @param sig The sig of this signature
   */
  protected PostSignature(byte[] sig) {
    this.sig = sig;
  }

  protected byte[] getSignature() {
    return sig;
  }
}
