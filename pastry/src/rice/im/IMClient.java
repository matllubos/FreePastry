package rice.im;

import java.util.*;
import java.io.*;

import rice.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.im.log.*;
import rice.im.messaging.*;
import rice.im.io.*;

/**
 * This class plays the client in a client-server relationship with {@link IMService}, which plays the server. <BR> IMService receives messages, implying changes of state 
 * and calls methods in this class which perform appropriate actions such as changing the interface, modifying data structures, etc.
 * 
 */

public class IMClient implements java.io.Serializable {

    public IMState state = new IMState(IMState.ONLINE);
    public IMService service;

    /**
     *Constructor
     */
    
    public IMClient(IMService service) {
	this.service = service;
	//InputEngine ie = new InputEngine();
	//ie.start();
    }

    public IMService getService() {
	return service;
    }
    /**
     * Called by IMService when the state of the application referenced by pua has changed.
     *@param state: the state (hopefully changed) that the client is now in.
     */
    public void stateChanged(IMNotificationMessage msg) throws Exception {
	IMState state = msg.getIMContent().getState();
	if (state.equals(new IMState(IMState.ENTER))) { 
	    System.out.println(msg.getIMContent().getSender() + " is now online ");
	    if (! service.online_contacts.contains(new String(msg.getIMContent().getSender().getName()))) {
		service.online_contacts.add(new String(msg.getIMContent().getSender().getName().substring(0, msg.getIMContent().getSender().getName().indexOf("@"))));
		IMGui.getIMGui().update(IMGui.getIMGui().getGraphics());
	    }
	else if (state.equals(new IMState(IMState.EXIT))) {
	    System.out.println( msg.getIMContent().getSender().getName() + " has just gone offline");
	    service.online_contacts.remove(new String(msg.getIMContent().getSender().getName().substring(0, msg.getIMContent().getSender().getName().indexOf("@"))));
	    IMGui.getIMGui().update(IMGui.getIMGui().getGraphics());
	}
	else {
	    System.out.println("error in statechanged()  in IMClient\n");
	    throw new Exception();
	}
	}
    }

     /**
     * Called by IMService when the state of the application referenced by pua has changed.
     *@param nm NotificationMessage that the IMService is passing onto this client 
     */
    public void messageReceived(NotificationMessage nm) {
	if (nm instanceof IMNotificationMessage) {
	    IMNotificationMessage inm = (IMNotificationMessage) nm;
	    IMTextMessage itm = (IMTextMessage) inm.getIMContent();
	    
	    String sender = inm.getIMContent().getSender().getName().substring(0, inm.getIMContent().getSender().getName().indexOf("@"));
	    IMGui.getIMGui().messageDisplay(sender, itm.getTextMessage());
	    System.out.println(sender + ": " + itm.getTextMessage());
	}
	
    }
    
    public IMState getState() throws Exception {
	if (state != null ) 
	    return state;
	else {
	    System.out.println("State in IMClient is null\n");
	    throw new Exception();
	}
    }
    
     /**
     * This method sets the state of the service.
     *
     * @param the {@link IMState} that the service should be set to.
     */ 
    
    public void setState(IMState state) {
	state.setState(state.getState());
    }

    
}

   //   protected class InputEngine extends Thread {
	


//  	public InputEngine() {
	   
//  	}

//  	public void run() {
//  	    System.out.println("----------------------------------------Welcome to the HELP menu---------------------------------------------");
//  	    System.out.println("1. Command Help");
//  	    System.out.println("2. Command Add Contact [name]");
//  	    System.out.println("3. Command Remove Contact [name]");
//  	    System.out.println("4. Command Get Contacts");
//  	    System.out.println("5. Command Get Online Contacts");
//  	    System.out.println("To chat, just type in your text message");
//  	    System.out.println("Type 'Quit' to exit");
	
//  	    readInput();
//  	}
	

//  	public void  readInput()  {
//  	    try {
//  		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//  		String str_main = "";
//  		while (! str_main.equals("Quit")) {
//  		    System.out.print(">");
//  		    str_main = br.readLine();
//  		    String str_temp = new String(str_main);
//  		    str_temp.trim();
		    
//  		    if (str_temp.equals("")) continue;
		    
//  		    if (str_temp.toLowerCase().equals("command help")) {
//  			System.out.println("----------------------------------------Welcome to the HELP menu---------------------------------------------");
//  			System.out.println("1. Command Help");
//  			System.out.println("2. Command Add Contact [name]");
//  			System.out.println("3. Command Remove Contact [name]");
//  			System.out.println("4. Command Get Contacts");
//  			System.out.println("5. Command Get Online Contacts");
//  			System.out.println("To chat, just type in your text message. The first word must be the name of the recipient");
//  			System.out.println("Type 'Quit' to exit");
//  		    }
		    
//  		    else if (str_temp.toLowerCase().startsWith("command add contact")) {
//  			String name = str_main.substring(19).trim();
//  			Continuation command = new Continuation() {
//  				public void receiveResult(Object o) {
//  				    String name = (String) o;
//  				    if (! name.equals("(null)"))
//  					System.out.println("Name " + name + " has been added ");
//  				    }

//  				public void receiveException(Exception e) {
//  				}
				
//  			    };
//  			service.addContact(name, command);
//  		    }
		    
//  		    else if (str_temp.toLowerCase().startsWith("command remove contact")) {
//  			String name = str_main.substring(22).trim();
//  			Continuation command = new Continuation() {
//  				public void receiveResult(Object o) {
//  				    String name = (String) o;
//  				    System.out.println("Name" + name + "has been removed");
//  				}				
//  				public void receiveException(Exception e) {
//  				}
				
//  			    };
//  			service.removeContact(name, command);
//  		    }
		    
		    
		    
//  		    else if (str_temp.toLowerCase().equals("command get contacts")) {
//  				    Vector vec = service.all_contacts;
//  				    System.out.println("Contacts are: ");
//  				    for (int i = 0; i < vec.size(); i++) {
//  					String s = (String) vec.get(i);
//  					System.out.println(s);
				  
//  				    }
//  		    }

//  		    else if (str_temp.toLowerCase().equals("command get online contacts")) {
//  			Vector vec = service.online_contacts;
//  				    System.out.println("Contacts are: ");
//  				    for (int i = 0; i < vec.size(); i++) {
//  					String s = (String) vec.get(i);
//  					System.out.println(s);
				  
//  				    }
//  		    }
		    
//  		    else if (str_temp.toLowerCase().equals("quit")) {
//  			service.sayGoodBye();
//  			System.exit(0);
//  		    }
		    
//  		    else {
//  			StringTokenizer st = new StringTokenizer(str_main.trim());
//  			String name = st.nextToken();
//  			String[] recip = new String[1];
//  			recip[0] = new String(name);
//  			IMTextMessage txt_msg = new IMTextMessage(service.pua, recip, str_main.substring(name.length()), state);
//  			service.sendMessage(txt_msg);
//  		    }
//  		}
		
//  	    }
	    
//  	    catch (Exception e) { e.printStackTrace(); }

//  	}

//      }

//  }



