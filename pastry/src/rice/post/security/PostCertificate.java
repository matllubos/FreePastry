package rice.post.security;

import rice.post.*;

import java.io.*;
import java.security.*;

/**
* This class is a wrapper for a signature in the POST system.
 *
 * @version $Id$
 */
public class PostCertificate extends PostSignature {

  private PostEntityAddress address;
  private PublicKey key;
  
  /**
  * Builds a signature from a given byte[]
   *
   * @param certificate The certificate in bytes
   */
  protected PostCertificate(PostEntityAddress address, PublicKey key, byte[] certificate) {
    super(certificate);

    this.address = address;
    this.key = key;
  }

  public PostEntityAddress getAddress() {
    return address;
  }

  public PublicKey getKey() {
    return key;
  }

  public boolean equals(Object o) {
    if (! (o instanceof PostCertificate)) {
      return false;
    }

    PostCertificate cert = (PostCertificate) o;

    return (super.equals(cert) &&
            address.equals(cert.getAddress()) &&
            key.equals(cert.getKey()));
  }
}
