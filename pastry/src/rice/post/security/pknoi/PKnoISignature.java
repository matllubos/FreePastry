package rice.post.security.pknoi;

import java.io.*;
import java.security.*;

import rice.post.*;
import rice.post.security.*;

/**
 * This class is the notion of a verification (or vouching) in the PKnoI system.
 *
 * @version $Id$
 * @author amislove
 */
public class PKnoISignature implements Serializable {

  /**
   * Builds a PKnoIChain from the to and from certificates, and the chain of
   * signatures which verify each other
   *
   * @param from The origin user
   * @param to The destination user
   * @param sigs The array of signatures
   */
  protected PKnoISignature(PKnoIPostCertificate signee, PKnoIPostCertificate signer, byte[] sig) {
  }

  /**
   * Returns the user who is the signee
   *
   * @return The signee
   */
  public PKnoIPostCertificate getSignee() {
    return null;
  }

  /**
   * Returns the signer
   *
   * @return The signer
   */
  public PKnoIPostCertificate getSigner() {
    return null;
  }

  /**
   * Returns the signature
   *
   * @return The signature
   */
  public byte[] getSignature() {
    return null;
  }
}
