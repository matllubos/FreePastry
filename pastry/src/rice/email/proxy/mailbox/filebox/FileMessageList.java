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

public class FileMessageList {
  
  List msgs = new ArrayList();

  public FileMessageList(File[] msgFiles) {
    Arrays.sort(msgFiles, new MsgComparator());
    int msgCount = 0;
    for (int i = 0; i < msgFiles.length; i++) {
      msgs.add(new FileMessage(msgFiles[i], ++msgCount));
    }
  }

  private FileMessageList()
  {
  }

  public FileMessageList filter(MsgFilter range) {
    FileMessageList result = new FileMessageList();
    for (Iterator i = msgs.iterator(); i.hasNext();) {
      FileMessage m = (FileMessage) i.next();
      if (range.includes(m))
        result.msgs.add(m);
    }

    return result;
  }

  public List toStoredMessageList() throws MailboxException {
    return msgs;
  }
}

class MsgComparator implements Comparator {
  public int compare(Object arg0, Object arg1)  {
    File fOne = (File) arg0;
    File fTwo = (File) arg1;

    String uid1 = extractUID(fOne);
    String uid2 = extractUID(fTwo);

    if (uid1.length() != uid2.length())
      return uid1.length() - uid2.length();
    else
      return uid1.compareTo(uid2);
  }

  private String extractUID(File f) {
    String name = f.getName();
    int len     = name.indexOf('.');

    return name.substring(0, len);
  }
}