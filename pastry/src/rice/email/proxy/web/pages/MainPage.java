package rice.email.proxy.web.pages;

import java.io.*;
import rice.email.proxy.web.*;
import rice.email.proxy.user.*;
import rice.email.proxy.mailbox.*;

public class MainPage extends WebPage {
  
  public boolean authenticationRequired() { return true; }
  
  public String getName() { return "main"; }
  
	public void execute(WebConnection conn, WebState state)	throws WebException, IOException {    
    writeHeader(conn);
    conn.print("<frameset name=main cols=20%,80% border=0>");
    conn.print("  <frame src=hierarchy name=hierarchy frameborder=0>");
    conn.print("  <frameset rows=30%,70% border=0>");
    conn.print("    <frame src=folder name=folder frameborder=0>");
    conn.print("    <frame src=message name=message frameborder=0>");
    conn.print("  </frameset>");
    conn.print("</frameset>");
    writeFooter(conn);
  }
  
}