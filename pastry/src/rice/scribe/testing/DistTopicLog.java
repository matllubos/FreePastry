/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.scribe.testing;

/**
 * @(#) DistTopicLog.java
 *
 * A log class corresponding to each topic to maintain the information about 
 * the sequence numbers received or published corresponding to this topic.
 *
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi 
 */

public class DistTopicLog{
    private int lastSeqNumRecv = -1;
    private long lastRecvTime = System.currentTimeMillis();
    private int seqNumToPublish = -1;
    private int count = 1;
    private boolean unsubscribed = false;
    
    public DistTopicLog(){
	}
    
    public int getLastSeqNumRecv(){
	return lastSeqNumRecv;
    }
    
    public void setLastSeqNumRecv(int seqno){
	lastSeqNumRecv = seqno;
	return;
    }

    public int getSeqNumToPublish(){
	return seqNumToPublish ;
    }
    
    public void setSeqNumToPublish(int seqno){
	seqNumToPublish = seqno;
	return;
    }

    public int getCount(){
	return count ;
    }
    
    public void setCount(int value){
	count = value;
	return;
    }

    public boolean getUnsubscribed(){
	return unsubscribed;
    }

    public void setUnsubscribed(boolean value){
	unsubscribed = value;
	return;
    }

    public long getLastRecvTime(){
	return lastRecvTime;
    }

    public void setLastRecvTime(long value){
	lastRecvTime = value;
	return;
    }
}




