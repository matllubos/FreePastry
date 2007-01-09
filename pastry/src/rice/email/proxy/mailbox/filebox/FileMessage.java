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
import java.util.regex.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

/**
 * Flags:
 *
 * <p>
 * C - recent (didn't exist during previous sessions)
 * </p>
 */
public class FileMessage implements StoredMessage {

  // useful filtering stuff
  public static final FileFilter MSG_FILE_FILTER = new MsgFileFilter();
  static final Pattern MAIL_PATTERN = Pattern.compile("(\\d+)\\.(\\w*)");

  // member variables
  File _file;
  int _uid;
  int _sequenceNum;
  FileFlagList _flags;

  static boolean isMsg(File f) {
    return MAIL_PATTERN.matcher(f.getName()).matches();
  }

  public FileMessage(File f, int seq) {
    _file = f;
    _sequenceNum = seq;

    Matcher mat = MAIL_PATTERN.matcher(f.getName());
    mat.matches();

    _uid = Integer.parseInt(mat.group(1));
    String flags = mat.group(2);

    _flags = new FileFlagList(_file, _uid + ".", flags);
  }

  public void purge() throws MailboxException {
    if (!_file.delete())
      throw new MailboxException("Couldn't delete " + _file);
  }

  private MimeMessage getMimeMessage() throws MailboxException {
    try {
      return new MimeMessage(new FileResource(_file));
    } catch (MailException me) {
      throw new MailboxException(me);
    }
  }

  public Email getMessage() throws MailboxException {
    return null;//getMimeMessage();
  }

  public int getSequenceNumber() {
    return _sequenceNum;
  }

  public int getUID() {
    return _uid;
  }
  
  public long getInternalDate() {
    return 0;
  }

  public FlagList getFlagList() {
    return _flags;
  }
}

class MsgFileFilter implements FileFilter {
  public boolean accept(File file) {
    return (file.isFile() && FileMessage.isMsg(file));
  }
}