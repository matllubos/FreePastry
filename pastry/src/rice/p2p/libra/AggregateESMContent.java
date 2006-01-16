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
package rice.p2p.libra;

import rice.p2p.util.MathUtils;

import java.util.*;
import rice.replay.*;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.NodeHandleSet;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.Topic;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;

/**
 * This represents meta data which helps in forming the ESM tree. This metadata is kept at the parent node in the Anycast tree for its children nodes
 *
 */
public class AggregateESMContent implements ScribeContent {
    
    // This is the list of updates for the different topics
    public Vector allTopics;
 
    public static class UpdatePair {
	public Topic topic;
	public ESMContent content;

	public UpdatePair(Topic topic, ESMContent content) {
	    this.topic = topic;
	    this.content = content;
	}

	public void dump(ReplayBuffer buffer, PastryNode pn) {
	    topic.dump(buffer,pn);
	    content.dump(buffer,pn);
	}
	
	public UpdatePair(ReplayBuffer buffer, PastryNode pn) {
	    topic = new Topic(buffer,pn);
	    content = new ESMContent(buffer,pn);
	}

    }


    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.p2p.scribe.ScribeContent.idLibraAggregateESMContent);
	buffer.appendShort(allTopics.size());
	for(int i=0; i< allTopics.size(); i++) {
	    UpdatePair pair = (UpdatePair) allTopics.elementAt(i);
	    pair.dump(buffer,pn);
	}
    }


    public AggregateESMContent(ReplayBuffer buffer, PastryNode pn) {
	Verifier.assertTrue(buffer.getByte() == ScribeContent.idLibraAggregateESMContent);
	this.allTopics = new Vector();
	int allTopicsSize = buffer.getShort();
	for(int i=0; i< allTopicsSize; i++) {
	    UpdatePair pair = new UpdatePair(buffer,pn);
	    allTopics.add(pair);
	}

    }


    public AggregateESMContent() {
	allTopics = new Vector();
    }


    public int getNumUpdates() {
	return allTopics.size();
    }

    public void appendUpdate(Topic t, ESMContent content) {
	UpdatePair pair = new UpdatePair(t,content);
	allTopics.add(pair);

    }

    

    

    public String toString() {
	String s = "[ AggregateESMContent: " + allTopics.size() +  " (";
	for(int i=0; i< allTopics.size(); i++) {
	    UpdatePair pair = (UpdatePair) allTopics.elementAt(i);
	    s = s + pair.topic + " ";
	}
	s = s + ") ]";
	return s;
    }
    
}

