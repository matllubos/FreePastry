package rice.email.proxy.web.pages;

import java.io.*;
import rice.email.proxy.web.*;
import rice.email.proxy.user.*;
import rice.email.proxy.mailbox.*;

public class LoginPage extends WebPage {
  
  public boolean authenticationRequired() { return false; }
  
  public String getName() { return "/"; }
  
	public void execute(WebConnection conn, WebState state)	throws WebException, IOException {
    String error = null;
    
    String username = conn.getParameter("username");
    String password = conn.getParameter("password");
    
    try {
      if ((username != null) && (password != null)) {
        String real = state.getPassword(username);        
        
        if (real != null) {
          if (real.equals(password)) {
            state.setUser(state.getUser(username));
            
            conn.redirect("main");
            return;
          } else {
            error = "Username or password incorrect.";
          }
        } else {
          error = "Username or password incorrect.";
        }
      }
    } catch (UserException e) {
      error = e.getMessage();
    } catch (MailboxException e) {
      error = e.getMessage();
    }
    
    writeHeader(conn);
    conn.print("<table width=100% height=100% border=0 bgcolor=FFFFFF>");
    conn.print("  <tr height=50%><td colspan=3>&nbsp;</td></tr>");
    conn.print("  <form action=" + getName() + " method=get>");
    conn.print("  <tr><td width=50%>&nbsp;</td>");
    conn.print("    <td bgcolor=000000>");
    conn.print("      <table bgcolor=FFFFFF border=0>");
    conn.print("        <tr><td colspan=4 align=center><b>ePOST Webmail Login</b></td></tr>");
    conn.print("        <tr><td colspan=4>&nbsp;</td></tr>");
    conn.print("        <tr><td width=20>&nbsp;</td><td>Username</td><td><input type=test name='username'></td><td width=20></td></tr>");
    conn.print("        <tr><td></td><td>Password</td><td><input type=password name='password'></td><td></td></tr>");
    conn.print("        <tr><td colspan=2>&nbsp;</td><td><input type='submit'></td><td></td></tr>");
    
    if (error != null) {
      conn.print("        <tr><td colspan=4>&nbsp;</td></tr>");
      conn.print("        <tr><td colspan=4 align=center><font color=red>" + error + "</font></td></tr>");
    }
    
    conn.print("      </table>");
    conn.print("    </td>");
    conn.print("    <td width=50%>&nbsp;</td></tr>");
    conn.print("  <tr height=50%><td colspan=3>&nbsp;</td></tr>");
    conn.print("</table>");
    conn.print("  </form>");
    writeFooter(conn);
  }
  
}