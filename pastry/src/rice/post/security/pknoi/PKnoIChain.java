package rice.post.security.pknoi;

import java.io.*;
import java.security.*;

import rice.post.*;
import rice.post.security.*;

/**
 * This class is the notion of a chain of "vouches" from one user to another in the
 * PKnoI POST system.  The chain can contain other metadata, such as the "validity"
 * of this chain (based on some metric) or similar information.
 *
 * @version $Id$
 * @author amislove
 */
public class PKnoIChain implements Serializable {

  /**
   * Builds a PKnoIChain from the to and from certificates, and the chain of
   * signatures which verify each other
   *
   * @param from The origin user
   * @param to The destination user
   * @param sigs The array of signatures
   */
  protected PKnoIChain(PKnoIPostCertificate from, PKnoIPostCertificate to, PKnoISignature[] sigs) {
  }

  /**
   * Returns the origin user for this cahin
   *
   * @return The origin of the chain
   */
  public PKnoIPostCertificate getFrom() {
    return null;
  }

  /**
   * Returns the destination user for this cahin
   *
   * @return The destination of the chain
   */
  public PKnoIPostCertificate getTo() {
    return null;
  }

  /**
   * Returns the signatures for this cahin
   *
   * @return The signatures for the chain
   */
  public PKnoISignature[] getSignatures() {
    return null;
  }
}
