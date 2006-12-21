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
      for (int i=0; i<folders.length; i++) {
        conn.print("  <tr onClick=setURL('" + getName() + "?folder=" + folders[i].getFullName() + "')>");
        conn.print("<td>" + folders[i].getFullName() + "</td></tr>"); 
      }
      
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