package rice.email.proxy.imap.commands;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import rice.email.proxy.imap.ImapState;
import rice.email.proxy.imap.commands.fetch.FetchOptionRegistry;
import rice.email.proxy.imap.commands.fetch.FetchPart;
import rice.email.proxy.mail.StoredMessage;
import rice.email.proxy.mailbox.MailFolder;
import rice.email.proxy.mailbox.MailboxException;
import rice.email.proxy.mailbox.MsgFilter;


/**
* FETCH command.
 *
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.5">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.4.5 </a>
 * </p>
 *
 * <p>
 * <a  href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.4.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-7.4.2 </a>
 * </p>
 */
public class FetchCommand extends AbstractImapCommand {
  
  public static FetchOptionRegistry regestry = new FetchOptionRegistry();

  public FetchCommand() {
    super("FETCH");
  }

  public boolean isValidForState(ImapState state) {
    return state.isSelected();
  }

  MsgFilter _range;
  List parts = new LinkedList();

  public void execute() {
    try {
      ImapState state = getState();
      MailFolder fold = state.getSelectedFolder();
      List msgs = fold.getMessages(_range);
      String result = "";
      for (Iterator i = msgs.iterator(); i.hasNext();) {
        StoredMessage msg = (StoredMessage) i.next();

        result += fetchMessage(msg);
      }

      getConn().print(result);

      taggedSimpleSuccess();
    } catch (MailboxException e) {
      taggedExceptionFailure(e);
    }
  }

  String fetchMessage(StoredMessage msg) throws MailboxException {
    String result = "* " + msg.getSequenceNumber() + " FETCH (";

    for (Iterator i = parts.iterator(); i.hasNext();) {
      Object part = i.next();
      FetchPart handler = regestry.getHandler(part);
      result += handler.fetch(msg, part);

      if (i.hasNext())
        result += " ";
    }

    return result +")\r\n";
  }

  public void appendPartRequest(String string) {
    if (parts.contains(string)) {
      return;
    }

    parts.add(string.toUpperCase());
  }

  public void appendPartRequest(Object obj)
  {
    parts.add(obj);
  }

  public List getParts()
  {

    return parts;
  }

  public MsgFilter getRange()
  {

    return _range;
  }

  public void setRange(MsgFilter range)
  {
    _range = range;
  }
}













