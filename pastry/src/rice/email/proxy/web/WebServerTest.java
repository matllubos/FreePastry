package rice.email.proxy.web;

import rice.email.proxy.test.user.*;

public class WebServerTest {
  
  public static void main(String[] args) throws Exception {
    MockUserManager manager = new MockUserManager();
    manager.createUser("amislove", null, "monkey");
    manager.getUser("amislove").getMailbox().createFolder("INBOX");
    
    WebServerImpl web = new WebServerImpl(1080, null, manager);
    web.start();
  }
  
}