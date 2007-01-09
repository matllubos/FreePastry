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

import rice.email.proxy.mailbox.*;

public class FileFlagList implements FlagList {

  HashSet _flags;
  File _msg;
  String _prepend;

  FileFlagList(File msg, String prepend, String flags) {
    _msg = msg;
    _prepend = prepend;
    _flags = new HashSet();

    if (flags.indexOf('D') != -1)
      setDeleted(true);

    if (flags.indexOf('S') != -1)
      setSeen(true);

    if (flags.indexOf('C') != -1)
      setFlag("\\Recent", true);
  }

  public void addFlag(String flag) {
    setFlag(flag, true);
  }

  public void removeFlag(String flag) {
    setFlag(flag, false);
  }

  public boolean isSet(String flag) {
    return _flags.contains(flag);
  }

  public void setFlag(String flag, boolean value) {
    if (value)
      _flags.add(flag);
    else
      _flags.remove(flag);
  }

  public void commit() throws MailboxException {
    StringBuffer flagBuffer = new StringBuffer(4);
    if (isRecent())
      flagBuffer.append('C');

    if (isDeleted())
      flagBuffer.append('D');

    if (isSeen())
      flagBuffer.append('S');

    String flagString = flagBuffer.toString();
    File newFile      = new File(_msg.getParent(),
                                 _prepend + flagString);
    if (!_msg.renameTo(newFile))
      throw new MailboxException("Couldn't rename " + _msg + " to " + newFile);

    _msg = newFile;
  }
  
  public boolean isRecent() {
    return _flags.contains("\\Recent");
  }
  
  public boolean isDraft() {
    return _flags.contains("\\Draft");
  }
  
  public boolean isAnswered() {
    return _flags.contains("\\Answered");
  }
  
  public boolean isDeleted() {
    return _flags.contains("\\Deleted");
  }
  
  public boolean isFlagged() {
    return _flags.contains("\\Flagged");
  }

  public boolean isSeen() {
    return _flags.contains("\\Seen");
  }

  public void setDeleted(boolean deleted) {
    setFlag("\\Deleted", deleted);
  }
  
  public void setSeen(boolean seen) {
    setFlag("\\Seen", seen);
  }
  
  public void setRecent(boolean seen) {
    setFlag("\\Recent", seen);
  }
  
  public void setDraft(boolean seen) {
    setFlag("\\Draft", seen);
  }
  
  public void setAnswered(boolean seen) {
    setFlag("\\Answered", seen);
  }
  
  public void setFlagged(boolean seen) {
    setFlag("\\Flagged", seen);
  }

  public List getFlags() {
    return null;
  }
  
  public String toFlagString() {
    StringBuffer flagBuffer = new StringBuffer();

    Iterator i = _flags.iterator();

    while (i.hasNext()) {
      flagBuffer.append((String) i.next());
    }

    return "(" + flagBuffer.toString().trim() + ")";
  }
}