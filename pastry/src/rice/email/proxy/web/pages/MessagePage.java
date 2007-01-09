/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.email.proxy.web.pages;

import rice.email.*;
import rice.*;
import rice.Continuation.*;

import javax.mail.*;
import javax.mail.internet.*;

import java.io.*;
import java.text.*;
import java.util.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.web.*;

public class MessagePage extends WebPage {
  
  public static final SimpleDateFormat DATE = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
  
  public boolean authenticationRequired() { return true; }
  
  public String getName() { return "/hierarchy"; }
  
	public void execute(WebConnection conn, final WebState state)	throws WebException, IOException {    
    try {
      writeHeader(conn);
      
      if (state.getCurrentMessageUID() < 0) {
        conn.print("<i>No message is selected</i>");
      } else { 

        List list = state.getCurrentFolder().getMessages(new MsgFilter() {
          public boolean includes(StoredMessage msg) {
            return msg.getUID() == state.getCurrentMessageUID();
          }
        });
        
        if (list.size() == 0) {
          conn.print("<i>That message no longer available.</i>");
        } else {
          StoredMessage message = (StoredMessage) list.get(0);
          
          EmailMessagePart real = message.getMessage().getContent();
          ExternalContinuation d = new ExternalContinuation();
          real.getHeaders(d);
          d.sleep();
          if (d.exceptionThrown()) throw new MailboxException(d.getException());

          InternetHeaders headers = FolderPage.getHeaders((EmailData) d.getResult());
          
          ExternalContinuation e = new ExternalContinuation();
          real.getContent(e);
          e.sleep();
          if (e.exceptionThrown()) throw new MailboxException(e.getException());
          
          EmailContentPart part = (EmailContentPart) e.getResult();
          String body = null;
          
          if (part instanceof EmailSinglePart) {
            ExternalContinuation f = new ExternalContinuation();
            part.getContent(f);
            f.sleep();
            if (f.exceptionThrown()) throw new MailboxException(f.getException());

            EmailData data = (EmailData) f.getResult();
            body = new String(data.getData()).replaceAll("\r", "").replaceAll("\n", "<br>");
          }
          
          String from = FolderPage.getHeader(headers, "From");
          String to = FolderPage.getHeader(headers, "To");
          String subject = FolderPage.getHeader(headers, "Subject");
          Date date = new Date(message.getInternalDate());
          
          conn.print("<table>");
          conn.print("<tr><td><b>From:</b></td><td>" + from + "</td></tr>");
          conn.print("<tr><td><b>To:</b></td><td>" + to + "</td></tr>");
          conn.print("<tr><td><b>Subject:</b></td><td>" + subject + "</td></tr>");
          conn.print("<tr><td><b>Date:</b></td><td>" + DATE.format(date) + "</td></tr>");
          conn.print("</table><p>");
          conn.print(body);
        }
      } 
      
      writeFooter(conn);
    } catch (MailboxException e) {
      throw new WebException(conn.STATUS_ERROR, "An internal error has occured - '" + e + "'.");      
    }    
  }
}
