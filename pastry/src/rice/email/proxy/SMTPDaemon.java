package rice.email.proxy;

import java.util.LinkedList;
import java.net.*;
import java.io.*;
import java.util.*;

import rice.post.*;
import rice.email.*;
import rice.post.PostException;

/**
 * Implementation of an SMTP daemon - designed to be forked off as a new
 * thread, it sits on the specified port and speaks SMTP to clients that
 * connect to it, then delivers messages on their behalf.
 */
class SMTPDaemon implements Runnable {

    InetAddress address;
    int port;
    EmailService service;

    public SMTPDaemon(InetAddress address, int port, EmailService service) {
	this.address = address;
	this.port = port;
	this.service = service;
    }

    public void run() {

	ServerSocket socket = null;
	try {
	    socket = new ServerSocket(port);
	} catch (IOException e) {
	    // do something sensible
	}
	    
	// Loop forever accepting connections.
	while(true) {
	    try {
		Socket sessionSocket = socket.accept();
		talkSMTP(sessionSocket);
	    } catch(Exception e) {
		// Do something sensible
	    }
	}

    }

    private void talkSMTP(Socket s) throws IOException {

	InputStream istream = s.getInputStream();
	OutputStream ostream = s.getOutputStream();

	// Use a BufferedReader for talking to the client, since
	// we do line-by-line communication
	BufferedReader r = new BufferedReader(new InputStreamReader(istream));
	PrintStream w = new PrintStream(ostream);
	
	// Do the SMTP protocol.
	w.println("220 ePost SMTP Services ready");
	
	String clientHello = r.readLine();
	if ( (clientHello.substring(0,5).equalsIgnoreCase("HELLO")) ||
	     (clientHello.substring(0,4).equalsIgnoreCase("HELO")) ||
	     (clientHello.substring(0,4).equalsIgnoreCase("EHLO")) ) {
	    // keep going
	}
	else {
	    // Do something sensible here here
	    s.close();
	    return;
	}

	StringTokenizer tokizer;
	tokizer = new StringTokenizer(clientHello);
	tokizer.nextToken();
	String clientID = tokizer.nextToken();

	w.println("250 Hello " + clientID + ", pleased to meet you");

	String mailFrom = r.readLine();
	tokizer = new StringTokenizer(mailFrom);
	if ( !(tokizer.nextToken().equalsIgnoreCase("MAIL")) ||
	     !(tokizer.nextToken().equalsIgnoreCase("FROM:")) ) {
	    // do something sensible
	    s.close();
	    return;
	}
	
	String fromAddress = "";
	while (tokizer.hasMoreTokens()) {
	    fromAddress += tokizer.nextToken();
	}
	
	// FIXME: Build this PostUserAddress from fromAddress
	PostUserAddress sender = new PostUserAddress(fromAddress);

	LinkedList recipients = new LinkedList();
	String subject = "No subject provided";

	boolean seenData = false;

	while(!seenData) {
	    String clientLine = r.readLine();
	    tokizer = new StringTokenizer(clientLine);
	    String mycmd = tokizer.nextToken();
	    if (mycmd.equalsIgnoreCase("DATA")) {
		seenData = true;
		continue;
	    }
	    else if (mycmd.equalsIgnoreCase("RCPT")) {
		tokizer.nextToken();
		String recip = "";
		while(tokizer.hasMoreTokens()) {
		    recip += tokizer.nextToken() + " ";
		}

		// FIXME: build this from recip
		PostUserAddress thisRecip = new PostUserAddress("recip");
		recipients.add(thisRecip);
		
	    }
	    else {
		// do something appropriate, error or so
		s.close();
		return;
	    }
	}

	// Now we have a LinkedList of recipients, we want an array
	PostUserAddress[] recipientArray =
	    (PostUserAddress[]) recipients.toArray(new PostUserAddress[0]);

	// Recipient group - FIXME do something with this
	PostGroupAddress[] recipientGroupArray = new PostGroupAddress[0];

	// We've seen DATA from the user, so now tell them it's okay
	// to deliver their message.
	w.println("354 Enter mail, end with '.' on a line by itself");
	StringBuffer messageBuf = new StringBuffer();
	String messageLine = r.readLine();
	while(!messageLine.equals(".")) {
	    messageBuf.append(messageLine);
	    messageBuf.append('\n');

	    tokizer = new StringTokenizer(messageLine);
	    if (tokizer.nextToken().equals("Subject:")) {
		String mySubj = "";
		while(tokizer.hasMoreTokens()) {
		    mySubj += tokizer.nextToken() + " ";
		}
		subject = mySubj;
	    }
		    

	    messageLine = r.readLine();
	}

	// FIXME: eventually, do MIME parsing here to break out attachments
	EmailData messageData = new EmailData(messageBuf.toString().getBytes());
	EmailData[] attachments = new EmailData[0];

	// Deliver the message.
	Email deliverMe = new Email(sender,
				    recipientArray,
				    recipientGroupArray,
				    subject,
				    messageData,  // body reference
				    attachments); // attach refs
	
	try {
	    service.sendMessage(deliverMe, messageData, attachments);
	} catch (PostException e) {
	    // do something sensible
	}

	// Tell the user that we delivered the message
	w.println("250 Message accepted for delivery");
	
	// Read the QUIT command
	r.readLine();
	w.println("221 closing connection, goodbye");
	s.close();

    }

}
