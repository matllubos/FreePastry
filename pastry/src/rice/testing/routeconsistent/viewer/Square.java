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
/*
 * Created on Apr 8, 2005
 */
package rice.testing.routeconsistent.viewer;

/**
 * @author Jeff Hoye
 */
public class Square {
  String fileName;
  int lineNum;
  int type;
  public long time, t2;
  int left;
  String nodeName;
  int right;
  
  public Square() {}
  public Square(long time, long t2, int left, int right, int type) {
    this.time = time; 
    this.t2 = t2; 
    this.left = left;
    this.right = right;
    this.type = type;
  }
  
  public String toString() {
    return fileName+","+lineNum+","+type+","+time+","+left+","+nodeName+","+right; 
  }

  public String getLeft() {
    return Integer.toString(left,16).toUpperCase();
  }
  public String getRight() {
    return Integer.toString(right,16).toUpperCase();
  }
  /**
   * @param thatSquare
   * @return
   */
  public Overlap overlap(Square that) {
    if (this.type >= 3) return null;
    if (that.type >= 3) return null;
    
    if (this.time > that.t2) return null;
    if (this.t2 < that.time) return null;
    if (this.left >= that.right) return null;
    if (this.right <= that.left) return null;      

    Overlap o = new Overlap();
    o.t1 = Math.max(this.time, that.time);
    o.t2 = Math.min(this.t2, that.t2);
    o.left = Math.max(this.left, that.left);
    o.right = Math.min(this.right, that.right);
    
    return o;
  }
}

