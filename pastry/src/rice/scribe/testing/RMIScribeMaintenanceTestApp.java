package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.direct.*;
import rice.pastry.leafset.*;

import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

import java.util.*;
import java.security.*;
import java.io.*;

/**
 * @(#) RMIScribeMaintenanceTestApp.java
 *
 * an application used by the  maintenance test suite for Scribe
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi 
 */

public class RMIScribeMaintenanceTestApp implements IScribeApp
{
    protected PastryNode m_pastryNode ;
    public Scribe m_scribe;
    public Vector listOfTopics;
    public NodeId topicId;
    public int m_appIndex;
    public static int m_appCount = 0;
    public int m_tolerance = 3;
    /**
     * The hashtable maintaining mapping from topicId to log object
     * maintained by this application for that topic.
     */
    protected Hashtable m_logTable = null;

    /**
     * The SendOptions object to be used for all messaging through Pastry
     */
    protected SendOptions m_sendOptions = null;

    /**
     * The SendOptions object to be used for all messaging through Pastry
     */
    protected static Credentials m_credentials = null;


    /**
     * The receiver address for the scribe system.
     */
    protected static Address m_address = new RMIScribeControlAddress();

    private File m_rmiLog;

    private String  m_rmiLogName ;

    private int m_rmiLogCounter ;

    private static class RMIScribeControlAddress implements Address {
	private int myCode = 0x9ab4a3c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof RMIScribeControlAddress);
	}
    }


    private static class RMITopicLog{
	protected Vector m_entries = null;

	public RMITopicLog(){
	    m_entries = new Vector();
	}
	
	public Vector getLogEntries(){
	    return (Vector)m_entries.clone();
	}
	
	public void insertLogEntry(Integer seqno){
	    m_entries.addElement(seqno);
	    return;
	}

	public void clearLogEntries(){
	    m_entries.clear();
	}

    }
    

    public RMIScribeMaintenanceTestApp( PastryNode pn,  Scribe scribe, Credentials cred ) {
	m_scribe = scribe;
	m_credentials = cred;
	m_pastryNode = pn;
	listOfTopics = new Vector();
	m_sendOptions = new SendOptions();
	m_credentials = new PermissiveCredentials();
	m_logTable = new Hashtable();
	m_appIndex = m_appCount ++;
	m_rmiLogName = new String("logs/" + m_appIndex);
    }

    public Scribe getScribe() {
	return m_scribe;
    }

    /**
     * up-call invoked by scribe when a publish message is 'delivered'.
     */
    public void receiveMessage( ScribeMessage msg ) {
	NodeId topicId;
	RMITopicLog log;
	
	
	topicId = (NodeId) msg.getTopicId();
	log = (RMITopicLog)m_logTable.get(topicId);
	if( log == null){
	    log = new RMITopicLog();
	    m_logTable.put(topicId, log);
	}
	log.insertLogEntry((Integer)msg.getData());
	try {
	    BufferedWriter out = new BufferedWriter(new FileWriter(m_rmiLogName, true));
	    m_rmiLogCounter = (int)System.currentTimeMillis();
	    out.write( m_rmiLogCounter + " " + (Integer)msg.getData()+ "\n");
	    out.close();
	}
	catch (IOException e) {
	}
	
    }

    public void processLog(NodeId topicId){
	RMITopicLog log;
	Vector logEntries;

	System.out.println("Printing Log for Node "+m_scribe.getNodeId());
	
	log = (RMITopicLog)m_logTable.get(topicId);
	if(log != null){
	    logEntries = (Vector)log.getLogEntries();
	    for(int i = 0; i < logEntries.size(); i++){
		System.out.println(((Integer)logEntries.elementAt(i)).intValue());
	    }
	}
	
    }


    /**
     * up-call invoked by scribe when a publish message is forwarded through
     * the multicast tree.
     */
    public void forwardHandler( ScribeMessage msg ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " forwarding: "+ msg);
	*/
    }
    
    /**
     * up-call invoked by scribe when a node detects a failure from its parent.
     */
    public void faultHandler( ScribeMessage msg, NodeHandle parent ) {
	/*
	  System.out.println("Node:" + getNodeId() + " App:"
	  + m_app + " handling fault: " + msg);
	*/
    }
    
    /**
     * up-call invoked by scribe when a node is added to the multicast tree.
     */
    public void subscribeHandler( ScribeMessage msg ) {
	/*
	  System.out.println("Node:" + getNodeId() + " App:"
	  + m_app + " child subscribed: " + msg);
	*/
    }
    /*
    public NodeId getNodeId() {
	return m_scribe.getNodeId();
    }
    */
    /**
     * direct call to scribe for creating a topic from the current node.
     */
    public void create( NodeId topicId ) {
	m_scribe.create( topicId, m_credentials);
    }
    
    /**
     * direct call to scribe for publishing to a topic from the current node.
     */    
    public void publish( NodeId topicId, Object data ) {
	m_scribe.publish( topicId, data, m_credentials );
    }
    
    /**
     * direct call to scribe for subscribing to a topic from the current node.
     */    
    public void subscribe( NodeId topicId ) {
	m_scribe.subscribe( topicId, this, m_credentials );
    }
    
    /**
     * direct call to scribe for unsubscribing a  topic from the current node
     * The topic is chosen randomly if null is passed and topics exist.
     */    
    public void unsubscribe(NodeId topicId) {
	m_scribe.unsubscribe( topicId, this, m_credentials );
    }
    
    /**
     * Returns the credentials of this client.
     *
     * @return the credentials.
     */
    public Credentials getCredentials() { return m_credentials; }
    
    /**
     * Returns the address of this client. 
     *
     * @return the address.
     */
    public Address getAddress() { return m_address; }

}







