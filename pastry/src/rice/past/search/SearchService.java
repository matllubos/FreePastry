package rice.past.search;

import java.net.URL;

/**
 * @(#) SearchService.java
 *
 * The interface to the SearchService itself.
 * This is how a SearchClient communicates with
 * the service.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface SearchService {

  /**
   * Performs the search and returns an ordered
   * list of SearchObjects. They are ordered according
   * to their score.
   *
   * @param keys The unordered list of query terms.
   */
  public URL[] query(IndexKeyString[] keys);

  /**
   * Used for indexing, this allows
   * the client to inform the service that
   * the given SearchObject needs to be indexed.
   * The service will in-turn contact the node
   * responsible for this document and ask it to
   * be indexed.
   * <p>
   * If the document has already been indexed,
   * calling this is the same as re-indexing.
   *
   * @param object The SearchObject that needs
   * to be re-indexed.
   */
  public void index(Searchable object);

}