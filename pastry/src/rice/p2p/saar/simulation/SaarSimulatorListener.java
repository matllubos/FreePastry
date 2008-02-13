package rice.p2p.saar.simulation;

import rice.p2p.saar.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Vector;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.pastry.direct.*;


public class SaarSimulatorListener implements SimulatorListener {


    public Environment env;

    public SaarSimulatorListener(Environment env) {
	this.env = env;

    }

    public void messageSent(Message msg, NodeHandle from, NodeHandle to, int delay) {
	/*
	long currtime = env.getTimeSource().currentTimeMillis();

	if(msg instanceof rice.pastry.routing.RouteMessage) {
	    rice.pastry.routing.RouteMessage rmsg = (rice.pastry.routing.RouteMessage) msg;
	    try {
		System.out.println("messageSent2(" + currtime + "," + from + "," + to + "," + delay + "," + rmsg.getMessage().getClass().getName());
	    }
	    catch(Exception e) {
		System.out.println("messageSent3(" + currtime + "," + from + "," + to + "," + delay + "," + rmsg);
	    }
	    
	} else {
	    System.out.println("messageSent4(" + currtime + "," + from + "," + to + "," + delay + "," + msg.getClass().getName());
	}
	*/
	
    }

    

    public void messageReceived(Message msg, NodeHandle from, NodeHandle to) {
	/*
	long currtime = env.getTimeSource().currentTimeMillis();

	if(msg instanceof rice.pastry.routing.RouteMessage) {
	    rice.pastry.routing.RouteMessage rmsg = (rice.pastry.routing.RouteMessage) msg;
	    try {
		System.out.println("messageReceived2(" + currtime + "," + from + "," + to + "," + rmsg.getMessage().getClass().getName());
		
	    } catch(Exception e) {
		System.out.println("messageReceived3(" + currtime + "," + from + "," + to + "," + rmsg);
	    }
	    
	} else {
	    System.out.println("messageReceived4(" + currtime + "," + from + "," + to + "," + msg.getClass().getName());
	}
	*/
    }
    


}