package rice.email.proxy.smtp;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.util.Workspace;

public class SmtpState {
  MovingMessage currentMessage;
  Workspace _workspace;

  public SmtpState(Workspace workspace)
  {
    _workspace = workspace;
    clearMessage();
  }

  public MovingMessage getMessage()
  {

    return currentMessage;
  }

  /**
    * To destroy a half-contructed message.
   */
  public void clearMessage()
  {
    if (currentMessage != null)
      currentMessage.releaseContent();

    currentMessage = new MovingMessage(_workspace);
  }
}