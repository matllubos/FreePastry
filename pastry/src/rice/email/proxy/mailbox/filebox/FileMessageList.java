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