/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
@SuppressWarnings("unchecked")
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
