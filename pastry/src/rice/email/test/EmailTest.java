package rice.email.test;

import rice.pastry.standard.*;
import rice.pastry.*;
import rice.pastry.wire.*;
import rice.post.*;
import rice.post.security.*;
import rice.email.*;
import rice.past.*;
import rice.scribe.*;
import rice.storage.*;

import java.net.*;

import java.security.*;

/**
 * This class provides helper functions for all email-testing
 * code.
 */
public class EmailTest {

    private InetSocketAddress firstAddress = null;	
    private SecurityService securityService = new SecurityService(null, null);

    private class Announcer implements Runnable {
	
	private EmailService es;
	private int waitTime;

	public Announcer(EmailService es, int waitTime) {
	    this.es = es;
	    this.waitTime = waitTime;
	}

	public void run() {
	    
	    try {
		while(true) {
		    Thread.sleep(waitTime);
		    this.es.getPost().announcePresence();
		}
	    } catch(InterruptedException ie) {
		System.out.println("Announcer for " + this.es + " was halted.");
		return;
	    }
	}
    }

    protected EmailService[] createEmailServices(String[] usernames, int waitTime) {

	final int nodeNum = usernames.length;
	    
	PastryNode[] localNodes = new PastryNode[nodeNum];
	PostUserAddress[] addresses = new PostUserAddress[nodeNum];
	StorageManager[] sManagers = new StorageManager[nodeNum];
	Scribe[] scribes = new Scribe[nodeNum];
	PASTService[] pastServices = new PASTService[nodeNum];
	EmailService[] emailServices = new EmailService[nodeNum];
	    
	try {

	    RandomNodeIdFactory rnd = new RandomNodeIdFactory();

	    for(int i = 0; i < nodeNum; i++) {
		int port = 0;

		while(port < 1024) {
		    port = (int)(((double) 16000)*Math.random());
		}

		WirePastryNodeFactory idFactory;
		idFactory = new WirePastryNodeFactory(rnd, port);
		    
		if(this.firstAddress == null) {
		    localNodes[i] = idFactory.newNode(null);
		    this.firstAddress = ((WireNodeHandle)
					 localNodes[i].getLocalHandle()).getAddress();
		} else {
		    NodeHandle nodeHandle = idFactory.generateNodeHandle(this.firstAddress);
			 
		    localNodes[i] = idFactory.newNode(nodeHandle);
		}

		addresses[i] = new PostUserAddress(usernames[i]);

		sManagers[i] = new MemoryStorageManager();

		scribes[i] = new Scribe(localNodes[i], null);
		
		pastServices[i] = new PASTServiceImpl(localNodes[i],
						      sManagers[i]);
	    }

	    // now that we've cereated all those, create the post
	    // and email services
		
	    try {
		Thread.sleep(2000);
	    } catch(InterruptedException ie) {
		return null;
	    }

	    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
	    KeyPair caPair = kpg.generateKeyPair();

	    for(int i = 0; i < nodeNum; i++) {

		KeyPair pair = kpg.generateKeyPair();
		PostCertificate cert = this.securityService.generateCertificate(addresses[i], 
										pair.getPublic(), 
										caPair.getPrivate());

		Post post = new Post(localNodes[i],
				     pastServices[i],
				     scribes[i],
				     addresses[i], pair, cert,
				     caPair.getPublic());
		emailServices[i] = new EmailService(post);

		new Thread(new Announcer(emailServices[i], waitTime)).start();
	    }

	    return emailServices;
	} catch(PostException pe) {
	    pe.printStackTrace();
	    return null;
	} catch(NoSuchAlgorithmException nsae) {
	    nsae.printStackTrace();
	    return null;
	}
    }

    public static void main(String[] args) throws Exception {

	EmailTest et = new EmailTest();

	// Create multiple services here to test.
		
	Thread.sleep(5000);
    }
}
