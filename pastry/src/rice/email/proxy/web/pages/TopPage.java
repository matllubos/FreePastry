package rice.email.proxy.web.pages;

import java.io.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.web.*;
import rice.email.proxy.user.*;

public class TopPage extends WebPage {
  
  public boolean authenticationRequired() { return true; }
  
  public String getName() { return "/top"; }
  
	public void execute(WebConnection conn, WebState state)	throws WebException, IOException {    
    writeHeader(conn);
    conn.println("<h3>ePOST Webmail for " + state.getUser().getName() + "<h3>");
    writeFooter(conn);
  }
  
}