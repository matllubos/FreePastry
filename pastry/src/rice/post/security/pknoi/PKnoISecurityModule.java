package rice.post.security.pknoi;

import java.io.*;
import java.security.*;

import rice.*;
import rice.post.*;
import rice.post.security.*;

/**
 * This class is the security module which implements the PKnoI (web of trust) based
 * security system.
 *
 * @version $Id$
 * @author amislove
 */
public class PKnoISecurityModule implements SecurityModule {

  /**
   * The name of the module
   */
  public static String MODULE_NAME = "PKnoI";

  /**
   * Constructor for PKnoISecurityModule.
   *
   * @param post The local post service
   */
  public PKnoISecurityModule(Post post) {
  }

  /**
   * Static method for generating a ceritificate from a user and public key
   *
   * @param address The address of the user
   * @param key The public key of the user
   * @return A certificate for the user
   * @exception SecurityException If the certificate generation has a problem
   */
  public static PKnoIPostCertificate generate(PostUserAddress address, PublicKey key) {
    return new PKnoIPostCertificate(address, key);
  }

  /**
   * Gets the unique name of the SecurityModule object
   *
   * @return The Name value
   */
  public String getName() {
    return MODULE_NAME;
  }

  /**
   * This method returns whether or not this module is able to verify the given
   * certificate.
   *
   * @param certificate The certificate in question
   * @return Whether or not this module can verify the certificate
   */
  public boolean canVerify(PostCertificate certificate) {
    return (certificate instanceof PKnoIPostCertificate);
  }

  /**
   * This method verifies the provided ceritifcate, and returns the result to
   * the continuation (either True or False).
   *
   * @param certificate The certificate to verify
   * @param command The command to run once the result is available
   * @exception SecurityException If the certificate verification has a problem
   */
  public void verify(PostCertificate certificate, Continuation command) throws SecurityException {
    try {
      PKnoIPostCertificate cert = (PKnoIPostCertificate) certificate;
      
      command.receiveResult(new Boolean(true));
    } catch (Exception e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
    }
  }
}
