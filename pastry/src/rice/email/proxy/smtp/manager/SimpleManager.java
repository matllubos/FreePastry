package rice.email.proxy.smtp.manager;

import rice.*;
import rice.email.*;
import rice.email.proxy.smtp.*;
import rice.email.proxy.mail.*;

public class SimpleManager implements SmtpManager {

  private EmailService email;

  public SimpleManager(EmailService email) {
    this.email = email;
  }

  public String checkSender(SmtpState state, MailAddress sender) {
    return null;
  }

  public String checkRecipient(SmtpState state, MailAddress rcpt) {
    /*     MailAddress sender = state.getMessage().getReturnPath();

    if (!_configuration.isLocalAddress(rcpt) &&
        !_configuration.isLocalAddress(sender))
  {

      return "550 Requested action not taken: user not local";
  } */

    return null;
  }

  public String checkData(SmtpState state) { 

    return null;
  }

  public void send(SmtpState state) throws Exception {
    final Exception[] exception = new Exception[1];
    final Object[] result = new Object[1];
    final Object wait = "wait";
    
    Continuation done = new Continuation() {
      public void receiveResult(Object o) {
        synchronized(wait) {
          result[0] = "result";
          wait.notify();
        }
      }

      public void receiveException(Exception e) {
        synchronized(wait) {
          exception[0] = e;
          result[0] = "result";
          wait.notify();
        }
      }
    };
    
    email.sendMessage(state.getMessage().toEmail(), done);

    synchronized(wait) { if (result[0] == null) wait.wait(); }
      
    if (exception[0] != null) {
      throw exception[0];
    }
  }
}