package rice.past.search;

import java.util.Date;
import java.io.Serializable;
import java.net.URL;

import ObjectWeb.Persistence.Persistable;
import ObjectWeb.Persistence.PersistenceID;
import ObjectWeb.Security.Credentials;

/**
 * @(#) IndexEntry.java
 *
 * Interface which represents a entry for a document in
 * the inverted index.  This class will contain the
 * IndexKey search term, the referenced Searchable object,
 * the score of IndexKey in this document, and an
 * expiration date of time entry.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class IndexEntry implements Persistable, Serializable, Comparable {

  private IndexKeyString _key;
  private URL _url;
  private double _rank;
  private Date _expiration;
  private Credentials _credentials;

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
  public IndexEntry(IndexKeyString key, URL url,
                    double rank, Date expiration,
                    Credentials credentials) {
    _key = key;
    _url = url;
    _rank = rank;
    _expiration = expiration;
    _credentials = credentials;
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
   * Returns the Searchable document for this entry.
   *
   * @return This IndexEntry's Searchable document.
   */
  public URL getURL() {
    return _url;
  }

  /**
   * Returns the rank of this entry.
   *
   * @return The rank of this IndexEntry.
   */
  public double getRank() {
    return _rank;
  }

  /**
   * Returns the expiration date of this entry.
   *
   * @return The expiration Date of this entry.
   */
  public Date getExpiration() {
    return _expiration;
  }

  /**
   * Extends the life of this entry until a specified
   * date.
   *
   * @param date The new expiration date of this entry.
   */
  public void extendLife(Date date) {
    _expiration = date;
  }

  /**
   * Returns the Object's credentials.
   *
   * @return The objects's credential object
   */
  public Credentials getCredentials() {
    return _credentials;
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

  /**
   * Method provided in order to implement the Comparable interface.
   *
   * @param o The object to compare this to.
   * @return 1, if o < this; 0 if 0 == this, or -1 of o > this
   */
  public int compareTo(Object o) {
    if (o instanceof IndexEntry) {
      IndexEntry thisEntry = (IndexEntry) o;
      if (thisEntry.getRank() < getRank()) {
        return -1;
      } else if (thisEntry.getRank() > getRank()) {
        return 1;
      } else {
        return 0;
      }
    } else {
      throw new IllegalArgumentException("Can not compare IndexEntry with non-IndexEntry");
    }
  }

}