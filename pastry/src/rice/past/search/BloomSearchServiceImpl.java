package rice.past.search;

import java.io.Serializable;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import rice.past.PASTService;
import rice.pastry.NodeId;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;
import rice.pastry.routing.SendOptions;
import rice.pastry.security.Credentials;
import rice.pastry.security.PermissiveCredentials;

/**
 * This class provides an implementation of the search service by
 * using bloom filter representations of the results for each individual
 * search result.  These bloom filters are passed around rather than the 
 * search results, minimizing bandwidth usage.
 *
 * @author Derek Ruths
 */
public class BloomSearchServiceImpl
    extends PastryAppl
    implements SearchService {

    public static int MAX_CACHED_TUPLE = 2;
    
    private static int waitID = 0;
  
    /**
     * This constant represents the tolerated percent false positive 
     * returned by the bloom filters used by this implementation.  Currently
     * it is set to allow up to a 20% false positive rate.
     */  
    private static double PERR_COEFF = -0.22314; // perr = .2
    
    // fields
    private static BloomSearchAddress address = new BloomSearchAddress();
    private Credentials credentials;
    private PASTService pastService;
    
    private Hashtable waitHash = new Hashtable();
    
    /**
     * This class is used as a helper object for sorting purposes.  The
     * {@link SearchTermComparator} sorts these objects according to 
     * their resultSize.
     */
    private class SearchTermContainer {
    
        private IndexKeyString term;
        private int resultSize;
        
        public SearchTermContainer(IndexKeyString term, int resultSize) {
            this.resultSize = resultSize;
            this.term = term;   
        }
        
        public int getResultSize() {
            return this.resultSize;   
        }
        
        public IndexKeyString getTerm() {
            return this.term;   
        }
    }
    
    /**
     * This class compares two SearchTermContainers by looking at their relative
     * result sizes.  A SearchTermContainer is larger if its result size is larger.
     */
    private class SearchTermComparator implements Comparator {
        
        /**
         * @see java.util.Comparator#compare(Object, Object)
         */
        public int compare(Object arg0, Object arg1) {
            
            SearchTermContainer stc1 = (SearchTermContainer) arg0;
            SearchTermContainer stc2 = (SearchTermContainer) arg1;
            
            if(stc1.getResultSize() < stc2.getResultSize()) {
                return -1;   
            } else if(stc1.getResultSize() == stc2.getResultSize()) {
                return 0;   
            } else {
                return 1;   
            }
        }

    }
    
    // constructors
    /**
     * This constructs a search service.
     *
     * @param pn is the pastry node that the search service will sit on.
     * @param ps is the past service which the local search service will bind
     * to.
     */
    public BloomSearchServiceImpl(PastryNode pn, PASTService ps) {
        super(pn);
        
        this.pastService = ps;
        this.credentials = new PermissiveCredentials();
    }
    
    /**
     * @see rice.past.search.SearchService#query(IndexKeyString[])
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

            // if there aren't any subsets which exist, then there
            // clearly aren't any objects which satisfy this search
            // query.  Return an empty set.
            if (theseKeys.length == 0) {
                return new URL[0];
            }

            // Determine where we should look for the results
            IndexKeyString thisKey = new IndexKeyString(theseKeys);
            
            // get the number of objects that the system holding this store
            // would return if we requested them all.
            int resultSize = getQueryResultSize(thisKey);

            searchTerms.add(new SearchTermContainer(thisKey, resultSize));
            
            totalFound += theseKeys.length;
            c = new Combinator(c.getKeys(), theseKeys);
        }

        // order the search terms according to how large the result will be 
        // large = how many results are associated with the search
        Object[] array = searchTerms.toArray();
        Arrays.sort(array, new SearchTermComparator()); 
        
        IndexKeyString[] searchTermsArray = new IndexKeyString[array.length];
        
        // TODO: Is there a better/faster way to copy this array?
        for(int i = 0; i < array.length; i++) {
            searchTermsArray[i] = ((SearchTermContainer)array[i]).getTerm();
        }
        
        // perform the search
        Vector ieResults = recurSearch(searchTermsArray[0].getNodeId(), null, searchTermsArray);

        URL[] urlResults = new URL[ieResults.size()];
        IndexEntry[] idxResults = new IndexEntry[ieResults.size()];
                
        for(int i = 0; i < urlResults.length; i++) {
            
            urlResults[i] = ((IndexEntry) ieResults.elementAt(i)).getURL();   
            idxResults[i] = (IndexEntry) ieResults.elementAt(i);
        }

        // cache the result, if it is deemed necessary
        if (keys.length <= MAX_CACHED_TUPLE) {
            this.cache(overallKey, idxResults);
        }
        
        return urlResults;
    }

    /**
     * This method sends a request for a search to be performed by a remote system.
     * 
     * @param dest is the system that will perform the search.
     * @param bf is the bloom filter that will be used in the search.
     * @param terms is the set of terms that will be ANDed together in order to
     * perform the search.
     * 
     * @return a vector of {@link IndexEntry} objects refering to the entries 
     * which satisfy the search.
     */
    private Vector recurSearch(NodeId dest, BloomFilter bf, IndexKeyString[] terms) {
        
        ResultLock rl = new ResultLock();        
        Integer ourWaitID = new Integer(BloomSearchServiceImpl.waitID++);
        
        this.waitHash.put(ourWaitID, rl);
        
        // request processing for the rest of the terms
        Message msg = new SearchMessage(this.getNodeId(), ourWaitID.intValue(), this.getAddress(), this.getCredentials(), bf, terms);
        this.routeMsg(dest, msg, this.getCredentials(), new SendOptions());
            
        // wait for the result to return
        return rl.getResult();
    }

    /**
     * This method performs a search on the specified terms and the contents of
     * the bloom filter.  This method assumes a boolean AND between all terms
     * specified.  It also assumes that all terms must be contained in the
     * bloom filter specified.
     * 
     * @param bloomFilter is the bloom filter that will be used in deciding which
     * entries to keep.
     * @param terms is the array of terms whose results will be ANDed together.
     * @return the set of entries which satisfy the query.
     */
    private IndexEntry[] search(BloomFilter bloomFilter, IndexKeyString[] terms) {
        
        IndexKeyString localTerm = terms[0];
        
        IndexEntry[] finalResults;
        
        Vector results = new Vector();
        
        // our local entries for the local term should be a superset for
        // the end result, so let's start with that.
        Vector localResults = new Vector();
        localResults = this.pastService.lookup(localTerm.getNodeId()).getUpdates();
        
        // if a bloom filter was given to us.
        if(bloomFilter != null) {
            // remove any entries which for sure don't appear in the last terms list
            // (we don't know the term, but we have the bloom filter.
            Iterator i = localResults.iterator();
        
            while(i.hasNext()) {
         
               // remove the entry if the bloom filter doesn't contain it.   
                if(!bloomFilter.contains(((IndexEntry)i.next()).getURL())) {
                    i.remove();   
                }
            }
        }
        
        // if we don't have any results, there's no point in passing the
        // query along.
        if(localResults.size() == 0) {
            return new IndexEntry[0];    
        }
        
        // include these results in the final results
        results.add(localResults);
        
        // if there are more terms beyond ours, send the search on, 
        // incorporating the bloom filter for our search
        if(terms.length > 1) {
            IndexKeyString nextTerm = terms[1];
            
            // build the bloom filter off of the local results that
            // have already been pruned by the bloom filter that was
            // provided (this should be a smaller list).
            BloomFilter bf = this.buildBloomFilter(localResults); 
            
            // send everything except our term on to the next system
            IndexKeyString[] remainingTerms = new IndexKeyString[terms.length - 1];
            
            // copy the rest of the array to be sent
            for(int idx = 0; idx < remainingTerms.length; idx++) {
                remainingTerms[idx] = terms[idx + 1];   
            }
            
            // pass the search along to the next system
            results.addElement(this.recurSearch(nextTerm.getNodeId(), bf, remainingTerms));
        } 

        // process the results against what is actually stored for our term
        // NOTE: WE CANNOT CACHE THESE RESULTS.  They are built partially off of
        // a bloom filter which is not exact.  Therefore our set is not sharp.
        finalResults = this.intersect(new IndexKeyString(terms), results);       
        
        return finalResults;
    }

    /**
     * This method build a bloom filter for an array of objects.
     * 
     * @param objects is the set of objects which the bloom filter will be 
     * created to contain.
     * @return a bloom filter containing all the specified objects.
     */
    private BloomFilter buildBloomFilter(Vector objects) {
        
        // calculate the size
        int size = - (int) Math.round(objects.size() / BloomSearchServiceImpl.PERR_COEFF);
        
        System.out.println("Creating a bloom filter of size = " + size);
        
        BloomFilter bloomFilter = new DefaultBloomFilter(size);
        
        // fill the bloom filter
        Iterator i = objects.iterator();
        
        while(i.hasNext()) {
         
            bloomFilter.add(((IndexEntry)i.next()).getURL());   
        }
        
        return bloomFilter;
    }


    /**
     * This method contacts the host of the specified key and determines how
     * many entries it has for the specified key.
     * 
     * @param key is the key whose entries will be counted.
     * @return the number of entries the host of the specified key has for that
     * key.
     */
    private int getQueryResultSize(IndexKeyString key) {
        
        SizeLock sl = new SizeLock();        
        Integer ourWaitID = new Integer(BloomSearchServiceImpl.waitID++);
        
        this.waitHash.put(ourWaitID, sl);
        
        Message msg = new ReturnResultSizeMessage(this.getNodeId(), ourWaitID.intValue(), this.getAddress(), this.getCredentials(), key);
        this.routeMsg(key.getNodeId(), msg, this.getCredentials(), new SendOptions());
        
        return sl.getSize();
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

            for (int i = 0; i < v.size(); i++) {
                IndexKeyString[] theseKeys = (IndexKeyString[]) v.elementAt(i);

                IndexKeyString k = new IndexKeyString(theseKeys);
                if (this.pastService.exists(k.getNodeId()))
                    return theseKeys;
            }

            size--;
        }

        return new IndexKeyString[0];
    }
    
    /**
     * Performs an intersection over a Vector of Vectors of IndexEntries.
     *
     * @param key The overall key we are searching for.
     * @param vectors A Vector of Vectors of IndexEntries, representing
     *        each of the individual search term inverted indicies.
     * @return An array of IndexEntries representing the intersection.
     */
    public IndexEntry[] intersect(IndexKeyString key, Vector vectors) {
        if (vectors.size() == 0)
            return new IndexEntry[0];

        int smallestNum = ((Vector) vectors.elementAt(0)).size();
        int smallest = 0;

        Hashtable[] tables = new Hashtable[vectors.size()];

        // build up hashtables for each of the IndexEntry vectors, mapping
        // URL -> IndexEntry.
        // also, find the smallest list with which we will perform the
        // intersection
        for (int i = 0; i < vectors.size(); i++) {
            tables[i] = new Hashtable();

            Vector theseEntries = (Vector) vectors.elementAt(i);
            for (int j = 0; j < theseEntries.size(); j++) {
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
        for (int i = 0; i < smallestEntries.size(); i++) {
            IndexEntry thisEntry = (IndexEntry) smallestEntries.elementAt(i);
            URL url = thisEntry.getURL();
            double rank = 1;
            Date expiration = thisEntry.getExpiration();
            ObjectWeb.Security.Credentials cred = thisEntry.getCredentials();

            boolean inAll = true;

            // check all other lists
            for (int j = 0; inAll && (j < tables.length); j++) {
                IndexEntry thisTableEntry = (IndexEntry) tables[j].get(url);
                if (thisTableEntry == null) {
                    inAll = false;
                } else {
                    // the rank of the new IndexEntry will be the product of all
                    // of the others
                    rank = rank * thisTableEntry.getRank();

                    // the Date of the new IndexEntry will be the minimum of all
                    // of the others
                    if (thisTableEntry.getExpiration().compareTo(expiration)
                        < 0)
                        expiration = thisTableEntry.getExpiration();
                }
            }

            // build the new IndexEntry, if necessary
            if (inAll) {
                IndexEntry newEntry =
                    new IndexEntry(key, url, rank, expiration, cred);
                results.addElement(newEntry);
            }
        }

        IndexEntry[] arrayResults = new IndexEntry[results.size()];

        for (int i = 0; i < results.size(); i++) {
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
        if (!this.pastService.exists(key.getNodeId())) {
            this.pastService.insert(key.getNodeId(), new IndexPlaceholder(key), null);

            // TO DO: maintainance of index
            for (int i = 0; i < entry.length; i++) {
                this.pastService.update(key.getNodeId(), entry[i], null);
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

        for (int i = 0; i < entries.length; i++) {
            IndexEntry thisEntry = entries[i];
            IndexKeyString thisKey = thisEntry.getIndexKeyString();

            if (!this.pastService.exists(thisKey.getNodeId())) {
                this.pastService.insert(
                    thisKey.getNodeId(),
                    new IndexPlaceholder(thisKey),
                    null);
            }

            this.pastService.update(thisKey.getNodeId(), thisEntry, null);
        }
    }

    /**
     * @see rice.pastry.client.PastryAppl#getAddress()
     */
    public Address getAddress() {
        return this.address;
    }

    /**
     * @see rice.pastry.client.PastryAppl#getCredentials()
     */
    public Credentials getCredentials() {
        return this.credentials;
    }

    private void doRequestedSearch(Message msg) {
    
        SearchMessage sm = (SearchMessage) msg;
            
        // search
        IndexEntry[] arrayResults = this.search(sm.getBloomFilter(), sm.getTerms());  
        Vector results = new Vector();
            
        for(int i = 0; i < arrayResults.length; i++) {
            results.add(arrayResults[i]);   
        }
            
        SearchResultMessage srm = new SearchResultMessage(sm.getWaitID(), this.getAddress(), this.getCredentials(), results);
            
        // send the msg back
        this.routeMsg(sm.getSource(), srm, this.getCredentials(), new SendOptions());

        return;            
    }
    
    /**
     * @see rice.pastry.client.PastryAppl#messageForAppl(Message)
     */
    public void messageForAppl(Message msg) {
        
        // handle return values here
        if(msg instanceof ResultSizeResultMessage) {
            
            ResultSizeResultMessage rsrm = (ResultSizeResultMessage) msg;
            
            SizeLock sl = (SizeLock) this.waitHash.remove(new Integer(rsrm.getWaitID()));    
            
            sl.setSize(rsrm.getSize());
            
            return;
        }
        
        if(msg instanceof SearchResultMessage) {
            SearchResultMessage srm = (SearchResultMessage) msg;
            
            ResultLock rl = (ResultLock) this.waitHash.remove(new Integer(srm.getWaitID()));
            
            rl.setResult(srm.getResult());    
            
            return;
        }
        
        // handle request messages
        if(msg instanceof ReturnResultSizeMessage) {
            
            ReturnResultSizeMessage rrsm = (ReturnResultSizeMessage) msg;
            
            // get the size
            Vector localResults = new Vector();
            localResults = this.pastService.lookup(rrsm.getTerm().getNodeId()).getUpdates();
            
            ResultSizeResultMessage rsrm = new ResultSizeResultMessage(rrsm.getWaitID(), this.getAddress(), this.getCredentials(), localResults.size());
            
            // send the result back.
            this.routeMsg(rrsm.getSource(), rsrm, this.getCredentials(), new SendOptions());
            
            return;
        }
        
        if(msg instanceof SearchMessage) {

            Runnable request = new DoRequestedSearchRunnable(msg);
            
            new Thread(request).start();
            
            /*
            SearchMessage sm = (SearchMessage) msg;
            
            // search
            IndexEntry[] arrayResults = this.search(sm.getBloomFilter(), sm.getTerms());  
            Vector results = new Vector();
            
            for(int i = 0; i < arrayResults.length; i++) {
                results.add(arrayResults[i]);   
            }
            
            SearchResultMessage srm = new SearchResultMessage(sm.getWaitID(), this.getAddress(), this.getCredentials(), results);
            
            // send the msg back
            this.routeMsg(sm.getSource(), srm, this.getCredentials(), new SendOptions());
            */
            
            return;
        }
    }

    private class DoRequestedSearchRunnable implements Runnable {
        
        private Message msg;
        
        public DoRequestedSearchRunnable(Message msg) {
            this.msg = msg;
        }
        
        public void run() {
            BloomSearchServiceImpl.this.doRequestedSearch(this.msg);   
        }
    }
}

/**
 * This class is used to allow non-blocking calls for requests on
 * the size of the set of results for a search term.
 */
class SizeLock implements Serializable {
    
    private int size;
    private boolean sizeSet = false;
    
    public SizeLock() {}    
    
    public synchronized void setSize(int size) {
        this.size = size;
        this.sizeSet = true;
            
        this.notifyAll();
    }
    
    public synchronized int getSize() {
        // TODO: We should wait a specific amount of time here (not infinitely)
        if(this.sizeSet == false) {
            try {
                this.wait();   
            } catch(InterruptedException ie) {
                ie.printStackTrace();   
            }
        }
        
        return this.size;
    }
}

/**
 * This class is used to allow non-blocking calls for the actual set of
 * results associated with a specific search term.
 */
class ResultLock implements Serializable {
    
    private Vector result;
    private boolean resultSet = false;
    
    public ResultLock() {}    
    
    public synchronized void setResult(Vector result) {
        this.result = result;
        this.resultSet = true;
            
        this.notifyAll();
    }
    
    public synchronized Vector getResult() {
        // TODO: We should wait a specific amount of time here (not infinitely)
        if(this.resultSet == false) {
            try {
                this.wait();   
            } catch(InterruptedException ie) {
                ie.printStackTrace();   
            }
        }
        
        return this.result;
    }
}

/**
 * This message is sent to request that the number of results
 * associated with a specific search term be sent to a specific
 * system.
 */
class ReturnResultSizeMessage extends Message {

    private IndexKeyString term;
    private int waitID;
    private NodeId source;
    
    /**
     * The constructor
     */
    public ReturnResultSizeMessage(NodeId source, int waitID, Address dest, Credentials cred, IndexKeyString term) {
        super(dest, cred);
        
        this.source = source;
        this.term = term;   
        this.waitID = waitID;
    }    
    
    public NodeId getSource() {
        return this.source;    
    }
    
    public IndexKeyString getTerm() {
        return this.term;   
    }
    
    public int getWaitID() {
        return waitID;   
    }
}

/**
 * This message is sent in response to the ReturnResultSizeMessage
 * in order to return the number of results associated with a given
 * search term.
 */
class ResultSizeResultMessage extends Message {
    
    private int size;
    private int waitID;
    
    public ResultSizeResultMessage(int waitID, Address dest, Credentials cred, int size) {
        super(dest, cred);
        
        this.waitID = waitID;
        this.size = size;   
    }    
    
    public int getWaitID() {
        return this.waitID;   
    }
    
    public int getSize() {
        return this.size;   
    }
}

/**
 * This message requests that a remote system perform a search
 * using the set of search strings and the provided bloom filter.
 */
class SearchMessage extends Message {
    
    private BloomFilter bloomFilter;
    private IndexKeyString[] terms;
    private int waitID;
    private NodeId source;
    
    /**
     * The constructor.
     */
    public SearchMessage(NodeId source, int waitID, Address dest, Credentials cred, BloomFilter bf, IndexKeyString[] terms) {
        super(dest, cred);
        
        this.source = source;
        this.bloomFilter = bf;
        this.terms = terms;
        this.waitID = waitID;
    }   
    
    public NodeId getSource() {
        return this.source;    
    }
    
    /**
     * @return the bloom filter to use in pruning down the search space.
     */
    public BloomFilter getBloomFilter() {
        return this.bloomFilter;   
    }
    
    /**
     * @return the terms to search for.
     */
    public IndexKeyString[] getTerms() {
        return this.terms;   
    }
    
    public int getWaitID() {
        return this.waitID;   
    }
}

/**
 * This message is sent in response to a SearchMessage.  This returns 
 * the results of the search to the system.
 */
class SearchResultMessage extends Message {
 
    private int waitID;
    private Vector result;
    
    public SearchResultMessage(int waitID, Address dest, Credentials cred, Vector result) {
        super(dest, cred);
        
        this.waitID = waitID;
        this.result = result;       
    }   
    
    public Vector getResult() {
        return this.result;    
    }
    
    public int getWaitID() {
        return this.waitID;   
    }
}

/**
 * This class is the address used by pastry for routing messages between 
 * applications.
 */
class BloomSearchAddress implements Address {

    public int hashCode() {
        return 1143;    
    }
    
    /**
     * @return <code>true</code> whenever two objects are equal.  In the case
     * of this object, they are always equal if they are both instances of
     * this class, BloomSearchAddress.
     */
    public boolean equals(Object obj) {
        return (obj instanceof BloomSearchAddress);
    }
}