package rice.post.log;

import java.security.*;

import rice.post.*;

/**
 * Class which represents the information located at the user's
 * PostUserAddress and contains pointers to each of the user's
 * application logs, as well as the user's identity, certificate,
 * and public key.
 */
public class UserBlock {

  /**
   * Constructs a user block given an address, key, and certificate
   *
   * @param entity The address of the entity who owns this block
   * @param key The public key of this entity
   * @param signature The sig of the CA
   */
  public UserBlock(PostEntityAddress address, PublicKey key, Signature signature) {
  }

  /**
   * Method by which Post can add a log reference to this userblock.
   *
   * @param application The application requesting a log.
   * @param reference The reference to this application's log.
   */
  public void addLog(PostClientAddress address, LogHeadReference reference) {
  }

  /**
   * Method by which Post can remove a log reference from this userblock.
   *
   * @param application The application whose log we want to remove
   */
  public void removeLog(PostClientAddress address) {
  }

  /**
   * Method by which a client can determine which applications have logs
   *
   * @return An array[] of class address, representing the applications which have logs
   */
  public PostClientAddress[] getApplicationsWithLogs() {
    return null;
  }

  /**
   * Method by which a client can get a reference to a specific application's
   * log.
   *
   * @param address The application whose log to return
   * @return A reference to the requested application's log
   */
  public LogHeadReference getLog(PostClientAddress address) {
    return null;
  }
}

