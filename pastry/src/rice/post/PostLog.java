package rice.post;

import java.security.*;
import java.security.cert.*;

import rice.*;
import rice.post.log.*;
import rice.post.storage.*;
import rice.post.security.*;

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
  private PostCertificate certificate;
  
  /**
   * Constructor for PostLog.  Package protected: only Post can create
   * a PostLog.
   *
   * @param user The user whom this PostLog is for
   * @param key The user's public key.
   * @param cert This user's certification
   */
  PostLog(PostEntityAddress user, PublicKey key, PostCertificate cert, Post post, Continuation command) {
    super("User " + user.toString() + "'s log", user.getAddress(), post);
 
    this.user = user;
    this.key = key;
    this.certificate = cert;

    sync(command);
  }
    
  /**
   * @return The user who owns this PostLog.
   */
  public PostEntityAddress getEntityAddress() {
    return user;
  }
    
  /**
   * @return The public key of the user who owns this PostLog.
   */
  public PublicKey getPublicKey() {
    return key;
  }

  /**
   * @return The certificate for the user who owns this PostLog.
   */
  public PostCertificate getCertificate() {
    return certificate;
  }

  public String toString() {
    return "PostLog[" + user + "]";
  }
}

