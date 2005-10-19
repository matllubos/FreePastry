package rice.post.security.pknoi;

import java.security.*;

import rice.post.*;
import rice.post.security.*;

/**
 * This class is the notion of a PostCertificate using the PKnoI (web of trust) based
 * authentication mechanism.
 *
 * @version $Id$
 * @author amislove
 */
public class PKnoIPostCertificate extends PostCertificate {

  /**
   * Builds a PostCertificate from a user address and a public key.
   *
   * @param address The address of the user whose certificate this is
   * @param key The key of the user whose certificate this is
   */
  protected PKnoIPostCertificate(PostEntityAddress address, PublicKey key) {
    super(address, key);
  }
}
