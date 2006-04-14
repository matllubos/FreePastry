package rice.email;

import java.io.*;
import java.util.*;

import rice.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;

/**
 * Represents the notion of a stored email: it contains the metadata (int UID and date),
 * the email and the Flags.
 *
 * @version $Id$
 * @author amislove
 */
public class StoredEmail implements Serializable, Comparable, Cloneable {
  
  /**
   * serial version for backwards compatibility
   */
  static final long serialVersionUID = -8624697817697280715L;

  /**
   * The internal email
   */
  protected Email _email;
  
  /**
   * The forever-unique identifier for this stored email
   */
  protected int _uid;
  
  /**
   * The current flags of this email
   */
  protected Flags _flags;
  
  /**
   * The internaldate of this email, or when it was appended to the folder
   */
  protected long internaldate;

  /**
   * Constructs a stored email
   *
   * @param email The email we are dealing with.
   * @param uid The unique UID for the email.
   * @param flags The flags on the email.
   */
  public StoredEmail(Email email, int uid, Flags flags, long internaldate) {
    this._uid = uid;
    this._email = email;
    this._flags = flags;
    this.internaldate = internaldate;
  }
  
  public StoredEmail(StoredEmail other, int uid) {
    this._uid = uid;
    this._email = other._email;
    this._flags = other._flags;
    this.internaldate = other.internaldate;
  }

  /**
   * Return the UID for the current email
   *
   * @return The UID for the email
   */
  public int getUID() {
    return _uid;
  }
  
  /**
   * Return the internaldate for the current email
   *
   * @return The internaldate for the email
   */
  public long getInternalDate() {
    return internaldate;
  }

  /**
   * Return the flags for the email
   *
   * @return The Flags for the email
   */
  public Flags getFlags() {
    return _flags;
  }

  /**
   * Return the email
   *
   * @return The Email.
   */
  public Email getEmail() {
    return _email;
  }
  
  /**
   * Returns the hashcode of this storedemail
   *
   * @return The hashcode
   */
  public int hashCode() {
    return _email.hashCode();
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param o DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean equals(Object o) {
    StoredEmail se = (StoredEmail) o;

    return (se._email.equals(_email) && //se._flags.equals(_flags) &&
            (se._uid == _uid) && (se.internaldate == internaldate));
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param o DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public int compareTo(Object o) {
    StoredEmail email = (StoredEmail) o;
    return getUID() - email.getUID();
  }
  
  public Object clone() {
    return new StoredEmail(_email, _uid, (Flags) _flags.clone(), internaldate);
  }
  
  public String toString() {
    return "[StoredEmail: " + _email + " uid " + _uid  + " date " + internaldate + " flags " + _flags + " ]";
  }
    
}

