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
 * @author Dave Price
 */
class SMTPDaemon implements Runnable {

    InetAddress address;
    int port;
    EmailService service;

    /**
     * Constructor for this SMTPDaemon
     * @param address the address to bind to (currently unused)
     * @param port The port to bind to
     * @param service The EmailService to pass mail along to
     */
    public SMTPDaemon(InetAddress address, int port, EmailService service) {
	this.address = address;
	this.port = port;
	this.service = service;
    }

    /**
     * Comply with the Runnable interface. The SMTPDaemon does its
     * work in a separate thread.
     */
    public void run() {

	ServerSocket socket = null;
	try {
	    socket = new ServerSocket(port);
	} catch (IOException e) {
	    // do something sensible
	}

	System.out.println("SMTP daemon started.");
	    
	// Loop forever accepting connections.
	while(true) {
	    Socket sessionSocket = null;
	    try {
		sessionSocket = socket.accept();
		System.out.println("SMTP daemon saw a connection.");
		talkSMTP(sessionSocket);
	    } catch(Exception e) {
		System.err.println("SMTP daemon: error: " + e);
		e.printStackTrace();

		// If we haven't already, close the connection.
		if (sessionSocket != null) {
		    try {
			sessionSocket.close();
		    } catch (IOException ie) {
		    }
		}
	    }
	}

    }

    /**
     * Speak SMTP to the caller on the other end of the line.
     * @param s The Socket to talk on
     */
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
	    throw new IOException("Died after HELLO");
	}

	StringTokenizer tokizer;
	tokizer = new StringTokenizer(clientHello);
	tokizer.nextToken();
	String clientID = tokizer.nextToken();

	w.println("250 Hello " + clientID + ", pleased to meet you");

	String mailFrom = r.readLine();
	//	System.err.println("Saw: " + mailFrom);
	tokizer = new StringTokenizer(mailFrom);
	String token1 = tokizer.nextToken();
	String token2 = tokizer.nextToken(":");
	//System.err.println("{'"+token1+"', '"+token2+"}");
	if ( !(token1.equalsIgnoreCase("MAIL")) ||
	     !(token2.equalsIgnoreCase(" FROM")) ) {
	    // do something sensible
	    throw new IOException("Died trying to get MAIL FROM:");
	}
	
	String fromAddress = "";
	while (tokizer.hasMoreTokens()) {
	    fromAddress += tokizer.nextToken();
	}
	//System.err.println("Sending mail to '" + fromAddress +"'");
	
	// FIXME: Build this PostUserAddress from fromAddress
	PostUserAddress sender = new PostUserAddress(fromAddress);

	LinkedList recipients = new LinkedList();
	String subject = "No subject provided";

	boolean seenData = false;

	w.println("250 " + sender + "... Sender ok");
	//System.err.println("About to enter while loop.");

	while(!seenData) {
	    //System.err.println("About to read data from client");
	    String clientLine = r.readLine();
	    //System.err.println("Haven't seen data - saw: " + clientLine);
	    tokizer = new StringTokenizer(clientLine);
	    String mycmd = tokizer.nextToken();
	    if (mycmd.equalsIgnoreCase("DATA")) {
		seenData = true;
		continue;
	    }
	    else if (mycmd.equalsIgnoreCase("RCPT")) {
		tokizer.nextToken(":");
		String recip = "";
		while(tokizer.hasMoreTokens()) {
		    recip += tokizer.nextToken() + " ";
		}
		//System.err.println("Sending to user " + recip);

		PostUserAddress thisRecip = new PostUserAddress(recip);
		recipients.add(thisRecip);

		w.println("250 " + recip + "...Recipient ok");
		
	    }
	    else {
		// do something appropriate, error or so
		throw new IOException("Didn't get RCPT or DATA");
	    }
	}

	// Now we have a LinkedList of recipients, we want an array
	PostEntityAddress[] recipientArray =
	    (PostEntityAddress[]) recipients.toArray(new PostEntityAddress[0]);

	// We've seen DATA from the user, so now tell them it's okay
	// to deliver their message.
	w.println("354 Enter mail, end with '.' on a line by itself");
	StringBuffer messageBuf = new StringBuffer();
	String messageLine = r.readLine();
	while(!messageLine.equals(".")) {

	    //System.err.println("message line = " + messageLine);
	    messageBuf.append(messageLine);
	    messageBuf.append('\n');

	    tokizer = new StringTokenizer(messageLine);
	    if (tokizer.hasMoreTokens() && tokizer.nextToken().equals("Subject:")) {
		String mySubj = "";
		while(tokizer.hasMoreTokens()) {
		    mySubj += tokizer.nextToken() + " ";
		}
		subject = mySubj;
	    }
		    

	    messageLine = r.readLine();
	}

	System.err.println("Sending mail from: " + sender.getName());
	System.err.println("First recipient: " +
			   ((PostUserAddress) recipientArray[0]).getName());
	System.err.println("Subject: " + subject);
	System.err.println("Message data: \n" + messageBuf.toString());

	EmailData messageData = new EmailData(messageBuf.toString().getBytes());
	EmailData[] attachments = new EmailData[0];

	// Deliver the message.
	Email deliverMe = new Email(sender,
				    recipientArray,
				    subject,
				    messageData,  // body reference
				    attachments); // attach refs
	//null);

	// For right now, we don't care about getting notification
	try {
	    service.sendMessage(deliverMe, null);
	} catch (PostException e) {
	    System.err.println("POST exception delivering message: " +
			       e);
	}
	
	// Tell the user that we delivered the message
	w.println("250 Message accepted for delivery");
	System.err.println("SMTP daemon sent a message.");
	
	// Read the QUIT command
	r.readLine();
	w.println("221 closing connection, goodbye");
	s.close();

    }

}



