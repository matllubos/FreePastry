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