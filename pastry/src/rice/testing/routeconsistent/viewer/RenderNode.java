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
 * Created on Apr 21, 2005
 */
package rice.testing.routeconsistent.viewer;

import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Jeff Hoye
 */
public class RenderNode {
  Node n;
  
  /**
   * List of squares, sorted by t1
   */
  List<Square> squares;

  int readyStart;
  int readyEnd;
  int left = Integer.MAX_VALUE;
  int right = 0;

  boolean leftSide;
  
  /**
   * @param node
   */
  public RenderNode(Node node, boolean leftSide) {
    this.n = node;
    this.leftSide = leftSide;
    squares = new LinkedList<Square>();
  }

  public Square getSquare(int space, long time) {
    // quick check of bounding box
    if (space < left) return null;
    if (space > right) return null;
    
    // linear search, could be much faster with binary search
    Iterator<Square> i = squares.iterator();
    while(i.hasNext()) {
      Square s = (Square)i.next(); 
      if (time > s.t2) continue;
      if (time < s.time) continue;
      if (s.left < s.right) { // typical case
        if (space < s.left) continue;
        if (space > s.right) continue;
        return s;
      } else { // wrapped case
        // the user clicked in the middle of where we exist
        if ((space > s.right) && (space < s.left)) continue;
        return s;
      } 
    }
    return null;
  }


  public void renderSelf(Graphics g, double xScale, double yScale, int xOff, long yOff, int maxX, int maxY, boolean selected,int renderType) {
    // TODO: cull based on bounding rectangle... just exit out if fails
    if (selected) {
      g.setColor(n.selectedColor); 
    } else {
      g.setColor(n.color);
    }
//    System.out.println(nodeName+" numSq:"+squares.size());
    Iterator<Square> i = squares.iterator();
    while(i.hasNext()) {
      Square s = (Square)i.next();
      if ((renderType != ConsRenderer.PL_RENDER_NOT) || s.type < 3) {
        if (renderType == ConsRenderer.PL_RENDER_LITE) {
          if (s.type > 3) {
            g.setColor(n.liteColor);
          } else {
            if (selected) {
              g.setColor(n.selectedColor);            
            } else {
              g.setColor(n.color);            
            }
          }
        }
        int x = (int)((xOff+s.left)*xScale);
        int w = (int)((xOff+s.right)*xScale) - (int)((xOff+s.left)*xScale);
        int y = (int)((yOff+s.time)*yScale);
        int h = (int)((yOff+s.t2)*yScale)-(int)((yOff+s.time)*yScale);
        //System.out.println("Space["+s.left+","+s.right+"] out:"+x+","+y+","+w+","+h);
        g.fillRect(x,y,w,h);
      }
    }    
    // draw setReady() line
    if (n.readyTime != 0) {
      g.setColor(Node.readyColor);
      int y = (int)((yOff+n.readyTime)*yScale);
      int x1 = (int)((xOff+readyStart)*xScale);
      int x2 = (int)((xOff+readyEnd)*xScale);
      g.drawLine(x1, y, x2, y);
    }
  }

  public void move(long diff) {
    Iterator<Square> i = squares.iterator();
    while(i.hasNext()) {
      Square s = (Square)i.next();
      s.time+=diff;
      s.t2+=diff;
    }
  }

  /**
   * @param s
   * @param real if this is a square that should be added, or just a stub to take up time
   */
  Square lastSquare = null;
  public void addSquare(Square s, boolean real) {
//    if (n.fileName.equals("log4.txt.planetlab1.ifi.uio.no")) {
//      System.out.println(this+" RenderNode.addSquare("+s+")"); 
//    }
    if (lastSquare != null) { 
      lastSquare.t2 = s.time;      
    }
    if (s.type != 3) {
      lastSquare = s;
    }
    if (real) {
      if (s.type != 3) {
        squares.add(s);          
      }
    } else {
      // don't need to add it at all if not real, squares have a start/stop time
//      if (leftSide) {
//      
//      } else {
//        
//      }
    }
  }
  
  public Collection<Overlap> overlaps(RenderNode that) {
    List<Overlap> l = new LinkedList<Overlap>();
    if (this.left >= that.right) return l;
    if (this.right <= that.left) return l;      
    Iterator<Square> i = squares.iterator();
    while(i.hasNext()) {
      Square thisSquare = (Square)i.next(); 
      Iterator<Square> i2 = that.squares.iterator();
      while(i2.hasNext()) {
        Square thatSquare = (Square)i2.next(); 
        Overlap o = thisSquare.overlap(thatSquare);
        if (o != null) {
          l.add(o); 
        }
      }
    }
    return l;
  }

}
