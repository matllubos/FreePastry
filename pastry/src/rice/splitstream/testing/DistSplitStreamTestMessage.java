package rice.splitstream.testing;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;
import rice.scribe.messaging.*;

import rice.splitstream.*;
import rice.splitstream.messaging.*;

import java.io.*;
import java.util.*;
import java.net.*;
/**
 *
 * @version $Id$ 
 *
 * @author Atul Singh
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package. 
 */


public class DistSplitStreamTestMessage extends Message implements Serializable
{

    private ChannelId m_channelId = null;
    /**
     * Constructor
     *
     * @param addr the address of the distSplitStreamTestApp receiver.
     * @param c the credentials associated with the mesasge.
     */
    public 
	DistSplitStreamTestMessage( Address addr, Credentials c, ChannelId channelId) {
	super( addr, c );
	m_channelId = channelId;
    }
    
    /**
     * This method is called whenever the pastry node receives a message for the
     * DistSplitStreamTestApp.
     * 
     * @param splitStreamApp the DistSplitStreamTestApp application.
     */
    public void handleDeliverMessage( DistSplitStreamTestApp splitStreamApp) {
       
	Channel channel = (Channel) splitStreamApp.m_channels.get(m_channelId);
	if(channel == null)
	    System.out.println("PROBLEM -- channel is null");
	//DistSplitStreamTest driver = splitStreamApp.getDriver();
	//if(driver == null)
	// System.out.println("PROBLEM -- driver is null --- should never happen");
	String bootHost = DistSplitStreamTest.bshost;//splitStreamApp.getDriver().getBootHost();
	String localHost = null;

	try{
	    localHost = InetAddress.getLocalHost().getHostName();
	}catch(UnknownHostException e){
	    System.out.println("Error determining local host: " + e);
	}
	
	System.out.println("Localhost "+localHost+", boothost "+bootHost);

	if(splitStreamApp.m_appIndex == 4 && localHost.equals(bootHost)){
	    // I am the creator of the channel
	    splitStreamApp.sendData(channel.getChannelId());
	}
    }
    

    public String toString() {
	return new String( "DIST_SCRIBE_REGR_TEST  MSG:" );
    }
}



