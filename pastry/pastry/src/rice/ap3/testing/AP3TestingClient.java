package rice.ap3.testing;

import rice.ap3.*;

import rice.pastry.*;

/**
 * @(#) AP3TestingClient.java
 *
 * A very simple client used for testing.
 *
 * @version $Id$
 # @author Gaurav Oberoi
 */
public class AP3TestingClient implements AP3Client {

  private Object _cachedResponse = null;
  private Object _content = null;

  private AP3TestingService _ap3;

  public AP3TestingClient(PastryNode pn) {
    _ap3 = new AP3TestingService(pn, this);
    _content = "Content fetched from: " + _ap3.getNodeId();
  }

  public Object checkCache(Object request) {
    return getCachedResponse();
  }

  public void cacheResponse(Object response) {
    setCachedResponse(response);
  }

  public Object fetchContent(Object request) {
    return _content;
  }

  public AP3TestingService getService() {
    return _ap3;
  }

  public void setContent(Object content) {
    _content = content;
  }

  /** Cache methods */

  public Object getCachedResponse() {
    return _cachedResponse;
  }

  public void setCachedResponse(Object response) {
    _cachedResponse = response;
  }

  public boolean isCacheEmpty() {
    return _cachedResponse == null;
  }

  public void clearCache() {
    _cachedResponse = null;
  }

}
