package rice.email.proxy.smtp.manager;

import java.net.InetAddress;

import rice.Continuation;
import rice.email.proxy.mail.MailAddress;

import rice.email.proxy.smtp.*;


public interface SmtpManager
{
    String ROLE = SmtpManager.class.getName();

    String checkSender(SmtpConnection conn, SmtpState state, MailAddress sender);

    String checkRecipient(SmtpConnection conn, SmtpState state, MailAddress rcpt);

    String checkData(SmtpState state);

    void send(SmtpState state, boolean local) throws Exception;
    
    void isPostAddress(String string, Continuation c);
    
    public InetAddress getLocalHost();
}