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

  public FlagList getFlagList() {
    return _flags;
  }
}

class MsgFileFilter implements FileFilter {
  public boolean accept(File file) {
    return (file.isFile() && FileMessage.isMsg(file));
  }
}