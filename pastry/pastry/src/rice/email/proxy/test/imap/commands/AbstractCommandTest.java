package rice.email.proxy.test.imap.commands;

import rice.email.proxy.imap.*;
import rice.email.proxy.imap.commands.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.test.imap.*;
import rice.email.proxy.test.mail.*;
import rice.email.proxy.test.mailbox.*;
import rice.email.proxy.test.user.*;
import rice.email.proxy.user.*;

import junit.framework.TestCase;

public abstract class AbstractCommandTest extends TestCase {
  
    final static String DEFAULT_USER = "david";

    public AbstractCommandTest(String s) {
        super(s);
    }

    private ImapState state;
    private MockImapConnection conn;
    private MockMailbox mbox;
    private MockUserManager manager;

    protected MockImapConnection getConn() {

        return conn;
    }

    protected ImapState getState() {

        return state;
    }

    protected MockMailbox getDefaultMailbox() {

        return mbox;
    }

    protected MockUserManager getMockUserManager() {

        return manager;
    }

    protected void setUp() throws Exception {
      conn = new MockImapConnection();

      manager = new MockUserManager();

      state = new ImapState(manager, null);
      createDefaultUser(getDefaultMailboxName());
      createDefaultFolder();
    }

    protected void setUp(AbstractImapCommand cmd) throws Exception {
      cmd.setConn(conn);
      cmd.setState(state);
    }

    protected void skipAuthentication(AbstractImapCommand cmd) throws UserException {
        state.setUser(state.getUser(getDefaultMailboxName()));
        cmd.setState(state);
    }

    protected void tearDown() throws Exception {
        conn = null;
        mbox = null;
        state = null;
    }

    protected void createDefaultUser(String name) throws MailboxException, UserException {
        manager.createUser(name, MockMailboxManager.class.getName(), 
                           "password is ignored");
        mbox = (MockMailbox) manager.getUser(name).getMailbox();
    }

    protected void createDefaultFolder() throws MailboxException, UserException {
        state.setUser(state.getUser(getDefaultMailboxName()));
        mbox.createFolder("INBOX");
    }

    protected String getDefaultMailboxName() {
        return DEFAULT_USER;
    }
}