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
package rice.email.proxy.mailbox.filebox;

import java.io.File;

import rice.email.proxy.mailbox.*;
import rice.environment.Environment;

public class FileMailboxManager implements MailboxManager {
  File base;
  Environment environment;
  
  public FileMailboxManager(File base, Environment env) {
    this.base = base;
    this.environment = env;
  }

  public String getMailboxType() {
    return FileMailboxManager.class.getName();
  }

  public String getName() {
    return getMailboxType();
  }

  private boolean isValidMailboxName(String username) {
    File userDir = new File(base, username);

    return userDir.getParentFile().equals(base);
  }

  public boolean mailboxExists(String username) {
    return (isValidMailboxName(username) &&
            new File(base, username).isDirectory());
  }

  public void destroyMailbox(String username) throws MailboxException {
    if (!isValidMailboxName(username))
      throw new MailboxException("Invalid username");

    File userMailbox = new File(base, username);
    if (!userMailbox.delete())
      throw new MailboxException("Failed to delete " + userMailbox);
  }

  public void createMailbox(String username) throws MailboxException {
    if (!isValidMailboxName(username))
      throw new MailboxException("Invalid username");

    createMailbox(new File(base, username));
  }

  public Mailbox getMailbox(String username) throws NoSuchMailboxException {
    if (!isValidMailboxName(username))
      throw new NoSuchMailboxException("Invalid username");

    return new FileMailbox(new File(base, username), environment);
  }

  private void createMailbox(File fold) throws MailboxException {
    if (fold.isDirectory())
      throw new MailboxException("Folder already exists");

    if (!fold.mkdir()) {
      fold.delete();
      throw new MailboxException("Couldn't create " + fold.toString());
    }

    FileMailbox maildir = new FileMailbox(fold, environment);

    FileFolder.createFolder(maildir, "INBOX");
  }
}