package rice.ap3;

import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;

import rice.ap3.routing.*;
import rice.ap3.messaging.*;

import java.util.Hashtable;
import java.util.Random;

/**
 * @(#) AP3ServiceImpl.java
 *
 * An AP3Service implementation
 *
 * @version $Id$
 # @author Gaurav Oberoi
 */
public class AP3ServiceImpl 
  extends PastryAppl
  implements AP3Service {

  /**
   * The credentials for the AP3 system.
   */
  protected Credentials _credentials;

  /**
   * Used for routing a message through Pastry.
   * Specifies options, which are currently
   * the default routing options.
   */
  protected SendOptions _sendOptions;

  /**
   * The routing table.
   */
  protected AP3RoutingTable _routingTable;

  /**
   * The table used to store information for blocked threads
   * waiting for a response.
   */
  protected Hashtable _threadTable;

  /**
   * Used to determine whether to fetch requested
   * content or not.
   */
  protected Random _random;

  /**
   * The AP3Client using the system.
   */
  protected AP3Client _client;

  /**
   * Used for generating random node ids
   * when forwarding requests.
   */
  protected RandomNodeIdFactory _randomNodeIDFactory;

  /**
   * Constructor
   */
  public AP3ServiceImpl(PastryNode pn, AP3Client client) {
    super(pn);
    this._client = client;
    this._credentials = new PermissiveCredentials();
    this._sendOptions = new SendOptions();
    this._routingTable = new AP3RoutingTableImpl();
    this._randomNodeIDFactory = new RandomNodeIdFactory();
    this._threadTable = new Hashtable();
    this._random = new Random();
  }

  /**
   * Called by an AP3Client to initiate a new request for content.
   * This method blocks the incoming thread until a response message is received.
   *
   * @param request Request object for content, as recognized by the AP3Client
   * @param fetchProbability The probability used by intermediate nodes to
   * determine whether to fetch or forward a request.
   * @return Corresponding response object
   */
  public Object getAnonymizedContent(Object request, double fetchProbability) {

    boolean messageIDCollided = true;
    AP3Message requestMsg = null;

    requestMsg = _createAP3Message(this.getNodeId(),
				   request,
				   AP3MessageType.REQUEST,
				   fetchProbability);

    /*
    while(messageIDCollided) {
      try {
	requestMsg = _createAP3Message(this.getNodeId(),
				       request,
				       AP3MessageType.REQUEST,
				       fetchProbability);
	_routingTable.addEntry(requestMsg);
	messageIDCollided = false;
      } catch(Exception e) {
	messageIDCollided = true;
      }
    }

    _threadTable.put(requestMsg.getID(),
		     new ThreadTableEntry(Thread.currentThread(), null));
    */

    this._routeMsg(_generateRandomNodeID(), requestMsg);

    /* In the future, change wait() to be wait(timeout) 
     * so that the thread can wake after a timeout period to handle the
     * timeout. 

    while(((ThreadTableEntry) _threadTable.get(requestMsg.getID()))._msg ==
	  null) {
      System.out.println("waiting...");
      try {
	//wait();
	Thread.currentThread().wait();
      } catch (Exception e) {
	System.out.println("\nawoken\n");
	// If the thread is interrupted, continue.
      }
    }
    */

    /* Thread is here because it has been notified by a thread
     * that deposited the response message in the thread table.
     * So get that response msg, extract the object and return it.

    AP3Message responseMsg = 
      ((ThreadTableEntry) _threadTable.get(requestMsg.getID()))._msg;

    return responseMsg.getContent();
    */

    System.out.println("\nDEBUG -------- Leaving getAnonymizedContent()");
    return null;
  }

  /**
   * Returns the address of this application.
   *
   * @return the address.
   */
  public Address getAddress() {
    return AP3Address.instance();
  }
  
  /**
   * Returns the credentials of this application.
   *
   * @return the credentials.
   */
  public Credentials getCredentials() {
    return this._credentials;
  }
  
  /**
   * Called by pastry when a message arrives for AP3.
   * The message could be a response or a request.
   *
   * <p>
   * If the message is a response, AP3
   * will either route it back towards the originiator
   * or, if its for this node, it will store the message
   * for the blocked request thread to pick up. It will
   * then unblock the appropriate blocked thread.
   *
   * <p>
   * If the message is a request, AP3 will either
   * forward the request to a randomly chosen node
   * or fetch the request based on the fetch probability given
   * in the message.
   *   
   * @param msg the message that is arriving.
   */
  public void messageForAppl(Message msg) {
    AP3Message ap3Msg = (AP3Message) msg;
    
    if(ap3Msg.getType() == AP3MessageType.REQUEST) {
      this._handleRequest(ap3Msg);
    } else if (ap3Msg.getType() == AP3MessageType.RESPONSE) {
      this._handleResponse(ap3Msg);
    } else {

      /* Should never be here */
      throw new IllegalArgumentException("Message type is neither request nor response");
    }
  }

  /**
   * Handles response messages.
   */
  protected void _handleResponse(AP3Message msg) {

    AP3RoutingTableEntry routeInfo = _routingTable.getEntry(msg.getID());
    _routingTable.dropEntry(msg.getID());

    if(routeInfo == null) {
      System.out.println("\n\nDEBUG -------- Received an unknown response in node " +
			 this.getNodeId() + "\n\n");
      /* We know nothing about this response message, so drop it */
      return;
    } else {
      if(routeInfo.getSource().equals(this.getNodeId())) {
	/* Response belongs to this node so deposit response message in the
	 * thread table and wake up the appropriate thread.
	 */
	ThreadTableEntry threadInfo = 
	  (ThreadTableEntry) _threadTable.get(routeInfo.getID());
	
	if(threadInfo == null) {
	  /* This is an error. We sent out the request, made a note of it
	   * in our routing table, but have no thread to re-awaken to collect
	   * the response. Should not happen.
	   */
	  throw new IllegalStateException
	    ("No sleeping thread found to give response to");
	}
	
	System.out.println("\n\nDEBUG -------- Received my response in node " +
			   this.getNodeId() + "\n\n");
	threadInfo._msg = msg;
	threadInfo._thread.notify();
      } else {
	/* Route response back towards originator after 
	 * letting the client cache it 
	 */
	_client.cacheResponse(msg.getContent());
	this._routeMsg(routeInfo.getSource(), msg);
      }
    }
  }

  /**
   * Handles request messages.
   */
  protected void _handleRequest(AP3Message msg) {
    
    AP3RoutingTableEntry routeInfo = _routingTable.getEntry(msg.getID());
    if(routeInfo != null) {
      /* This is a message id collision, drop the request */
      System.out.println("\ncollision.node =" + this.getNodeId() + 
			 "\ncollision.messageID =" + msg.getID() + "\n");
      return;
    }
    
    AP3Message responseMsg = null;
    Object content = null;

    content = _client.checkCache(msg.getContent());
    if(content != null) {
      /* We're an intermediate node that found the requested content
       * in our cache. Let's return it.
       */
      _sendResponse(msg.getSource(), content);
    } else if(_shouldFetch(msg.getFetchProbability())) {
      /* According to the fetch probability set in the message
       * and our random coin toss, we're supposed to fetch 
       * the content. 
       */
      content = _client.fetchContent(msg.getContent());
      _sendResponse(msg.getSource(), content);
    } else {
      /* We're supposed to forward the request to a randomly chosen
       * node. Make a mark of it in the routing table.
       */
      try {
	_routingTable.addEntry(msg);
	this._routeMsg(_generateRandomNodeID(), msg);
      } catch (Exception e) {
	/* A message id collision occurred, drop the request */
      }
    }
  }

  /**
   * Helper function used to create an AP3Message. Useful when
   * subclassing this class, such as for testing.
   */
  protected AP3Message _createAP3Message(NodeId source,
					 Object content,
					 int messageType,
					 double fetchProbability) {
    return new AP3Message(source, content, messageType, fetchProbability);
  }

  /**
   * Helper function used to route messages in the AP3 system.
   */
  protected void _routeMsg(NodeId dest, AP3Message msg) {
    this.routeMsg(dest, msg, _credentials, _sendOptions);
  }

  /**
   * Helper function to determine if this node should fetch
   * the requested content or not.
   */
  protected boolean _shouldFetch(double prob) {
    return (_random.nextDouble() < prob);
  }

  /**
   * Helper function used to generate random node ids.
   */
  protected NodeId _generateRandomNodeID() {
    return _randomNodeIDFactory.generateNodeId();
  }

  /**
   * Helper function used to return response messages
   */
  protected void _sendResponse(NodeId dest, Object responseContent) {
    AP3Message responseMsg = _createAP3Message(null,
					       responseContent,
					       AP3MessageType.RESPONSE,
					       -1.0);
    this._routeMsg(dest, responseMsg);
  }

  /**
   * Helper class used to store information on blocked threads
   * waiting for a response.
   */
  protected class ThreadTableEntry {
    /**
     * The blocked thread itself.
     */
    protected Thread _thread;

    /**
     * The resposne message for the blocked thread
     * to pick up when its woken up.
     */
    protected AP3Message _msg;

    /**
     * Constructor.
     */
    ThreadTableEntry(Thread thread, AP3Message msg) {
      this._thread = thread;
      this._msg = msg;
    }
  }
}








