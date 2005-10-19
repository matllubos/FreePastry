package rice.past.search;

import java.util.Date;
import java.io.Serializable;

import ObjectWeb.Persistence.Persistable;
import ObjectWeb.Persistence.PersistenceID;
import ObjectWeb.Security.Credentials;

/**
 * @(#) IndexPlaceholder.java
 *
 * Class which is designed to be the placeholder in PAST of
 * a inverted index list.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class IndexPlaceholder implements Persistable, Serializable {

  private IndexKeyString _key;

  /**
   * Constructor for creating an IndexEntry given a search
   * term key, a document to search, the rank of the search
   * term, and the expiration date of the entry.
   *
   * @param key The "search term" of this entry.
   * @param doc The document this term is referencing.
   * @param rank The rank of this key in this doc.
   * @param expiration The time of expiration of this entry.
   * @param credentials The credentials of the author of this IndexEntry
   */
  public IndexPlaceholder(IndexKeyString key) {
    _key = key;
  }

  /**
   * Returns the IndexKey of this entry.
   *
   * @return The IndexKey for this IndexEntry.
   */
  public IndexKeyString getIndexKeyString() {
    return _key;
  }

  /**
   * Returns the Object's credentials.
   *
   * @return The objects's credential object
   */
  public Credentials getCredentials() {
    return null;
  }

  /**
   * Called by the <code>PersistenceManager</code> in the context of the
   * <code>recoverPersistentObjects</code> method just after an object was
   * recovered. Passes the object's new <code>PersistanceID</code> object as an
   * argument.
   *
   * <p> The object can use this pid to access any <code>PersistentStorage</code>
   * objects it may own.
   *
   * @param pid The object's new <code>PersistenceID</code>
   */
  public void reActivate(PersistenceID pid) {
  }

}