package rice.post.security;

import java.io.*;
import java.security.*;

import rice.*;
import rice.post.*;

/**
 * This interface represents the abstraction of a security module, which can be
 * used to verify certificates.
 *
 * @version $Id$
 * @author amislove
 */
public interface SecurityModule {

  /**
   * Gets the unique name of the SecurityModule object
   *
   * @return The Name value
   */
  public String getName();

  /**
   * This method verifies the provided ceritifcate, and returns the result to
   * the continuation (either True or False).
   *
   * @param certificate The certificate to verify
   * @param command The command to run once the result is available
   */
  public void verify(PostCertificate certificate, Continuation command);

  /**
   * This method returns whether or not this module is able to verify the given
   * certificate.
   *
   * @param certificate The certificate in question
   * @return Whether or not this module can verify the certificate
   */
  public boolean canVerify(PostCertificate certificate);
}
