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
   * @param request Request object for content, as recognized by the AP3Client
   * @return Corresponding response object
   */
  Object getAnonymizedContent(Object request);

  /**
   * Gets the fetch probability used on requested messages.
   * @return Probability between 0 and 0.5
   */
  public double getFetchProbability();

  /**
   * Sets the fetch probability to be used on requested messages.
   * Must be less than one half, for anonymity purposes.
   * @param prob Probability between 0 and 0.5
   */
  public void setFetchProbability(double prob);

}
