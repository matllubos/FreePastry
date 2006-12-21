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
package rice.email.proxy.imap.commands.fetch;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import java.io.*;
import java.text.*;
import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;


public class MessagePropertyPart extends FetchPart {
  
    List supportedParts = Arrays.asList(new Object[]  {
      "ALL", "FAST", "FULL", "BODY", "BODYSTRUCTURE", "ENVELOPE",
      "FLAGS", "INTERNALDATE", "UID"
    });

    public boolean canHandle(Object req) {
        return supportedParts.contains(req);
    }

    public String fetch(StoredMessage msg, Object part) throws MailboxException {
      return part + " " + fetchHandler(msg, part);
    }

    public String fetchHandler(StoredMessage msg, Object part) throws MailboxException {
      EmailMessagePart message = msg.getMessage().getContent();

      if ("ALL".equals(part)) {
        return fetch(msg, "FLAGS") + " " +
        fetch(msg, "INTERNALDATE") + " " +
        fetch(msg, "RFC822.SIZE") + " " +
        fetch(msg, "ENVELOPE");
      } else if ("FAST".equals(part)) {
        return fetch(msg, "FLAGS") + " " +
        fetch(msg, "INTERNALDATE") + " " +
        fetch(msg, "RFC822.SIZE");
      } else if ("FULL".equals(part)) {
        return fetch(msg, "FLAGS") + " " +
        fetch(msg, "INTERNALDATE") + " " +
        fetch(msg, "RFC822.SIZE") + " " +
        fetch(msg, "ENVELOPE") + " " +
        fetch(msg, "BODY");
      } else if ("BODY".equals(part)) {
        return fetchBodyStructure(message, false);
      } else if ("BODYSTRUCTURE".equals(part)) {
        return fetchBodyStructure(message, true);
      } else if ("ENVELOPE".equals(part)) {
        return fetchEnvelope(message);
      } else if ("FLAGS".equals(part)) {
        return fetchFlags(msg);
      } else if ("INTERNALDATE".equals(part)) {
        return fetchInternaldate(msg);
      } else if ("RFC822.SIZE".equals(part)) {
        return fetchSize(message);
      } else if ("UID".equals(part)) {
        return fetchUID(msg);
      } else {
        throw new MailboxException("Unknown part type specifier");
      }
    }

    String fetchBodyStructure(EmailContentPart part, boolean bodystructure) throws MailboxException {
      if (part instanceof EmailMultiPart)
        return fetchBodyStructure((EmailMultiPart) part, bodystructure);
      else if (part instanceof EmailHeadersPart)
        return fetchBodyStructure((EmailHeadersPart) part, bodystructure);
      else if (part instanceof EmailMessagePart)
        return fetchBodyStructure((EmailMessagePart) part, bodystructure);
      else
        throw new MailboxException("Unrecognized part " + part);
    }

    String fetchBodyStructure(final EmailMultiPart part, boolean bodystructure) throws MailboxException {
      EmailContentPart[] parts;
      try {
        parts = (EmailContentPart[])(new ExternalContinuationRunnable() {
          protected void execute(Continuation c) {
              part.getContent(c);
          }
        }).invoke(_conn.getEnvironment());
      } catch (Exception e) {
        throw new MailboxException(e);
      }

      StringBuffer result = new StringBuffer();
      result.append("(");

      for (int i=0; i<parts.length; i++) {
        result.append(fetchBodyStructure(parts[i], bodystructure));
      }

      if (part.getType().toLowerCase().indexOf("multipart/") >= 0) {
        String type = handleContentType(part.getType());

        type = type.substring(type.indexOf(" ") + 1);

        if (! bodystructure) {
          type = type.substring(0, type.indexOf(" ("));
        } else {
          type += " NIL NIL NIL";
        }

        result.append(" " + type);
      } else { 
        result.append(" \"MIXED\"");
      }

      return result.append(")").toString();
    }

