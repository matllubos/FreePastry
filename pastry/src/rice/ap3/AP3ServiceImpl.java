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
 * @author Gaurav Oberoi
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
   * Called by getAnonymizedContent.
   * This method blocks the incoming thread until a response message is received.
   *
   * @param request Request object for content, as recognized by the AP3Client
   * @param fetchProbability The probability used by intermediate nodes to
   * determine whether to fetch or forward a request.
   * @param timeout Number of milliseconds to wait for a response before
   * declaring a failed request.
   * @param destination The handle of the node to directly send the request to
   * @return Corresponding response object
   */
  protected Object getAfterCallback(Object request,
				    double fetchProbability,
				    long timeout,
				    NodeHandle destination) {

      boolean messageIDCollided = true;
      AP3Message requestMsg = null;

    while(messageIDCollided) {
      try {
	requestMsg = _createAP3Message(this.getNodeHandle(),
	 			       request,
		 		       AP3MessageType.REQUEST,
			 	       fetchProbability);
	_routingTable.addEntry(requestMsg);
	messageIDCollided = false;
      } catch(Exception e) {
	messageIDCollided = true;
      }
    }


    /* Update the thread table so that this thread can collect its
     * response when it arrives.
     */
    ThreadTableEntry entry = new ThreadTableEntry();
    _threadTable.put(requestMsg.getID(), entry);

    /* Try to directly send the request to the node randomly chosen
     * by getAnonymizedContent
     */
    try 
	{
	    this._routeMsgDirect( destination, requestMsg );
	}
    catch ( java.lang.Exception e )
	{
	    //target is no longer available; send to another node
	    _threadTable.remove( requestMsg.getID() );
	    _routingTable.dropEntry(requestMsg.getID());
	    return getAnonymizedContent( request, fetchProbability, timeout );
	}

    /* Wait till a response is received.
     *
     * Needs to be changed so that it works properly by 
     * subclassing Thread and giving us something that can
     * be suspended and resumed.
     */
    if(entry._msg == null) {
      try {
	synchronized (entry._waitObject) {
	  entry._waitObject.wait(timeout);
	}
      } catch (InterruptedException e) {
      }
    }

    /* Remove the thread from the thread table.
     */
    _threadTable.remove(requestMsg.getID());

    /* Thread is here because it has been notified by a thread
     * that deposited the response message in the thread table.
     * Or because it has timed out.
     */
    AP3Message responseMsg = entry._msg;

    /* If it has timed out, return null and remove request from routing table.
     * Need to resend request in the future.
     */
    if(responseMsg == null) {
      _routingTable.dropEntry(requestMsg.getID());
      return null;
    } else {
      return responseMsg.getContent();
    }
  }


  /**
   * Called to request content anonymously. This method selects a random node to
   * send the content request to and waits for a response from it. It then calls
   * getAfterCallback to send the actual request directly to the chosen node.
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
				     long timeout) {

    boolean callbackIDCollided = true;
    AP3Message callbackMsg = null;

    while ( callbackIDCollided )
	{
	    try
		{
		    callbackMsg = _createAP3Message( this.getNodeHandle(),
                                                     null,
						     //this.getNodeHandle(),
						     AP3MessageType.CALLBACK,
						     -1 );
		    _routingTable.addEntry( callbackMsg );
		    callbackIDCollided = false;
		}
	    catch ( Exception e )
		{
		    callbackIDCollided = true;
		}
	}

     /* Update the thread table so that this thread can collect its
     * response when it arrives.
     */
    ThreadTableEntry entry = new ThreadTableEntry();
    _threadTable.put( callbackMsg.getID(), entry );

    /* Route the callback message to the randomly chosen node
     */
    System.out.println( " BLAH BLAH BLAH");
    NodeId blah = _generateRandomNodeID();
    System.out.println( "WE HAVE A NODE_ID: " + blah ); 
    this._routeMsg( blah, callbackMsg );

    /* Wait till a response is received.
     *
     * Needs to be changed so that it works properly by 
     * subclassing Thread and giving us something that can
     * be suspended and resumed.
     */
    if(entry._msg == null) {
      try {
	synchronized (entry._waitObject) {
	  entry._waitObject.wait(timeout);
	}
      } catch (InterruptedException e) {
      }
    }

    /* Remove the thread from the thread table.
     */
    _threadTable.remove(callbackMsg.getID());

    /* Thread is here because it has been notified by a thread
     * that deposited the response message in the thread table.
     * Or because it has timed out.
     */
    AP3Message responseMsg = entry._msg;

    /* If it has timed out, return null and remove request from routing table.
     * Need to resend request in the future.
     */
    if(responseMsg == null) {
      _routingTable.dropEntry(callbackMsg.getID());
      return null;
    } else {
      return getAfterCallback( request, 
			       fetchProbability, 
			       timeout, 
			       responseMsg.getSource() );
    }
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
   * The message could be a response, a request, or
   * a callback.
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
   * <p>
   * If the message is a callback, AP3 will route a response 
   * containing the ID of this node to the originating node.
   *   
   * @param msg the message that is arriving.
   */
  public void messageForAppl(Message msg) {
    AP3Message ap3Msg = (AP3Message) msg;
    
    if(ap3Msg.getType() == AP3MessageType.REQUEST) {
      this._handleRequest(ap3Msg);
    } else if (ap3Msg.getType() == AP3MessageType.RESPONSE) {
      this._handleResponse(ap3Msg);
    } 
    else if ( ap3Msg.getType() == AP3MessageType.CALLBACK )
	{
	    this._handleCallback( ap3Msg );
	}
    else {
      /* Should never be here */
      throw new IllegalArgumentException("Message type is not request, response, or callback");
    }
  }

  /**
   * Handles response messages.
   */
  protected synchronized void _handleResponse(AP3Message msg) {
    AP3RoutingTableEntry routeInfo = _routingTable.getEntry(msg.getID());
    _routingTable.dropEntry(msg.getID());

    if(routeInfo == null) {
      /* We know nothing about this response message, so drop it */
      return;
    } else {
      if(routeInfo.getSource().equals(this.getNodeHandle())) {
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
	
	/* This will cause the thread awaiting this message
	 * to wake up and return the content to the user.
	 */
	threadInfo._msg = msg;
	synchronized (threadInfo._waitObject) {
	  threadInfo._waitObject.notify();
	}
      } else {
	/* Route response back towards originator after 
	 * letting the client cache it 
	 */
	_client.cacheResponse(msg.getContent());
        try
        {
	  this._routeMsgDirect(routeInfo.getSource(), msg);
        }
        catch (java.lang.Exception e)
        {
          //do nothing; intermediate node has dropped out
        }
      }
    }
  }

  /**
   * Handles request messages.
   */
  protected synchronized void _handleRequest(AP3Message msg) {
    
    AP3RoutingTableEntry routeInfo = _routingTable.getEntry(msg.getID());
    Object content = null;

    if(routeInfo != null) {
      /* This is a message id collision, drop the request */
      return;
    }
    
    content = _client.checkCache(msg.getContent());
    if(content != null) {
      /* We're an intermediate node that found the requested content
       * in our cache. Let's return it.
       */
      _sendResponseDirect(msg.getSource(), msg.getID(), content);
    } else if(_shouldFetch(msg.getFetchProbability())) {
      /* According to the fetch probability set in the message
       * and our random dice toss, we're supposed to fetch 
       * the content. 
       */
      content = _client.fetchContent(msg.getContent());
      _client.cacheResponse(content);
      _sendResponseDirect(msg.getSource(), msg.getID(), content);
    } else {
      /* We're supposed to forward the request to a randomly chosen
       * node. Make a mark of it in the routing table and update
       * the message to reflect the new source, which is the current node.
       */
      try {
	_routingTable.addEntry(msg);
	msg.setSource(this.getNodeHandle());

        //send out the callback
        boolean callbackIDCollided = true;
        AP3Message callbackMsg = null;

        while ( callbackIDCollided )
        {
            try
                {
                    callbackMsg = _createAP3Message( this.getNodeHandle(),
						     null,
                                                     //this.getNodeHandle(),
                                                     AP3MessageType.CALLBACK,
                                                     -1 );
                    _routingTable.addEntry( callbackMsg );
                    callbackIDCollided = false;
                }
            catch ( Exception e )
                {
                    callbackIDCollided = true;
                }
        }

        /* Update the thread table so that this thread can collect its
         * response when it arrives.
         */
        ThreadTableEntry entry = new ThreadTableEntry();
        _threadTable.put( callbackMsg.getID(), entry );

        /* Route the callback message to the randomly chosen node
         */
        this._routeMsg( _generateRandomNodeID(), callbackMsg );

        /* Wait till a response is received.
         *
         * Needs to be changed so that it works properly by
         * subclassing Thread and giving us something that can
         * be suspended and resumed.
         */
        if(entry._msg == null) {
          try {
            synchronized (entry._waitObject) {
              entry._waitObject.wait(5000);     //waiting an arbitrary length of time
            }
          } catch (InterruptedException e) {
          }
      }

      /* Remove the thread from the thread table.
       */
      _threadTable.remove(callbackMsg.getID());

      /* Thread is here because it has been notified by a thread
       * that deposited the response message in the thread table.
       * Or because it has timed out.
       */
      AP3Message responseMsg = entry._msg;

      /* If it has timed out, remove request from routing table.
       * Message chain will time out.
       */
      if(responseMsg == null) {
        _routingTable.dropEntry(callbackMsg.getID());
      } else {
        try
        {
          this._routeMsgDirect( responseMsg.getSource(), msg );
        }
        catch ( java.lang.Exception e )
        {
          //found node dropped out; let request die
        }
      }
      } catch (Exception e) {
	/* A message id collision occurred, drop the request */
	return;
      }
    }
  }

    /*
     * Handles callback messages
     */
    protected synchronized void _handleCallback( AP3Message msg )
    {
	AP3RoutingTableEntry routeInfo = _routingTable.getEntry(msg.getID());
	Object content = null;
	
	if( routeInfo != null ) 
	    {
		/* This is a message id collision, drop the request */
		return;
	    }
	//content = this.getNodeHandle();
	_sendResponseDirect( msg.getSource(), msg.getID(), content );
    }

  /**
   * Helper function used to create an AP3Message. Useful when
   * subclassing this class, such as for testing.
   */
  protected AP3Message _createAP3Message(NodeHandle source,
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
     * Helper function used to route a message directly to the target node
     */
    protected void _routeMsgDirect( NodeHandle dest, AP3Message msg ) throws java.lang.Exception
    {
	boolean found = this.routeMsgDirect( dest, msg, _credentials, _sendOptions );

	if ( !found )
	    {
		throw new java.lang.Exception( "Error routing directly: target node no longer exists" );
	    }
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
    System.out.println( "We are indeed seeing the procedure" );
    NodeId q = _randomNodeIDFactory.generateNodeId();
    System.out.println( "The returned value from the factory is: " + q );
    return q;
  }

  /**
   * Helper function used to return response messages
   */
  protected void _sendResponse(NodeId dest, AP3MessageID id, Object responseContent) {
    AP3Message responseMsg = _createAP3Message(null,
					       responseContent,
					       AP3MessageType.RESPONSE,
					       -1.0);
    responseMsg.setID(id);
    this._routeMsg(dest, responseMsg);
  }

  /**
   * Helper function used to directly return response messages
   */
  protected void _sendResponseDirect( NodeHandle dest, AP3MessageID id, Object responseContent )
  {
    AP3Message responseMsg = _createAP3Message( null, 
						responseContent, 
						AP3MessageType.RESPONSE, 
						-1.0 );
    responseMsg.setID( id );
    try
    {
      this._routeMsgDirect( dest, responseMsg );
    }
    catch ( java.lang.Exception e )
    {
      //Node from which the request originated no longer exists; nothing to do
    }
  }

  /**
   * Helper class used to store information on blocked threads
   * waiting for a response.
   */
  protected class ThreadTableEntry {
    /**
     * The blocked thread itself.
     */
    protected Object _waitObject;

    /**
     * The resposne message for the blocked thread
     * to pick up when its woken up.
     */
    protected AP3Message _msg;

    /**
     * Constructor.
     */
    ThreadTableEntry() {
      this._waitObject = new Object();
      this._msg = null;
    }
  }
}
















