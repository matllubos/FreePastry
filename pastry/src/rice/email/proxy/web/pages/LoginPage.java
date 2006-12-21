/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
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