    String fetchBodyStructure(final EmailHeadersPart part, boolean bodystructure) throws MailboxException {
      InternetHeaders headers;
      try {
        headers = getHeaders((EmailData)(new ExternalContinuationRunnable() {
          protected void execute(Continuation c) {
              part.getHeaders(c);
          }
        }).invoke(_conn.getEnvironment()));
      } catch (Exception e) {
        throw new MailboxException(e);
      }

      EmailContentPart content;
      try {
        content = (EmailContentPart)(new ExternalContinuationRunnable() {
          protected void execute(Continuation d) {
              part.getContent(d);
          }
        }).invoke(_conn.getEnvironment());
      } catch (Exception e) {
        throw new MailboxException(e);
      }
      
      if (content instanceof EmailMultiPart) 
        return fetchBodyStructure((EmailMultiPart) content, bodystructure);
      
      StringBuffer result = new StringBuffer();

      String contentHeader = getHeader(headers, "Content-Type", false);

      if (contentHeader == "NIL")
        contentHeader = "text/plain";
      
      String contentType = handleContentType(contentHeader);

      String encoding = getHeader(headers, "Content-Transfer-Encoding").toUpperCase();

      if (encoding.equals("NIL"))
        encoding = "\"7BIT\"";

      String id = getHeader(headers, "Content-ID");

      String description = getHeader(headers, "Content-Description");

      result.append("(" + contentType + " " + id + " " + description + " " + encoding + " " + content.getSize());

      if (contentType.indexOf("TEXT") >= 0) {
        result.append(" " + ((EmailSinglePart) content).getLines());
      }

      if (content instanceof EmailMessagePart) {
        EmailMessagePart message = (EmailMessagePart) content;
        result.append(" " + fetchEnvelope(message) + " " + fetchBodyStructure(message, bodystructure) + " 0");
      }

      if (bodystructure) {
        result.append(" " + getHeader(headers, "Content-MD5"));

        String disposition = getHeader(headers, "Content-Disposition", false);

        if (! disposition.equals("NIL"))
          result.append(" (" + handleContentType(disposition, false, false) + ") ");
        else
          result.append(" NIL ");

        result.append(getHeader(headers, "Content-Language") + " ");
        
        result.append(getHeader(headers, "Content-Location"));
      }
    
      return result.append(")").toString();      
    }

    String fetchBodyStructure(final EmailMessagePart part, boolean bodystructure) throws MailboxException {
      EmailContentPart data; 
      try {
        data = (EmailContentPart)(new ExternalContinuationRunnable() {
          protected void execute(Continuation c) {
              part.getContent(c);
          }
        }).invoke(_conn.getEnvironment());
      } catch (Exception e) {
        throw new MailboxException(e);
      }

      if (data instanceof EmailMultiPart) {
        return fetchBodyStructure((EmailMultiPart) data, bodystructure);
      } else {
        return fetchBodyStructure((EmailHeadersPart) part, bodystructure);
      }
    }

    private String handleContentType(String type) {
      return handleContentType(type, true);
    }

    private String handleContentType(String type, boolean includeSubType) {
      return handleContentType(type, includeSubType, true);
    }

    private String handleContentType(String type, boolean includeSubType, boolean insertDefaultChartype) {
      String[] props = type.split(";");

      String result = parseContentType(props[0], includeSubType) + " ";

      String propText = "";

      for (int i=1; i<props.length; i++) {
        String thisProp = parseBodyParameter(props[i]);

        if (! thisProp.equals("NIL"))
          propText += thisProp + " ";
      }

      if (propText.equals(""))
        if (insertDefaultChartype)
          result += "(\"CHARSET\" \"US-ASCII\")";
        else
          result += "NIL";
      else
        result += "(" + propText.trim() + ")";

      return result;
    }

