package rice.post.security.ca;

import java.security.*;

import rice.post.*;
import rice.post.security.*;

/**
 * This class is the notion of a PostCertificate using the PKI (CA) based
 * authentication mechism.
 *
 * @version $Id$
 * @author amislove
 */
public class CAPostCertificate extends PostCertificate {

  /**
   * The signature which verifies this certificate
   */
  private byte[] signature;

  /**
   * Builds a PostCertificate from a user address and a public key.
   *
   * @param address The address of the user whose certificate this is
   * @param key The key of the user whose certificate this is
   * @param signature The signature which verifies this certificate
   */
  protected CAPostCertificate(PostEntityAddress address, PublicKey key, byte[] signature) {
    super(address, key);

    this.signature = signature;
  }

  /**
   * Gets the signature of the PostCertificate object
   *
   * @return The signature
   */
  public byte[] getSignature() {
    return signature;
  }
}
