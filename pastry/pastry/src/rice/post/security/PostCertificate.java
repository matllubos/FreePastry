package rice.post.security;

import java.io.*;
import java.security.*;

import rice.post.*;

/**
 * This class is the abstraction of a certificate in the POST system, regardless
 * of the underlying security model. This class contains an address and a key.
 *
 * @version $Id$
 * @author amislove
 */
public abstract class PostCertificate implements Serializable {

  private PostEntityAddress address;
  private PublicKey key;

  /**
   * Builds a PostCertificate from a user address and a public key.
   *
   * @param address The address of the user whose certificate this is
   * @param key The key of the user whose certificate this is
   */
  protected PostCertificate(PostEntityAddress address, PublicKey key) {
    this.address = address;
    this.key = key;
  }

  /**
   * Gets the Address attribute of the PostCertificate object
   *
   * @return The Address value
   */
  public PostEntityAddress getAddress() {
    return address;
  }

  /**
   * Gets the Key attribute of the PostCertificate object
   *
   * @return The Key value
   */
  public PublicKey getKey() {
    return key;
  }

  /**
   * Returns whether or not this object is equal
   *
   * @param o The object to compare to
   * @return Whether or not this one is equal.
   */
  public boolean equals(Object o) {
    if (!(o instanceof PostCertificate)) {
      return false;
    }

    PostCertificate cert = (PostCertificate) o;

    return (address.equals(cert.getAddress()) && key.equals(cert.getKey()));
  }
}
