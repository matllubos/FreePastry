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
package rice.email.proxy.pop3;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.user.*;


public class Pop3State {
  
  UserManager _manager;
  User _user;
  Mailbox _mailbox;
  MailFolder _inbox;
  String _challenge;
  
  public Pop3State(UserManager manager) {
    _manager = manager;
  }
  
  public String getChallenge() {
    return _challenge;
  }
  
  public void setChallenge(String challenge) {
    this._challenge = challenge;
  }
  
  public User getUser() {
    return _user;
  }
  
  public String getPassword(String username) throws UserException {
    return _manager.getPassword(username);
  }
  
  public User getUser(String username) throws UserException {
    return _manager.getUser(username);
  }
  
  public void setUser(User user) throws MailboxException, UserException {
    _user = user;
  }
  
  public boolean isAuthenticated() {
    return _mailbox != null;
  }
  
  public void authenticate(String pass) throws MailboxException, UserException {
    if (_user == null)
      throw new UserException("No user selected");
    
    _user.authenticate(pass);
    _mailbox = _user.getMailbox();
    _inbox = _mailbox.getFolder("INBOX");
  }
  
  public MailFolder getFolder() {
    return _inbox;
  }
}