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

  private AP3TestingService _ap3;

  public AP3TestingClient(PastryNode pn) {
    _ap3 = new AP3TestingService(pn, this);
  }

  public Object checkCache(Object request) {
    return null;
  }

  public void cacheResponse(Object response) {
  }

  public Object fetchContent(Object request) {
    return null;
  }

  public AP3TestingService getService() {
    return _ap3;
  }

}
