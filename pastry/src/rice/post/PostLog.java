package rice.post;

import java.io.*;
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
   * Serialver for backwards compatibility
   */
  public static final long serialVersionUID = 5516854362333868152L;

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
   * Any extra data, or specifically, the head of one's aggregation log
   */
  protected Serializable aggregate;
  
  /**
   * Constructor for PostLog.  Package protected: only Post can create
   * a PostLog.
   *
   * @param user The user whom this PostLog is for
   * @param key The user's public key.
   * @param cert This user's certification
   * @param post The local Post service
   * @param command The command to call once done
   */
  PostLog(PostEntityAddress user, PublicKey key, PostCertificate cert, Post post, Continuation command) {
    super("User " + user.toString() + "'s log", user.getAddress(), post);
 
    this.user = user;
    this.key = key;
    this.certificate = cert;
    
    sync(command);
  }
  
  /**
   * Constructor for PostLog, which effectively renames a previous user to a new username.  
   * All of the user's metadata is taken from the the provided PostLog.
   *
   * @param user The user whom this PostLog is for
   * @param key The user's public key.
   * @param cert This user's certification
   * @param post The local Post service
   * @param previous The previous PostLog of the user
   * @param command The command to call once done
   */
  PostLog(PostEntityAddress user, PublicKey key, PostCertificate cert, Post post, PostLog previous, Continuation command) {
    super("User " + user.toString() + "'s log", user.getAddress(), post);
    
    this.user = user;
    this.key = key;
    this.certificate = cert;
    
    // now, we copy the necessary metadata over
    this.children = previous.children;
    this.topEntryReference = previous.topEntryReference;
    
    sync(command);
  }
  
  /**
   * @return THe head of the aggregation log
   */
  public Serializable getAggregateHead() {
    return aggregate;
  }
  
  /**
   * Updates the head of the aggregation log
   *
   * @param aggregate The current head of the aggregation log
   */
  public void setAggregateHead(Serializable aggregate) {
    this.aggregate = aggregate;
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

