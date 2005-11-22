package rice.email.proxy.mailbox.filebox;

import java.io.*;
import java.util.regex.*;

import rice.email.proxy.imap.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;

public class FileMailbox implements Mailbox {
  File _folder;
  Environment environment;
  
  public FileMailbox(File folder, Environment env) throws NoSuchMailboxException {
    if (!folder.isDirectory())
      throw new NoSuchMailboxException(folder + " is not a directory");
    
    _folder = folder;
    this.environment = env;
  }
  
  public String getHierarchyDelimiter() {
    return "/";
  }


  public void put(MovingMessage msg) throws MailboxException  {
    getFolder("INBOX").put(msg);
  }
  
  public void renameFolder(String old_name, String new_name) throws MailboxException {
    throw new MailboxException("FOLDERS NOT IMPLEMENTED!");
  }

  public MailFolder getFolder(String folder) throws MailboxException {
    if (!isValidFolderName(folder))
      throw new MailboxException("Invalid folder name.");

    return new FileFolder(new File(_folder, folder), folder);
  }

  private boolean isValidFolderName(String fold) {
    File subfolder = new File(_folder, fold);

    return subfolder.getParentFile().equals(_folder);
  }

  public void createFolder(String folder) throws MailboxException {
    if (!isValidFolderName(folder))
      throw new MailboxException("Invalid folder name.");

    FileFolder.createFolder(this, folder);
  }

  public void deleteFolder(String folder) throws MailboxException {
    if (!isValidFolderName(folder))
      throw new MailboxException("Invalid folder name.");

    ((FileFolder) getFolder(folder)).delete();
  }

  public MailFolder[] listFolders(String pattern) throws MailboxException {
    try {
      File[] files = _folder.listFiles(new ListFilter(pattern));
      MailFolder[] folders = new MailFolder[files.length];
      for (int i = 0; i < files.length; i++) {
        MailFolder folder = new FileFolder(files[i],  files[i].getName());
        folders[i] = folder;
      }

      return folders;
    } catch (PatternSyntaxException pse) {
      Logger logger = environment.getLogManager().getLogger(FileMailbox.class, null);
      if (logger.level <= Logger.WARNING) logger.logException(
          "Pattern syntax", pse);
      throw new MailboxException("Pattern syntax", pse);
    }
  }

  public void subscribe(String fullName) throws MailboxException {
    if (!isValidFolderName(fullName))
      throw new MailboxException("Invalid folder name.");

    try {
      String foldName   = fullName;
      String markerName = ".sub" + foldName;
      File markerFile   = new File(_folder, markerName);
      markerFile.createNewFile();
    } catch (IOException e) {
      throw new MailboxException(e);
    }
  }

  public void unsubscribe(String fullName) throws MailboxException {
    String foldName   = fullName;
    String markerName = ".sub" + foldName;
    File markerFile   = new File(_folder, markerName);

    if (!markerFile.getParentFile().equals(_folder))
      throw new MailboxException("Invalid folder name");

    if (!markerFile.delete())
      throw new MailboxException("Couldn't delete subscription to " + fullName);
  }

  public String[] listSubscriptions(String pattern) throws MailboxException {
    try {
      File[] files = _folder.listFiles(new LsubFilter(pattern));
      String[] subscriptions = new String[files.length];
      for (int i = 0; i < files.length; i++) {
        files[i].getName().substring(4);
      }

      return subscriptions;
    } catch (PatternSyntaxException pse) {
      Logger logger = environment.getLogManager().getLogger(FileMailbox.class, null);
      if (logger.level <= Logger.WARNING) logger.logException(
          "Pattern syntax", pse);
      throw new MailboxException("Pattern syntax", pse);
    }
  }

  static class ListFilter implements FileFilter {
    String patStr;
    Pattern pat;

    public ListFilter(String pattern) {
      patStr = PatternConverter.toRegex(pattern);
      pat    = Pattern.compile(patStr);
    }

    public boolean accept(File f) {
      if (!f.isDirectory())

        return false;

      String name = f.getName();

      if (name.startsWith("."))
        return false;

      return pat.matcher(name).matches();
    }
  }

  static class LsubFilter implements FileFilter {
    String patStr;
    Pattern pat;

    public LsubFilter(String pattern) {
      patStr = PatternConverter.toRegex(pattern);
      pat    = Pattern.compile("\\.sub" + patStr);
    }

    public boolean accept(File f)  {
      if (!f.getName().startsWith(".sub"))
        return false;

      if (!f.isFile())
        return false;

      return pat.matcher(f.getName()).matches();
    }
  }

  public static class PatternConverter {
    private static char[] CONTROL_CHARS = new char[] {
      '\\', '.', '[', ']', '{', '}', '(', ')', '?', '+', '|', '^', '$'
    };

    private static String prefixAll(char[] chars, char prefix) {
      StringBuffer buffer = new StringBuffer(chars.length * 2);
      for (int i = 0; i < chars.length; i++)
      {
        buffer.append(prefix);
        buffer.append(chars[i]);
      }

      return buffer.toString();
    }

    private static Pattern CONTROL_PATTERN = Pattern.compile(prefixAll(CONTROL_CHARS, '\\'));

    public static String toRegex(String pattern) {
      Matcher matcher = CONTROL_PATTERN.matcher(pattern);
      String result   = matcher.replaceAll("\\\\$1");

      result = result.replaceAll("\\*|%", ".*");

      return result;
    }
  }
}
