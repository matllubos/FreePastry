package rice.past.search;

import java.io.Serializable;
import java.net.URL;

/**
 * @(#) Searchable.java
 *
 * Interface for objects in the Pastry Search Service to
 * implement if they wish to be able to be searched.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface Searchable {

  /**
   * Method which is called on the Searchable object
   * when the SearchService wishes to index this
   * document.  The Searchable object should return an
   * array of IndexEntries representing all of the
   * IndexKeys for this SearchObject, as well
   * as their scores.
   *
   * @return The array of IndexEntries for this
   *         Searchable object.
   */
  public IndexEntry[] catalog();

  /**
   * Method which returns the URL of this Searchable
   * object.
   *
   * @return This Searchable's URL
   */
  public URL getURL();

}