package rice.email.test;

import java.net.InetAddress;
import java.io.*;
import java.util.*;

import rice.email.*;
import rice.email.messaging.*;
import rice.email.proxy.*;

/**
 * Test class for dealing with proxies, ensuring that they work
 * and make the appropriate connections.
 */

class EmailReceiver implements Observer {
    public void update(Observable src, Object args) {
	System.out.println("SRC: " + src + ", ARG: " + args);
	
	if(args instanceof EmailNotificationMessage) {
	    System.out.println("Email received");
	} else {
	    System.out.println("Not an email");
	}
    }
}

public class ProxyTest {

    public static void main(String[] args) throws Exception {

	String server;
	String uname;
	    

	if (args.length < 2) {
	    System.out.println("Please specify a server and username");
	    System.exit(-1);
	}
	server = args[0];
	uname = args[1];

	EmailTest et = new EmailTest();
	String[] nameset = new String[2];
	nameset[0] = "<sender@sender.org>";
	nameset[1] = "<recipient@recipient.org>";
	EmailService[] eservset = et.createEmailServices(nameset, 2000);
	EmailService sender = eservset[0];
	EmailService receiver = eservset[1];

	Thread.sleep(5000);
	
	IMAPProxy imp = new
	    IMAPProxy(InetAddress.getByName("imap.owlnet.rice.edu"),
		      143);

	BufferedReader r = new BufferedReader(new
	    FileReader("password"));
	String pass = r.readLine();

	imp.attach(receiver, "dwp", pass);
 
	System.err.println("IMAP proxy started.");

	
	
	SMTPProxy smp = new
	    SMTPProxy(InetAddress.getByName("localhost"),
		      11235);

	smp.attach(sender);

	System.err.println("SMTP proxy started.");

	while(true) {
	    Thread.sleep(5000);
	}
    }	

}


