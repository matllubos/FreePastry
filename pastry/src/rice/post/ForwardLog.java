package rice.post;

import java.io.*;
import java.security.*;
import java.security.cert.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.post.log.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * This class represents the log of forwarding addresses for a given user.
 * Other nodes which are sending notifications to this node should also send
 * notifications to all of the addresses listed in this log.
 * 
 * @version $Id$
 */
public class ForwardLog extends Log {
  
  /**
   * Serialver for backwards compatibility
   */
  public static final long serialVersionUID = 5516854362333868152L;
  
  /**
   * The universal name for this log
   */
  public static final String FORWARD_NAME = "NotificationForward";
  
  /**
   * The list of addresses to forward to
   */
  protected String[] addresses;
  
  /**
   * Constructor for ForwardLog.  Package protected: only Post can create
   * a ForwardLog.
   *
   * @param user The user whom this PostLog is for
   * @param key The user's public key.
   * @param cert This user's certification
   * @param post The local Post service
   * @param command The command to call once done
   */
  public ForwardLog(final PostLog log, String[] addresses, Id location, Post post, Continuation command) {
    super(FORWARD_NAME, location, post);
    
    this.addresses = addresses;
    
    sync(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        log.addChildLog(ForwardLog.this, parent);
      }
    });
  }
  
  /**
   * Returns the list of forward addresses
   *
   * @return The forward addresses
   */
  public String[] getAddresses() {
    return addresses;
  }
  
  /**
   * Updates the list of addresses
   *
   * @param addresses The new list of addresses
   */
  public void setAddresses(String[] addresses, Continuation command) {
    this.addresses = addresses;
    sync(command);
  }
  
  /**
   * Returns whether or not this log should be cached
   *
   * @return Whether or not this log should be cached
   */
  public boolean cache() {
    return false;
  }
  
  public String toString() {
    return "ForwardLog[" + addresses + "]";
  }
}

