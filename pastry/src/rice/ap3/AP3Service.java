package rice.ap3;

/**
 * @(#) AP3Service.java
 *
 * This interface is exported by AP3 for any applications or components
 * wishing to request anonymized content.<p>
 *
 * Such applications must implement the <code>AP3Client</code> interface,
 * and should call <code>getAnonymizedContent</code> to initiate a
 * request.  Note that AP3 itself has no knowledge of the protocol being
 * used, and relies upon the AP3Clients to handle fetching and caching.
 *
 * @version $Id$
 * @author Charles Reis
 * @author Gaurav Oberoi
 */
public interface AP3Service {

  /**
   * Called by an AP3Client to initiate a new request for content. It specifies
   * the fetch probability to be used by each intermediate hop in the network.
   * @param request Request object for content, as recognized by the AP3Client
   * @param fetchProbability For intermediate nodes to decide on whether to
   * forward or fetch.
   * @return Corresponding response object
   */
  Object getAnonymizedContent(Object request, double fetchProbability);

}
