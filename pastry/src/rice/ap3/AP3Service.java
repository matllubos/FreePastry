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
   * Called by an AP3Client to initiate a new request for content.
   * This method blocks the incoming thread until a response message is received.
   *
   * @param request Request object for content, as recognized by the AP3Client
   * @param fetchProbability The probability used by intermediate nodes to
   * determine whether to fetch or forward a request.
   * @param timeout Number of milliseconds to wait for a response before
   * declaring a failed request.
   * @return Corresponding response object
   */
  public Object getAnonymizedContent(Object request,
                                     double fetchProbability,
                                     long timeout);

}
