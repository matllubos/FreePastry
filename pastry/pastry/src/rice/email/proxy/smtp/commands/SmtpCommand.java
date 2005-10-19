package rice.email.proxy.smtp.commands;

import java.io.IOException;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;

public abstract class SmtpCommand {

	public abstract void execute(
		SmtpConnection conn,
		SmtpState state,
		SmtpManager manager,
		String commandLine)
		throws IOException;

}
