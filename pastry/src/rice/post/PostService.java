package rice.post;

import rice.pastry.client.*;
import rice.pastry.*;

/**
 * This class is a multiplexing layer which allows 
 * multiple clients to use a single PostService. It also
 * handles Post requests such as notification, delivery, etc,
 * on behalf of other nodes in the network.
 */
public class PostService extends PastryAppl {

  /**
   * Builds a PostService to run on the given 
   * pastry node.
   *
   * @param node The Pastry node to run on.
   * @param pastcli The PAST client running on this Pastry node.
   * @param scribe The Scribe service running on this Pastry node.
   */
  public PostService(PastryNode node, PASTClient pastcli, IScribe scribe) {
  }
  
  /**
   * Gets the PAST client that we were assigned.
   */
  public PASTClient getPAST() {
  }
   
  /**
   * Gets the Scribe service assigned to this PostService.
   */
  public IScribe getScribe() {
  }

  /**
   * Registers a listener with this PostService.  
   *
   * @param listener The listener for this service.
   * @param name The unique name for the registering listener.
   */
  public void registerListener(PostServiceListener listener, String name) {
  }

  /**
   * Sends a message using Pastry.
   *
   * @param pm The message to send
   */
  public void sendMessage(PostMessage pm) throws PostException {
  }
}