    private String parseContentType(String type, boolean includeSubType) {
      if (type.matches("\".*\"")) {
        type = type.substring(1, type.length()-1);
      }
      
      String mainType = "\"" + type + "\"";
      String subType = "NIL";

      if (type.indexOf("/") != -1) {
        mainType = "\"" + type.substring(0,type.indexOf("/")) + "\"";

        if (type.indexOf(";") != -1) {
          subType = "\"" + type.substring(type.indexOf("/") + 1, type.indexOf(";")) + "\"";
        } else {
          subType = "\"" + type.substring(type.indexOf("/") + 1) + "\"";
        }
      }

      if (includeSubType)
        return mainType.toUpperCase() + " " + subType.toUpperCase();
      else
        return mainType.toUpperCase();
    }
    
    private String parseBodyParameter(String content) {
      content = content.trim();
      String result = "NIL";

      if (content.indexOf("=") >= 0) {
        String name = content.substring(0, content.indexOf("=")).toUpperCase();
        String value = content.substring(content.indexOf("=") + 1);

        if (value.matches("\".*\"")) {
          value = value.substring(1, value.length()-1);
        }

        result = "\"" + name.toUpperCase() + "\" \"" + value + "\"";
      } 

      return result;
    }

    private int countLines(String string) {
      int len = (string.split("\n")).length;

      if (! string.endsWith("\n")) {
        len--;
      }

      return len;
    }

    String fetchSize(EmailMessagePart message) throws MailboxException {
      return "" + (message.getSize() + internalFetchSize(message));
    }

    int internalFetchSize(EmailContentPart content) throws MailboxException {
      if (content instanceof EmailHeadersPart) {
        return 2 + internalFetchSize(((EmailHeadersPart) content).content); 
      } else if (content instanceof EmailMultiPart) {
        EmailMultiPart multi = (EmailMultiPart) content;
        
        int result = ((multi.type.length() + 6) * (multi.content.length + 1));
        
        for (int i=0; i<multi.content.length; i++)
          result += internalFetchSize(multi.content[i]);

        return result;
      } else {
        return 0;
      }
    }

    String fetchUID(StoredMessage msg) throws MailboxException {
      return "" + msg.getUID();
    }

    String fetchID(StoredMessage msg) throws MailboxException {
      return "\"" + msg.getSequenceNumber() + "\"";
    }

    String fetchFlags(StoredMessage msg) throws MailboxException {
      return msg.getFlagList().toFlagString();
    }

    String fetchInternaldate(final StoredMessage msg) throws MailboxException {
      if (msg.getInternalDate() != 0)
        return "\"" + rice.email.proxy.mail.MimeMessage.dateWriter.format(new Date(msg.getInternalDate())) + "\"";
      
      InternetHeaders headers;
      try {
        headers = getHeaders((EmailData)(new ExternalContinuationRunnable() {
          protected void execute(Continuation d) throws MailboxException {
              msg.getMessage().getContent().getHeaders(d);
          }
        }).invoke(_conn.getEnvironment()));
      } catch (Exception e) {
        throw new MailboxException(e);
      }

      return getHeader(headers, "Date");
    }

    private String addresses(InternetAddress[] addresses) {
      if ((addresses != null) && (addresses.length > 0)) {
        String result = "(";

        for (int i=0; i<addresses.length; i++) {
          result += address(addresses[i]);
        }

        return result + ")";
      } else {
        return "NIL";
      }
    }

    private String address(InternetAddress address) {
      String personal = address.getPersonal();

      if (personal == null)
        personal = "NIL";
      else
        personal = "\"" + personal.replaceAll("\"", "'") + "\"";

      String emailAddress = address.getAddress().replaceAll("\"", "'");
      String user = "NIL";
      String server = "NIL";

      if (emailAddress != null) {
        if (emailAddress.indexOf("@") >= 0) {
          user = "\"" + emailAddress.substring(0, address.getAddress().indexOf("@")) + "\"";
          server = "\"" + emailAddress.substring(address.getAddress().indexOf("@") + 1) + "\"";
        } else {
          user = "\"" + emailAddress + "\"";
        }
      }

      return "(" + personal + " NIL " + user + " " + server + ")";
    }

