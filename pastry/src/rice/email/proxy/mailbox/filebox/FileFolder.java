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
package rice.email.proxy.mailbox.filebox;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;


public class FileFolder implements MailFolder {
  File _folder;
  File _tmpDir;
  String _name;

  public FileFolder(File folder, String name) throws MailboxException {
    if (!folder.isDirectory())
      throw new MailboxException(folder + " is not a valid directory");

    _folder = folder;
    _name = name;

    _tmpDir = new File(folder, "tmp");

    if (!_tmpDir.isDirectory())
      throw new MailboxException(_tmpDir + " is not a valid directory");
  }

  public String getFullName() {
    return _name;
  }

  public MailFolder createChild(String name) throws MailboxException {
    throw new MailboxException("FOLDERS NOT IMPLEMENTED!");
  }

  
  static void createFolder(FileMailbox base, String name) throws MailboxException {
    File parent = base._folder;
    File fold   = new File(parent, name);
    if (!fold.mkdir())
      throw new MailboxException("Failed to create directory " + fold);

    File tmp = new File(fold, "tmp");
    if (!tmp.mkdir())
      throw new MailboxException("Failed to create directory " + tmp);

    File uidFile = new File(fold, ".uid" + UIDFactory.getUniqueId());

    try {
      if (!uidFile.createNewFile()) {
        fold.delete();
      }
    } catch (IOException ioe) {
      throw new MailboxException("Failed to create " + uidFile);
    }
  }

  public void delete() throws MailboxException {
    if ("INBOX".equals(_name))
      throw new MailboxException("INBOX may not be deleted");

    if (!_folder.delete())
      throw new MailboxException("Couldn't delete " + _folder);
  }

  public void put(MovingMessage msg, List flags, long date) throws MailboxException {
    put(msg);
  }

  public void put(MovingMessage msg) throws MailboxException {
    try {
      String fName   = UIDFactory.getUniqueId() + ".C";
      File spot;
      spot           = new File(_tmpDir, fName);
      FileWriter out = new FileWriter(spot);
      StreamUtils.copy(msg.getContent(), out);
      out.close();
      spot.renameTo(new File(_folder, fName));
    } catch (IOException ioe) {
      throw new MailboxException(ioe);
    }
  }

  public List getMessages(MsgFilter range) throws MailboxException {
    return getMsgs().filter(range).toStoredMessageList();
  }

  public String getUIDValidity() throws MailboxException {
    File[] files = _folder.listFiles(UID_FILTER);
    if (files.length != 1)
      throw new MailboxException("Not one UID validity");

    String fName = files[0].getName();
    if (fName.length() < 5)
      throw new MailboxException("UID Validity too short");

    return fName.substring(4);
  }

  public int getNextUID() throws MailboxException {
    return UIDFactory.getUniqueId();
  }

  FileMessageList getMsgs() {
    return new FileMessageList(_folder.listFiles(FileMessage.MSG_FILE_FILTER));
  }

  public int getExists() throws MailboxException {
    return 0;
  }

  public int getRecent() throws MailboxException {
    return 0;
  }

  static final FileFilter UID_FILTER = new UIDFileFilter();
  static final Pattern UID_PATTERN   = Pattern.compile("\\.uid(\\d+)");

  static class UIDFileFilter implements FileFilter {
    public boolean accept(File f) {
      String fName = f.getName();

      return UID_PATTERN.matcher(fName).matches();
    }
  }
  
  public void copy(MovingMessage[] messages, List[] flags, long[] dates) throws MailboxException {
    throw new MailboxException("COPY NOT IMPLEMENTED!");
  }
  
  public void purge(StoredMessage[] messages) throws MailboxException {
    throw new MailboxException("PURGE NOT IMPLEMENTED!");
  }
  
  public void update(StoredMessage[] messages) throws MailboxException {
    throw new MailboxException("UPDATE NOT IMPLEMENTED!");
  }
    
  public MailFolder getChild(String name) throws MailboxException {
    throw new MailboxException("FOLDERS NOT IMPLEMENTED!");
  }
    
  public MailFolder[] getChildren() throws MailboxException {
    throw new MailboxException("FOLDERS NOT IMPLEMENTED!");
  }
}