package rice.email.test;

import java.net.InetAddress;

import rice.email.*;
import rice.email.proxy.*;

/**
 * Test class for dealing with proxies, ensuring that they work
 * and make the appropriate connections.
 */
public class ProxyTest {

    public static void main(String[] args) throws Exception {

	EmailTest et = new EmailTest();
	EmailService eserv = et.createEmailService();

	IMAPProxy imp = new
	    IMAPProxy(InetAddress.getByName("imap.owlnet.rice.edu"),
		      143);

	imp.attach(eserv, "dwp", args[0]);

	SMTPProxy smp = new
	    SMTPProxy(InetAddress.getByName("localhost"),
		      11235);

	smp.attach(eserv);

	while(true) {
	    Thread.sleep(5000);
	}
    }	

}
