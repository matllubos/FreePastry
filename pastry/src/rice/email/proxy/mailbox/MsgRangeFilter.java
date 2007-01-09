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

import rice.email.proxy.mail.StoredMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MsgRangeFilter
    extends MsgFilter
{
    static final Pattern TWO_PART = Pattern.compile(
                                            "(\\d+|\\*):(\\d+|\\*)");
    int _top;
    int _bottom;
    boolean _isUID;

    public MsgRangeFilter(String rng, boolean uid)
    {
        if (rng.indexOf(':') == -1)
        {
            int value             = Integer.parseInt(rng);
            _top                  = value;
            _bottom               = value;
        }
        else
        {
            Matcher mat = TWO_PART.matcher(rng);
            mat.matches();

            if (mat.groupCount() != 2)
              throw new RuntimeException("GroupCount was not 2!");
            
            String bot = mat.group(1);
            String top = mat.group(2);
            if (bot.equals("*"))
                _bottom = 0;
            else
                _bottom = Integer.parseInt(bot);

            if (top.equals("*"))
                _top = Integer.MAX_VALUE;
            else
                _top = Integer.parseInt(top);

        }

        _isUID = uid;
    }

    public boolean includes(StoredMessage msg)
    {
        int msgValue = (_isUID ? msg.getUID() : msg.getSequenceNumber());

        return msgValue >= _bottom && msgValue <= _top;
    }
}