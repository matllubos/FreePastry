package rice.post.security;

import java.io.*;

/**
* This class is a wrapper for a signature in the POST system.
 *
 * @version $Id$
 */
public class PostCertificate extends PostSignature {

  /**
  * Builds a signature from a given byte[]
   *
   * @param certificate The certificate in bytes
   */
  protected PostCertificate(byte[] certificate) {
    super(certificate);
  }
  
}
