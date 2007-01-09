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
package rice.email.proxy.mailbox;

import rice.email.proxy.mail.*;

import java.util.List;


public interface MailFolder {

    /**
     * Obtains a list of <code>StoredMessage</code>s  in this folder
     * that fall within the designated range.
     */
    List getMessages(MsgFilter range) throws MailboxException;

    String getFullName();

    int getNextUID() throws MailboxException;
    String getUIDValidity() throws MailboxException;
    int getExists() throws MailboxException;
    int getRecent() throws MailboxException;

    void put(MovingMessage msg, List flags, long internaldate) throws MailboxException;
    void put(MovingMessage msg) throws MailboxException;
    void copy(MovingMessage[] messages, List[] flags, long[] dates) throws MailboxException;
    void update(StoredMessage[] messages) throws MailboxException;
    void purge(StoredMessage[] messages) throws MailboxException;
    
    public MailFolder getChild(String name) throws MailboxException;
    public MailFolder createChild(String name) throws MailboxException;
    public MailFolder[] getChildren() throws MailboxException;
    public void delete() throws MailboxException;
}
