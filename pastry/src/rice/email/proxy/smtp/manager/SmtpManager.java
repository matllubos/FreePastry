package rice.email.proxy.smtp.manager;

import rice.email.proxy.mail.MailAddress;

import rice.email.proxy.smtp.SmtpState;


public interface SmtpManager
{
    String ROLE = SmtpManager.class.getName();

    String checkSender(SmtpState state, MailAddress sender);

    String checkRecipient(SmtpState state, MailAddress rcpt);

    String checkData(SmtpState state);

    void send(SmtpState state, boolean local) throws Exception;
    
    boolean isPostAddress(String string);
}