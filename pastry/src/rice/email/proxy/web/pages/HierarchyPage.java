package rice.email.proxy.web.pages;

import java.io.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.web.*;
import rice.email.proxy.user.*;

public class HierarchyPage extends WebPage {
  
  public boolean authenticationRequired() { return true; }
  
  public String getName() { return "/hierarchy"; }
  
	public void execute(WebConnection conn, WebState state)	throws WebException, IOException {    
    String error = null;
    String folder = conn.getParameter("folder");
    
    try {
      if (folder != null) {
        state.setCurrentFolder(folder);
        
        conn.redirect("main");
        return;
      }
    } catch (UserException e) {
      error = e.getMessage();
    } catch (MailboxException e) {
      error = e.getMessage();
    }
    
    try {
      writeHeader(conn);
      MailFolder[] folders = state.getUser().getMailbox().listFolders("*");
      
      conn.print("<table border=0>");
      for (int i=0; i<folders.length; i++) 
        conn.print("  <tr><td><a target=_top href='" + getName() + "?folder=" + folders[i].getFullName() + "'>" + folders[i].getFullName() + "</td></tr>");
      
      conn.print("</table>");
      
      if (error != null) 
        conn.print("<p><font color=red>" + error + "</font>");
      
      writeFooter(conn);
    } catch (UserException e) {
      throw new WebException(conn.STATUS_ERROR, "An internal error has occured - '" + e + "'.");
    } catch (MailboxException e) {
      throw new WebException(conn.STATUS_ERROR, "An internal error has occured - '" + e + "'.");      
    }
  }
  
}