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
package rice.email.proxy.imap.commands.search;

import java.io.ByteArrayInputStream;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;

import rice.Continuation.ExternalContinuation;
import rice.email.EmailData;
import rice.email.EmailMessagePart;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.MailboxException;


public class StringArgSearchPart extends SearchPart {

  String argument;
  
  public boolean includes(StoredMessage msg) {
    if (getType().equals("BCC")) {
      return handleHeader(msg, "Bcc");
    } else if (getType().equals("BODY")) {
      return handleBody(msg);
    } else if (getType().equals("CC")) {
      return handleHeader(msg, "Cc");
    } else if (getType().equals("FROM")) {
      return handleHeader(msg, "From");
    } else if (getType().equals("KEYWORD")) {
      return handleFlag(msg, true);
    } else if (getType().equals("SUBJECT")) {
      return handleHeader(msg, "Subject");
    } else if (getType().equals("TEXT")) {
      return handleBody(msg);
    } else if (getType().equals("TO")) {
      return handleHeader(msg, "To");
    } else if (getType().equals("UNKEYWORD")) {
      return handleFlag(msg, false);
    } else {
      return false;
    }
  }

  protected boolean handleFlag(StoredMessage msg, boolean set) {
    String flags = msg.getFlagList().toFlagString().toLowerCase();

    if (set) {
      return (flags.indexOf(getArgument().toLowerCase()) >= 0);
    } else {
      return (flags.indexOf(getArgument().toLowerCase()) < 0);
    }
  }

  protected boolean handleBody(StoredMessage msg) {
    return false;
  }

  protected boolean handleHeader(StoredMessage msg, String header) {
    try {
      final EmailMessagePart message = msg.getMessage().getContent();
      
      ExternalContinuation c = new ExternalContinuation();
      message.getHeaders(c);
      c.sleep();

      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }

      InternetHeaders headers = new InternetHeaders(new ByteArrayInputStream(((EmailData) c.getResult()).getData()));

      Enumeration e = headers.getMatchingHeaderLines(new String[] {header});
      
      while (e.hasMoreElements()) {
        String line = (String) e.nextElement();

        if (line.indexOf(getArgument()) >= 0) return true;
      }

      return false;
    } catch (MailboxException e) {
      System.out.println("Exception " + e + " was thrown in StringArgSearchPart.");
      return false;
    } catch (MessagingException e) {
      System.out.println("Exception " + e + " was thrown in StringArgSearchPart.");
      return false;
    }
  }

  public void setArgument(String argument) {
    this.argument = argument;
  }

  public String getArgument() {
    return argument;
  }
}