    private String collapse(String[] addresses) {
      if ((addresses == null) || (addresses.length == 0)) return "";
      if (addresses.length == 1) return addresses[0];
      
      String result = addresses[0];

      for (int i=1; i<addresses.length; i++) {
        result += ", " + addresses[i];
      }

      return result;
    }

    private InternetHeaders getHeaders(EmailData data) throws MailboxException {
      try {
        return new InternetHeaders(new ByteArrayInputStream(data.getData()));
      } catch (MessagingException e) {
        throw new MailboxException(e);
      }
    }
    
    private String getHeader(InternetHeaders headers, String header) throws MailboxException {
      return getHeader(headers, header, true);
    }
    
    private String getHeader(InternetHeaders headers, String header, boolean format) throws MailboxException {
      String[] result = headers.getHeader(header);

      if ((result != null) && (result.length > 0)) {
        String text = result[0].replaceAll("\\n", "").replaceAll("\\r", "").replaceAll("[\\u0080-\\uffff]", "?");

        if (! format)
          return text;

        text = text.replaceAll("\"", "'");
        
        if (text.indexOf("\"") == -1)
          return "\"" + text + "\"";
        else
          return "{" + text.length() + "}\r\n" + text;
      }

      return "NIL";
    }
    
    public String fetchEnvelope(final EmailHeadersPart part) throws MailboxException {
      try {
        InternetHeaders headers;
        try {
          headers = getHeaders((EmailData)(new ExternalContinuationRunnable() {
            protected void execute(Continuation d) throws MailboxException {
              part.getHeaders(d);
            }
          }).invoke(_conn.getEnvironment()));
        } catch (Exception e) {
          throw new MailboxException(e);
        }

        StringBuffer result = new StringBuffer();

		InternetAddress[] from = null;
		InternetAddress[] sender = null;
        InternetAddress[] replyTo = null;
		InternetAddress[] to = null;
        InternetAddress[] cc = null;
        InternetAddress[] bcc = null;

        InternetAddress[] fallback = new InternetAddress[1];
		fallback[0] = new InternetAddress("malformed@cs.rice.edu","MalformedAddress");

        //from, sender, reply-to, to, cc, bcc, in-reply-to, and message-id.
		try{
			from = InternetAddress.parse(collapse(headers.getHeader("From")));
		} catch (AddressException ae) { from = fallback;}
		try{
			sender = InternetAddress.parse(collapse(headers.getHeader("Sender")));
		} catch (AddressException ae) { sender = fallback;}
		try{
		    replyTo = InternetAddress.parse(collapse(headers.getHeader("Reply-To")));
		} catch (AddressException ae) { replyTo = fallback; }
		try{
		    to = InternetAddress.parse(collapse(headers.getHeader("To")));
		} catch (AddressException ae) { to = fallback;}
		try{
		    cc = InternetAddress.parse(collapse(headers.getHeader("Cc")));
		} catch (AddressException ae) { cc = fallback; }
		try{
		     bcc = InternetAddress.parse(collapse(headers.getHeader("Bcc")));
		} catch (AddressException ae) { bcc = fallback;}
		
       
        if (addresses(sender).equals("NIL"))
          sender = from;

        if (addresses(replyTo).equals("NIL"))
          replyTo = from;

        result.append("(" + getHeader(headers, "Date") + " ");
        result.append(getHeader(headers, "Subject") + " ");
        result.append(addresses(from) + " ");
        result.append(addresses(sender) + " ");
        result.append(addresses(replyTo) + " ");
        result.append(addresses(to) + " ");
        result.append(addresses(cc) + " ");
        result.append(addresses(bcc) + " ");
        result.append(getHeader(headers, "In-Reply-To") + " ");
        result.append(getHeader(headers, "Message-ID") + ")");

        return result.toString();
      } catch (Exception ae) {
        throw new MailboxException(ae);
      }
    }
}
