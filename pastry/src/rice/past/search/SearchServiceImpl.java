package rice.past.search;

import java.util.Vector;
import java.util.Hashtable;
import java.net.URL;
import java.util.Date;
import java.util.Arrays;

import rice.past.PASTService;

import ObjectWeb.Security.Credentials;

/**
 * @(#) SearchServiceImpl.java
 *
 * Class which is the implementation of the Search
 * Service on top of Pastry.  One instance of this
 * service is designed to be running on each Pastry
 * node in order to provide a search service for
 * Pastry. <p>
 *
 * This class is designed to use the PAST document
 * storage system in order to handle the archiving,
 * retrieval, and replication of search indices. <p>
 *
 * The services that this layer provides are searching
 * combinations (such as handling the 'and' keyword) and
 * the indexing of documents.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SearchServiceImpl implements SearchService {

  private PASTService _past;
  public static int MAX_CACHED_TUPLE = 2;

  /**
   * Creates a SearchServiceImpl given a PASTService.
   *
   * @param past The PAST service used for storage.
   */
  public SearchServiceImpl(PASTService past) {
    _past = past;
  }

  /**
   * Performs the search and returns an ordered
   * list of SearchObjects. They are ordered according
   * to their score.
   *
   * @param keys The unordered list of query terms.
   */
  public URL[] query(IndexKeyString[] keys) {
    if (keys.length < 1) {
      throw new IllegalArgumentException("Queries must have at least one term.");
    }

    // create overall IndexKeyString
    IndexKeyString overallKey = new IndexKeyString(keys);

    Combinator c = new Combinator(keys);
    Vector searchTerms = new Vector();
    int totalFound = 0;

    // loop over all of the combinations of the search terms
    // finding the largest subset of them with a cached entry
    // in PAST.
    while (totalFound < keys.length) {
      IndexKeyString[] theseKeys = findHighest(c, keys.length);

      if (theseKeys.length == 0)
        return new URL[0];

      totalFound += theseKeys.length;
      searchTerms.addElement(theseKeys);
      c = new Combinator(c.getKeys(), theseKeys);
    }

    Vector searchResults = new Vector();

    // for each of the subsets, retrieve the list of IndexEntries
    for (int i=0; i<searchTerms.size(); i++) {
      IndexKeyString thisKey = new IndexKeyString((IndexKeyString[]) searchTerms.elementAt(i));
      searchResults.addElement(_past.lookup(thisKey.getNodeId()).getUpdates());
    }

    // intersect all of the subset lists, and order the result
    IndexEntry[] intersection = intersection(overallKey, searchResults);
    Arrays.sort(intersection);

    // cache the result, if it is deemed necessary
    if (keys.length <= MAX_CACHED_TUPLE)
      cache(overallKey, intersection);

    // retrieve the URLs from the IndexEntry array
    URL[] result = new URL[intersection.length];
    for (int i=0; i<intersection.length; i++) {
      result[i] = intersection[i].getURL();
    }

    return result;
  }

  /**
   * Given a Combinator c and a maximum tuple size, this
   * method will find the largest tuple that has an entry in
   * PAST, and return an array of the IndexKeyStrings representing
   * that entry.
   *
   * @param c The Combinator to get combinations from.
   * @param max_size The maximum tuple size to consider.
   * @return An array of IndexKeyStrings from c which is the
   *         largest tuple having an entry in PAST. If none
   *         of the IndexKeyStrings have an entry in PAST,
   *         an empty array is returned.
   */
  public IndexKeyString[] findHighest(Combinator c, int max_size) {
    int size = 0;
    if (max_size > c.size())
      size = c.size();
    else
      size = max_size;

    // grab all combinations of a single size, and see if they
    // exist in PAST
    while (size > 0) {
      Vector v = c.getCombination(size);

      for (int i=0; i<v.size(); i++) {
        IndexKeyString[] theseKeys = (IndexKeyString[]) v.elementAt(i);

        IndexKeyString k = new IndexKeyString(theseKeys);
        if (_past.exists(k.getNodeId()))
          return theseKeys;
      }

      size--;
    }

    return new IndexKeyString[0];
  }

  /**
   * Performs an intersection over a Vector of Vectors of IndexEntries.
   * This method also calculates the new scores of the documents,
   * and caches the result into PAST.
   *
   * @param key The overall key we are searching for.
   * @param vectors A Vector of Vectors of IndexEntries, representing
   *        each of the individual search term inverted indicies.
   * @return An array of IndexEntries representing the intersection.
   */
  public IndexEntry[] intersection(IndexKeyString key, Vector vectors) {
    if (vectors.size() == 0)
      return new IndexEntry[0];

    int smallestNum = ((Vector) vectors.elementAt(0)).size();
    int smallest = 0;

    Hashtable[] tables = new Hashtable[vectors.size()];

    // build up hashtables for each of the IndexEntry vectors, mapping
    // URL -> IndexEntry.
    // also, find the smallest list with which we will perform the
    // intersection
    for (int i=0; i<vectors.size(); i++) {
      tables[i] = new Hashtable();

      Vector theseEntries = (Vector) vectors.elementAt(i);
      for (int j=0; j<theseEntries.size(); j++) {
        IndexEntry thisEntry = (IndexEntry) theseEntries.elementAt(j);
        tables[i].put(thisEntry.getURL(), thisEntry);
      }

      if (theseEntries.size() < smallestNum) {
        smallestNum = theseEntries.size();
        smallest = i;
      }
    }

    // walk over smallest, getting their entries
    Vector smallestEntries = (Vector) vectors.elementAt(smallest);
    Vector results = new Vector();

    // perform the intersection by walking over the smallest list
    // and seeing if each of the elements is in all of the other
    // lists.  for each URL that is in all of the lists, create
    // a new IndexEntry and store the result
    for (int i=0; i<smallestEntries.size(); i++) {
      IndexEntry thisEntry = (IndexEntry) smallestEntries.elementAt(i);
      URL url = thisEntry.getURL();
      double rank = 1;
      Date expiration = thisEntry.getExpiration();
      Credentials cred = thisEntry.getCredentials();

      boolean inAll = true;

      // check all other lists
      for (int j=0; inAll && (j<tables.length); j++) {
        IndexEntry thisTableEntry = (IndexEntry) tables[j].get(url);
        if (thisTableEntry == null) {
          inAll = false;
        } else {
          // the rank of the new IndexEntry will be the product of all
          // of the others
          rank = rank * thisTableEntry.getRank();

          // the Date of the new IndexEntry will be the minimum of all
          // of the others
          if (thisTableEntry.getExpiration().compareTo(expiration) < 0)
            expiration = thisTableEntry.getExpiration();
        }
      }

      // build the new IndexEntry, if necessary
      if (inAll) {
        IndexEntry newEntry = new IndexEntry(key, url, rank, expiration, cred);
        results.addElement(newEntry);
      }
    }

    IndexEntry[] arrayResults = new IndexEntry[results.size()];

    for (int i=0; i<results.size(); i++) {
      arrayResults[i] = (IndexEntry) results.elementAt(i);
    }

    return arrayResults;
  }


  /**
   * Caches a computed tuple, if no such entry exists in the
   * system.
   *
   * @param key The IndexKeyString of this entry
   * @param entry The array of IndexEntries referring to this key.
   */
  public void cache(IndexKeyString key, IndexEntry[] entry) {
    if (! _past.exists(key.getNodeId())) {
      _past.insert(key.getNodeId(), new IndexPlaceholder(key), null);

      // TO DO: maintainance of index
      for (int i=0; i<entry.length; i++) {
        _past.update(key.getNodeId(), entry[i], null);
      }
    }
  }



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
  public void index(Searchable object) {
    IndexEntry[] entries = object.catalog();

    for (int i=0; i<entries.length; i++) {
      IndexEntry thisEntry = entries[i];
      IndexKeyString thisKey = thisEntry.getIndexKeyString();

      if (! _past.exists(thisKey.getNodeId())) {
        _past.insert(thisKey.getNodeId(), new IndexPlaceholder(thisKey), null);
      }

      _past.update(thisKey.getNodeId(), thisEntry, null);
    }
  }
}