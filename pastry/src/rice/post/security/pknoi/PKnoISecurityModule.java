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
package rice.post.security.pknoi;

import java.io.*;
import java.security.*;

import rice.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.storage.*;
import rice.post.messaging.*;
import rice.post.security.*;

/**
 * This class is the security module which implements the PKnoI (web of trust) based
 * security system.
 *
 * @version $Id$
 * @author amislove
 */
@SuppressWarnings("unchecked")
public class PKnoISecurityModule extends PostClient implements SecurityModule {

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

  /**
   * This method is how the Post object informs the clients
   * that there is an incoming notification.  This should never be called on the
   * PKnoI client.
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm, Continuation command) {
    command.receiveResult(new Boolean(true));
    // NOTHING NOW
  }
  
  /**
   * This method is periodically invoked by Post in order to get a list of
   * all handles under which the application has live objects.  This used to
   * implement the garbage collection service, thus, the application must
   * ensure that all data which it is still interested in is returned.
   *
   * The applications should return a PastContentHandle[] containing all of 
   * the handles The application is still interested in to the provided continatuion.
   */
  public void getContentHashReferences(Continuation command) {
    command.receiveResult(new ContentHashReference[0]);
  }
  
  /**
    * This method is periodically invoked by Post in order to get a list of
   * all mutable data which the application is interested in.
   *
   * The applications should return a Log[] containing all of 
   * the data The application is still interested in to the provided continatuion.
   */
  public void getLogs(Continuation command) {
    command.receiveResult(new Log[0]);
  }

  /**
   * This method will attempt to find all chains of length up to len, and return
   * a PKnoIChain[] to the continuation once all chains have been completed.  Note that
   * performing this method for length longer than 3 or 4 is not recommended, as the
   * algorithm is DFS and is of O(e^len).
   *
   * @param destination the certificate to look for
   * @param source The starting user
   * @param len The maximum chains length to find
   * @param command The command to return the result o
   */
  public void findChains(PKnoIPostCertificate source, PKnoIPostCertificate destination, int len, Continuation command) {
  }

  /**
   * This method should be called when this user wishes to "vouch" for the user with
   * the provided certificate.  This should *ONLY* be called if the user has estabilished
   * this user's identity through out-of-band means.  Note that other users added this way
   * will be visible to the world, and is considered an affirmation of the user.
   *
   * @param cert The certificate to vouch for
   * @param command The command to run with the success/failure
   */
  public void addPublic(PKnoIPostCertificate cert, Continuation command) {
  }

  /**
   * This method should be called when this user wishes to record a non-verified certificate
   * for later use.  This users are hidden from the rest of the world.
   *
   * @param cert The certificate to add
   * @param command The command to run with the success/failure
   */
  public void addPrivate(PKnoIPostCertificate cert, Continuation command) {
  }
  
}
