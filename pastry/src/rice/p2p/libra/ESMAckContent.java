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

import rice.replay.*;
import rice.pastry.PastryNode;
import rice.p2p.scribe.ScribeContent;

/**
 * This represents meta data which helps in forming the ESM tree. This metadata is kept at the parent node in the Anycast tree for its children nodes
 *
 */
public class ESMAckContent implements ScribeContent {
    public String topicName = "";

    public void dump(ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.p2p.scribe.ScribeContent.idLibraESMAckContent);
	int tNumber = topicName2Number(topicName);
	buffer.appendInt(tNumber);
    }

    
    public ESMAckContent(ReplayBuffer buffer, PastryNode pn) {
	Verifier.assertTrue(buffer.getByte() == ScribeContent.idLibraESMAckContent);
	int tNumber = buffer.getInteger();
	this.topicName = "" + tNumber;	

    }


    // This is the default aggregate esmcontent
    public ESMAckContent(String topicName) {
	this.topicName = topicName;
	
    }

    private int  topicName2Number(String name) {
	try {
	    int tNumber = Integer.parseInt(name);
	    return tNumber;
	} catch(Exception e) {
	    System.out.println("ERROR: TopicNumber could not be extracted from " + name);
	    return -1;
	}
    }

    public String toString() {
	String s = "ESMAckContent:" + topicName;
	return s;
    }

}
