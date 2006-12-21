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
package rice.email.proxy.web;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.mail.MovingMessage;
import rice.email.proxy.user.*;
import rice.email.proxy.util.*;

public class WebState {
  
  protected User user;
  protected MailFolder currentFolder;
  protected int currentMessageUID;
  protected UserManager userManager;
  
  
  public WebState(UserManager userManager) {
    this.userManager = userManager;
    this.currentMessageUID = -1;
  }

  public User getUser() {
    return user;
  }
  
  public MailFolder getCurrentFolder() {
    return currentFolder;
  }
  
  public int getCurrentMessageUID() {
    return currentMessageUID;
  }
  
  public void setCurrentMessageUID(int uid) {
    this.currentMessageUID = uid;
  }
  
  public void setCurrentFolder(String name) throws UserException, MailboxException {
    this.currentFolder = user.getMailbox().getFolder(name);
    this.currentMessageUID = -1;
  }
  
  public void setUser(User user) throws UserException, MailboxException {
    this.user = user;
    this.currentFolder = user.getMailbox().getFolder("INBOX");
  } 
  
  public User getUser(String username) throws UserException  {
    return userManager.getUser(username);
  }
  
  public String getPassword(String username) throws UserException {
    return userManager.getPassword(username);
  }
  
}