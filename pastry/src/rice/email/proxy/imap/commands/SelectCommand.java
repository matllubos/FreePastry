package rice.email.proxy.imap.commands;

import rice.email.proxy.mailbox.MailboxException;


/**
 * SELECT command.
 * 
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.1">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.3.1 </a>
 * </p>
 */
public class SelectCommand extends ExamineCommand {
  
  public SelectCommand() {
    super("SELECT");
  }
  
  protected boolean isWritable() {
    return true;
  }
}






