package rice.ap3;

/**
 * @(#) AP3Client.java
 *
 * Implemented by any application or component which wishes to use AP3 to
 * anonymize content requests.<p>
 * The application needs to know how to fetch a requested object when it is
 * at the end of a request chain, and will have an opportunity to check its
 * cache on requests and cache responses regardless of its position in the
 * request chain.
 *
 * @version $Id$
 # @author Gaurav Oberoi
 */
public interface AP3Client {
  /**
   * Returns the cached response for the request object, or null
   * if the request has not been cached.  This method is called
   * on intermediate hops in the request chain.
   * @param request Request object from another originator
   * @return Corresponding cached response, or null
   */
  Object checkCache(Object request);

  /**
   * Allows the application to cache a response to a request,
   * if applicable.  This method is called on intermediate hops
   * in the response chain.
   * @param response Response object containing content
   */
  void cacheResponse(Object response);

  /**
   * Fetches the the requested content from the external network
   * resource and returns it.  This method is called on the last
   * hop of the request chain.
   * @param request Request object from another originator
   * @return Corresponding response object containing content
   */
  Object fetchContent(Object request);

}
