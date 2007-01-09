/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.email.proxy.imap.commands;

import rice.email.proxy.imap.ImapState;
import rice.email.proxy.user.User;
import rice.email.proxy.user.UserException;


/**
 * LOGIN command.
 * 
 * <p>
 * <a href="http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.2.2">
 * http://asg.web.cmu.edu/rfc/rfc2060.html#sec-6.2.2 </a>
 * </p>
 */
public class LoginCommand
    extends AbstractImapCommand
{
    String username;
    String password;

    public LoginCommand()
    {
        super("LOGIN");
    }

    public boolean isValidForState(ImapState state)
    {

        return !state.isAuthenticated();
    }

    public void execute()
    {
        try
        {
        	User user = getState().getUser(username);
        	user.authenticate(password);
            getState().setUser(user);
            taggedSimpleSuccess();
        }
        catch (UserException nsue)
        {
            taggedExceptionFailure(nsue);
        }
    }

    public String getUser()
    {

        return username;
    }

    public void setUser(String user)
    {
        username = user;
    }

    public String getPassword()
    {

        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }
}