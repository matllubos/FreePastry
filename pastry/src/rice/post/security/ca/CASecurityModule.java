package rice.post.security.ca;

import java.io.*;
import java.security.*;

import rice.*;
import rice.post.*;
import rice.post.security.*;
import rice.p2p.util.*;

/**
 * This class is the security module which implements the PKI (CA) based
 * security system.
 *
 * @version $Id$
 * @author amislove
 */
public class CASecurityModule implements SecurityModule {

  /**
   * The name of the module
   */
  public static String MODULE_NAME = "CA";

  /**
   * The CA's well-known public key
   */
  private PublicKey caKey;

  /**
   * Constructor for CASecurityModule.
   *
   * @param caKey The well-known public key of the certificate authority
   */
  public CASecurityModule(PublicKey caKey) {
    this.caKey = caKey;
  }

  /**
   * Static method for generating a ceritificate from a user, public key, and
   * the CA's private key
   *
   * @param address The address of the user
   * @param key The public key of the user
   * @param caKey The private key of the certificate authority
   * @return A certificate for the user
   * @exception SecurityException If the certificate generation has a problem
   */
  public static CAPostCertificate generate(PostUserAddress address, PublicKey key, PrivateKey caKey) throws SecurityException {
    try {
      byte[] keyByte = SecurityUtils.serialize(key);
      byte[] addressByte = SecurityUtils.serialize(address);

      byte[] all = new byte[addressByte.length + keyByte.length];
      System.arraycopy(addressByte, 0, all, 0, addressByte.length);
      System.arraycopy(keyByte, 0, all, addressByte.length, keyByte.length);

      byte[] signature = SecurityUtils.sign(all, caKey);

      return new CAPostCertificate(address, key, signature);
    } catch (IOException e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
    }
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
    return (certificate instanceof CAPostCertificate);
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
      CAPostCertificate cert = (CAPostCertificate) certificate;
      byte[] keyByte = SecurityUtils.serialize(cert.getKey());
      byte[] addressByte = SecurityUtils.serialize(cert.getAddress());

      byte[] all = new byte[addressByte.length + keyByte.length];
      System.arraycopy(addressByte, 0, all, 0, addressByte.length);
      System.arraycopy(keyByte, 0, all, addressByte.length, keyByte.length);

      command.receiveResult(new Boolean(SecurityUtils.verify(all, cert.getSignature(), caKey)));
    } catch (IOException e) {
      throw new SecurityException("InvalidKeyException verifying object: " + e);
    }
  }
}
