package rice.email.proxy.test.mailbox.postbox;

import javax.mail.*;
import javax.mail.internet.*;

import java.io.*;

import rice.email.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.mailbox.postbox.*;
import rice.email.proxy.util.*;
import junit.framework.TestCase;

/**
* This test is temporarly unusable due to
 * interface changes.
 */
public class PostMessageTest extends TestCase {
  
  public PostMessageTest(String s) {
    super(s);
  }

  public void testSimpleMessageParsing() {
    try {
      Email email = PostMessage.parseEmail(new StringBufferResource(TEST_MESSAGE));
    } catch (MailboxException e) {
      e.printStackTrace();
      assertTrue("Exception " + e + " thrown. ", false);
    }
  }

  public void testAdvancedMessageParsing() {
    try {
      Email email = PostMessage.parseEmail(new StringBufferResource(LARGE_TEST_MESSAGE));

      assertTrue("Top level should be multipart", email.content.content instanceof EmailMultiPart);

      EmailMultiPart multi = (EmailMultiPart) email.content.content;
      assertTrue("Top level multipart should have 2 subparts", multi.content.length == 2);
      assertTrue("First top-level subpart should be a single part", multi.content[0].content instanceof EmailSinglePart);
      assertTrue("Second top-level subpart should be a message part", multi.content[1].content instanceof EmailMessagePart);

      EmailMessagePart message = (EmailMessagePart) multi.content[1].content;

      assertTrue("Second level should be multipart", message.content instanceof EmailMultiPart);

      multi = (EmailMultiPart) message.content;
      assertTrue("Second level multipart should have 2 subparts", multi.content.length == 2);
      assertTrue("First second-level subpart should be a single part", multi.content[0].content instanceof EmailSinglePart);
      assertTrue("Second second-level subpart should be a single part", multi.content[1].content instanceof EmailSinglePart);

      MimeMessage part = PostMessage.processEmailMessagePartSpecial(email.content);
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      part.writeTo(baos);

      System.out.println(new String(baos.toByteArray()));
    } catch (MailboxException e) {
      e.printStackTrace();
      assertTrue("Exception " + e + " thrown. ", false);
    } catch (MessagingException e) {
      e.printStackTrace();
      assertTrue("Exception " + e + " thrown. ", false);
    } catch (IOException e) {
      e.printStackTrace();
      assertTrue("Exception " + e + " thrown. ", false);
    }
  }

