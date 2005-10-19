package rice.email.proxy.test.mail;

import junit.framework.TestCase;

import rice.email.proxy.mail.*;

public class MailAddressTest extends TestCase {
  
    public MailAddressTest(String s) {
        super(s);
    }

    public void testParsing() throws Exception {
        MailAddress addr = new MailAddress("user@domain");
        assertEquals("user", addr.getUser());
        assertEquals("domain", addr.getHost());
    }
}