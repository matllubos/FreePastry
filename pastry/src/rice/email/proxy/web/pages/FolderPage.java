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

public class FolderPage extends WebPage {
  
  public static final int NUM_MESSAGES_PER_PAGE = 20;
  
  public static final SimpleDateFormat DATE = new SimpleDateFormat("MMM d");
  
  public boolean authenticationRequired() { return true; }
  
  public String getName() { return "/folder"; }
  
  public void execute(WebConnection conn, final WebState state)  throws WebException, IOException {    
    String uid = conn.getParameter("message");
    
    try {
      if (uid != null) {
        state.setCurrentMessageUID(Integer.parseInt(uid));
        
        List list = state.getCurrentFolder().getMessages(new MsgFilter() {
          public boolean includes(StoredMessage msg) {
            return msg.getUID() == state.getCurrentMessageUID();
          }
        });      
        
        if (list.size() > 0) {
          StoredMessage message = (StoredMessage) list.get(0);
          if (! message.getFlagList().isSeen()) {
            message.getFlagList().setSeen(true);
            state.getCurrentFolder().update(new StoredMessage[] {message});
          }
        }
        
        conn.redirect("main");
        return;
      }
      
      writeHeader(conn);
      if (state.getCurrentFolder() != null) {
        MailFolder folder = state.getCurrentFolder();
        final int exists = folder.getExists();

        int start = (exists > NUM_MESSAGES_PER_PAGE ? exists - NUM_MESSAGES_PER_PAGE : 1);
        int end = exists;
        
        List messages = folder.getMessages(new MsgFilter() {
          public boolean includes(StoredMessage msg) {
            return msg.getSequenceNumber() > exists - NUM_MESSAGES_PER_PAGE; 
          }
        });
        
        conn.print("<table bgcolor=\"#FFFFFF\" border=0 cellspacing=0 cellpadding=0 width=100%>");
        conn.print("<tr><td><b>" + folder.getFullName() + "</b><td><td align=right><b>");
        conn.print(start + "</b> - <b>" + end + "</b> of <b>" + exists + "</b></td></tr>");
        conn.print("</table><p>");
        
        conn.print("<table bgcolor=\"#FFFFFF\" border=0 cellspacing=0 cellpadding=0 width=100%>");
        
        for (int i=messages.size()-1; i>=0; i--) {
          conn.println("<tr><td colspan=3 height=1 bgcolor=\"#AAAAAA\"></td></tr>");
          StoredMessage message = (StoredMessage) messages.get(i);

          EmailMessagePart real = message.getMessage().getContent();
          ExternalContinuation d = new ExternalContinuation();
          real.getHeaders(d);
          d.sleep();
          if (d.exceptionThrown()) throw new MailboxException(d.getException());
          
          InternetHeaders headers = getHeaders((EmailData) d.getResult());
          
          String from = getHeader(headers, "From").replaceAll("\"", "");
          String subject = getHeader(headers, "Subject");
          Date date = new Date(message.getInternalDate());
                    
          boolean unseen = (! message.getFlagList().isSeen());
          boolean selected = (message.getUID() == state.getCurrentMessageUID());

          String color="EEEEFF";
          
          if (unseen)
            color="FFFFFF";
          
          conn.print("  <tr onClick=setURL('" + getName() + "?message=" + message.getUID() + "')>");
          conn.print("<td width=20% bgcolor=" + color + ">" + (unseen ? "<b>" : "") + from + (unseen ? "</b>": "") + "</td>");
          conn.print("<td width=70% bgcolor=" + color + ">" + (unseen ? "<b>": "") + subject + (unseen ? "</b>": "") + "</td>");
          conn.print("<td width=10% bgcolor=" + color + ">" + (unseen ? "<b>": "") + DATE.format(date) + (unseen ? "</b>": "") + "</td></tr>");
        }

        conn.println("<tr><td colspan=3 height=1 bgcolor=\"#AAAAAA\"></td></tr>");
        conn.print("</table>");
      } else {
        conn.print("<i>No folder is selected</i>");
      }
      writeFooter(conn);
    } catch (MailboxException e) {
      throw new WebException(conn.STATUS_ERROR, "An internal error has occured - '" + e + "'.");      
    }
  }
  
  protected static String getHeader(InternetHeaders headers, String header) throws MailboxException {
    String[] result = headers.getHeader(header);
    
    if ((result != null) && (result.length > 0)) {
      String text = result[0].replaceAll("\\n", "").replaceAll("\\r", "");
      return text;
    }
    
    return "<i>unknown</i>";
  }
  
  protected static InternetHeaders getHeaders(EmailData data) throws MailboxException {
    try {
      return new InternetHeaders(new ByteArrayInputStream(data.getData()));
    } catch (MessagingException e) {
      throw new MailboxException(e);
    }
  }
}