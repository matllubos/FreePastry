package rice.post.log;

import java.security.*;
import java.security.cert.*;

import rice.post.*;

/**
 * This class represents the Log which sits at the address of the user's
 * PostUserAddress and points to the logs of other applications.  This
 * log itself has no entries.
 * 
 * This object overrides Log in order to contain the user's public key
 * and certificate for other users in the system to read.
 * 
 * @version $Id$
 */
public class PostLog extends Log {

  /**
   * The user of this log.
   */
  private PostEntityAddress user;

  /**
   * The public key of the user.
   */
  private PublicKey key;

  /**
   * The certificate of the user.
   */
  private java.security.cert.Certificate certificate;
  
  /**
   * Constructor for PostLog.
   *
   * @param user The user whom this LogHead is for
   * @param key The user's public key.
   * @param cert This user's certification
   */
  protected PostLog(PostEntityAddress user, PublicKey key, java.security.cert.Certificate cert) {
    super("User " + user.toString() + "'s log", user.getAddress());

    this.user = user;
    this.key = key;
    this.certificate = cert;
  }
    
  /**
   * Returns the user to whom this log head belongs
   *
   * @return The user who owns this LogHead.
   */
  public PostEntityAddress getEntityAddress() {
    return user;
  }
    
  /**
   * Returns the public key of the user who owns this log head
   *
   * @return The public key of the user who owns this LogHead.
   */
  public PublicKey getPublicKey() {
    return key;
  }

  /**
   * Returns the signature for this user
   *
   * @return The public key of the user who owns this LogHead.
   */
  public java.security.cert.Certificate getCertificatie() {
    return certificate;
  }
}

