package rice.email.test;

import rice.pastry.standard.*;
import rice.pastry.*;
import rice.pastry.wire.*;
import rice.post.*;
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

	protected EmailService createEmailService(String username) {

		try {

		RandomNodeIdFactory rnd = new RandomNodeIdFactory();
		int port = (int)(((double) 16000)*Math.random());

		WirePastryNodeFactory idFactory = new WirePastryNodeFactory(rnd, port);
		PastryNode localNode = null;

		if(this.firstAddress == null) {
			localNode = idFactory.newNode(null);
			this.firstAddress = ((WireNodeHandle) localNode.getLocalHandle()).getAddress();
		} else {
			NodeHandle nodeHandle = idFactory.generateNodeHandle(this.firstAddress);
			 
			localNode = idFactory.newNode(nodeHandle);
		}

		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		KeyPair caPair = kpg.generateKeyPair();
		KeyPair pair = kpg.generateKeyPair();

		PostUserAddress address = new PostUserAddress(username);

		NodeId nodeId = localNode.getNodeId();

		StorageManager storage = new MemoryStorageManager();

		Scribe scribe = new Scribe(localNode, null);
		PASTService past = new PASTServiceImpl(localNode, storage);
		Post post = new Post(localNode, past, scribe, address, pair, null, caPair.getPublic());
		EmailService email = new EmailService(post);

		return email;
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

		for(int i = 0; i < 10; i++) {
			System.out.println("Creating " + i);
			et.createEmailService("user" + i);
		}

		Thread.sleep(5000);
	}
}
