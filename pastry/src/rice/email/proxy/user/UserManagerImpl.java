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
package rice.email.proxy.user;

import rice.email.*;
import rice.email.proxy.mailbox.MailboxManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;


public class UserManagerImpl implements UserManager {

  private EmailService email;
  private MailboxManager manager;
  private Hashtable users;
  
  public UserManagerImpl(EmailService email, MailboxManager manager) {
    this.email = email;
    this.manager = manager;
    this.users = new Hashtable();
  }
  
  public String getPassword(String name) throws NoSuchUserException {
    if (users.get(name) != null) {
      return (String) users.get(name);
    }
    
    throw new NoSuchUserException("User " + name + " not found!");
  }
  
  public User getUser(String name) throws NoSuchUserException {
    if (users.get(name) != null) {
      return new UserImpl(name, manager, (String) users.get(name));
    }

    throw new NoSuchUserException("User " + name + " not found!");
  }

  public void createUser(String name, String service, String authenticationData) throws UserException {
    users.put(name, authenticationData);
  }

  public void deleteUser(String name) throws UserException {
    throw new UserException("Cannot delete users.");
  }
}