  static String TEST_MESSAGE = "Subject: subject\n\nbody\n.";
  static String LARGE_TEST_MESSAGE =
    "Received: via dmail-2000(11) for amislove; Thu, 5 Jun 2003 00:00:14 -0500 (CDT)\n" +
    "Return-Path: <druschel@cs.rice.edu>\n" +
    "Delivered-To: amislove@owlserv2.mail.rice.edu\n" +
    "Received: from handler7.mail.rice.edu (handler7.mail.rice.edu [128.42.58.207])\n" +
    "        by owlserv2.mail.rice.edu (Postfix) with ESMTP id 0D0EF27FE0\n" +
    "        for <amislove@owlserv2.mail.rice.edu>; Thu,  5 Jun 2003 00:00:14 -0500 (CDT)\n" +
    "Received: by handler7.mail.rice.edu (Postfix)\n" +
    "        id EA2B71DD1A; Thu,  5 Jun 2003 00:00:13 -0500 (CDT)\n" +
    "Delivered-To: amislove@owlnet.rice.edu\n" +
    "Received: from localhost (localhost [127.0.0.1])\n" +
    "        by handler7.mail.rice.edu (Postfix) with SMTP id D162B1DD19\n" +
    "        for <amislove@owlnet.rice.edu>; Thu,  5 Jun 2003 00:00:13 -0500 (CDT)\n" +
    "Received: from localhost (localhost [127.0.0.1])\n" +
    "        by handler7.mail.rice.edu (Postfix) with ESMTP id 7B34B1DD1A\n" +
    "        for <amislove@owlnet.rice.edu>; Thu,  5 Jun 2003 00:00:13 -0500 (CDT)\n" +
    "Received: from cs.rice.edu (cs.rice.edu [128.42.1.30])\n" +
    "        by handler7.mail.rice.edu (Postfix) with ESMTP id 510331DD19\n" +
    "        for <amislove@owlnet.rice.edu>; Thu,  5 Jun 2003 00:00:12 -0500 (CDT)\n" +
    "Received: by cs.rice.edu (Postfix)\n" +
    "        id D20754AEE2; Wed,  4 Jun 2003 23:58:47 -0500 (CDT)\n" +
    "Delivered-To: amislove@cs.rice.edu\n" +
    "Received: from localhost (localhost [127.0.0.1])\n" +
    "        by cs.rice.edu (Postfix) with SMTP\n" +
    "        id AD2424AE2E; Wed,  4 Jun 2003 23:58:43 -0500 (CDT)\n" +
    "Received: from localhost (localhost [127.0.0.1])\n" +
    "        by cs.rice.edu (Postfix) with ESMTP\n" +
    "        id 45DEC4AEF3; Wed,  4 Jun 2003 23:58:43 -0500 (CDT)\n" +
    "Received: from cs.rice.edu ([127.0.0.1])\n" +
    " by localhost (cs.rice.edu [127.0.0.1:10024]) (amavisd-new) with ESMTP\n" +
    " id 06292-05; Wed,  4 Jun 2003 23:57:49 -0500 (CDT)\n" +
    "Received: from cs.rice.edu (dosa.cs.rice.edu [128.42.3.76])\n" +
    "        by cs.rice.edu (Postfix) with ESMTP\n" +
    "        id 4B34A4AE2E; Wed,  4 Jun 2003 23:57:23 -0500 (CDT)\n" +
    "Message-ID: <3EDECD3C.2060701@cs.rice.edu>\n" +
    "Date: Thu, 05 Jun 2003 00:55:24 -0400\n" +
    "From: Peter Druschel <druschel@cs.rice.edu>\n" +
    "Organization: Rice University\n" +
    "User-Agent: Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.3) Gecko/20030312\n" +
    "X-Accept-Language: en-us, en\n" +
    "MIME-Version: 1.0\n" +
    "To: systems@cs.rice.edu\n" +
    "Subject: [Fwd: post doc]\n" +
    "Content-Type: multipart/mixed;\n" +
    " boundary=\"------------090007010600060604060300\"\n" +
    "X-Virus-Scanned: by amavis-20021227p3\n" +
    "X-Virus-Scanned: by AMaViS snapshot-20020300\n" +
    "X-DCC--Metrics: handler7.mail.rice.edu 1067; Body=3 Fuz1=3 Fuz2=3\n" +
    "\n" +
    "This is a multi-part message in MIME format.\n" +
    "--------------090007010600060604060300\n" +
    "Content-Type: text/plain; charset=ISO-8859-1; format=flowed\n" +
    "Content-Transfer-Encoding: 7bit\n" +
    "\n" +
    "\n" +
    "--------------090007010600060604060300\n" +
    "Content-Type: message/rfc822;\n" +
    " name=\"post doc\"\n" +
    "Content-Transfer-Encoding: 7bit\n" +
    "Content-Disposition: inline;\n" +
    " filename=\"post doc\"\n" +
    "\n" +
    "Return-Path: <yhf@cs.duke.edu>\n" +
    "Received: from localhost (localhost [127.0.0.1])\n" +
    "        by cs.rice.edu (Postfix) with SMTP id CCB934ABCD\n" +
    "        for <druschel@cs.rice.edu>; Wed,  4 Jun 2003 11:51:08 -0500 (CDT)\n" +
    "Received: from localhost (localhost [127.0.0.1])\n" +
    "        by cs.rice.edu (Postfix) with ESMTP id 52FF84ABC8\n" +
    "        for <druschel@cs.rice.edu>; Wed,  4 Jun 2003 11:51:08 -0500 (CDT)\n" +
    "Received: from cs.rice.edu ([127.0.0.1])\n" +
    " by localhost (cs.rice.edu [127.0.0.1:10024]) (amavisd-new) with ESMTP\n" +
    " id 27116-08 for <druschel@cs.rice.edu>; Wed,  4 Jun 2003 11:50:59 -0500 (CDT)\n" +
    "Received: from duke.cs.duke.edu (duke.cs.duke.edu [152.3.140.1])\n" +
    "        by cs.rice.edu (Postfix) with ESMTP id D4F2A4AA40\n" +
    "        for <druschel@cs.rice.edu>; Wed,  4 Jun 2003 11:50:53 -0500 (CDT)\n" +
    "Received: from yadkin.cs.duke.edu (yadkin.cs.duke.edu [152.3.141.3])\n" +
    "        by duke.cs.duke.edu (8.12.9/8.12.9) with ESMTP id h54GoqJE023115\n" +
    "        (version=TLSv1/SSLv3 cipher=EDH-RSA-DES-CBC3-SHA bits=168 verify=NO)\n" +
    "        for <druschel@cs.rice.edu>; Wed, 4 Jun 2003 12:50:53 -0400 (EDT)\n" +
    "Date: Wed, 4 Jun 2003 12:50:52 -0400 (EDT)\n" +
    "From: Haifeng Yu <yhf@cs.duke.edu>\n" +
    "To: druschel@cs.rice.edu\n" +
    "Subject: post doc\n" +
    "Message-ID: <Pine.GSO.4.56.0306041240460.2091@yadkin.cs.duke.edu>\n" +
    "MIME-Version: 1.0\n" +
    "Content-Type: MULTIPART/Mixed; BOUNDARY=\"-559023410-851401618-1054693141=:26174\"\n" +
    "Content-ID: <Pine.GSO.4.56.0306041237570.2091@yadkin.cs.duke.edu>\n" +
    "X-Virus-Scanned: by amavis-20021227p3\n" +
    "X-DCC--Metrics: cs.rice.edu 1067; Body=2 Fuz1=2\n" +
    "\n" +
    "  This message is in MIME format.  The first part should be readable text,\n" +
    "  while the remaining parts are likely unreadable without MIME-aware tools.\n" +
    "  Send mail to mime@docserver.cac.washington.edu for more info.\n" +
    "\n" +
    "---559023410-851401618-1054693141=:26174\n" +
    "Content-Type: TEXT/PLAIN; CHARSET=US-ASCII\n" +
    "Content-ID: <Pine.GSO.4.56.0306041237571.2091@yadkin.cs.duke.edu>\n" +
    "\n" +
    "Hi Peter,\n" +
    "\n" +
    "I am currently looking for postdoc opportunities because this year's\n" +
    "academic job search does not yield satisfying results. Amin suggested\n" +
    "me to contact you.\n" +
    "\n" +
    "As you may already know, my main research area is distributed systems\n" +
    "and operating systems. My thesis project TACT has publications in\n" +
    "PODC'02, SOSP'01, OSDI'00, VLDB'00 and other conferences.\n" +
    "\n" +
    "My new WITNESS project enables probabilistic distributed consensus\n" +
    "without majority, and one application will be to maintain strongly\n" +
    "consistency data in p2p systems.\n" +
    "\n" +
    "I attached my latest CV in PDF format. Let me know if I can provide\n" +
    "any additional information and I look forward to your reply.\n" +
    "\n" +
    "Regards,\n" +
    "\n" +
    "Haifeng\n" +
    "\n" +
    "----------------------------------------------------------------------\n" +
    "Haifeng Yu                              Phone: (919) 660-6516\n" +
    "Office: LSRC, Rm D-110                  email: yhf@cs.duke.edu\n" +
    "Department of Computer Science          URL: http://www.cs.duke.edu/~yhf\n" +
    "Duke University, NC 27708\n" +
    "----------------------------------------------------------------------\n" +
    "---559023410-851401618-1054693141=:26174\n" +
    "Content-Type: TEXT/PLAIN; NAME=\"cv.PDF\"\n" +
    "Content-Transfer-Encoding: BASE64\n" +
    "Content-ID: <Pine.GSO.4.56.0306032219010.26174@dan.cs.duke.edu>\n" +
    "Content-Description: \n" +
    "Content-Disposition: ATTACHMENT; FILENAME=\"cv.PDF\"\n" +
    "\n" +
    "JVBERi0xLjMKJcfsj6IKOCAwIG9iago8PC9MZW5ndGggOSAwIFIvRmlsdGVy\n" +
    "Ugo+PgpzdGFydHhyZWYKNDYxNDgKJSVFT0YK\n" +
    "\n" +
    "---559023410-851401618-1054693141=:26174--\n" +
    "\n" +
    "--------------090007010600060604060300--\n" +
    "\n" +
    "\n";
}