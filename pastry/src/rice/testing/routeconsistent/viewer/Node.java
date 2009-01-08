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

import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * @author Jeff Hoye
 */
public class Node {
  
  /**
   * The node's name.
   */
  String nodeName;
  
  /**
   * The node's filename.
   */
  String fileName;
  
  // ************ Bounding Rect ***********
  /**
   * The global lifetime of the node
   */
  long t1 = Long.MAX_VALUE;
  long t2 = 0;
  long readyTime;
  
  /**
   * The span of the node
   */
//  int left = Integer.MAX_VALUE;
//  int right = 0;
//  int readyStart;
//  int readyEnd;
  
  String ip;
  
  static Color readyColor = Color.BLACK;
  
  /**
   * List of squares, sorted by t1
   */
//  List squares;
  
  Color color = null;  
  Color selectedColor = null;
  Color liteColor = null;
  
  /** 
   * Time from first square to a ready square.
   */
  int timeToReady = -1;
  
  boolean empty = false;
  public Node() {
    empty = true;
  }
  
  public RenderNode r1,r2,primary,secondary;
  
  /**
   * @param nodeName
   * @param fileName
   */
  public Node(String nodeName, String fileName) {
    empty = false;
    this.nodeName = nodeName;
    this.fileName = fileName;
//    squares = new LinkedList();
    Random r = new Random(fileName.hashCode());    
    float red = r.nextFloat();
    float green = r.nextFloat();
    float blue = r.nextFloat();
    color = new Color(red, green, blue, 0.5f);
    liteColor = new Color(red, green, blue, 0.2f);
    selectedColor = new Color(red, green, blue);
    
    r1 = new RenderNode(this, true);
    r2 = new RenderNode(this, false);
    
    String firstChar = nodeName.substring(2,3);
    //System.out.println("nodeName:"+nodeName+" firstChar \""+firstChar+"\"");
    if (Integer.valueOf(firstChar,16).intValue() <= 7) {
      primary = r1;
      secondary = r2;
    } else {
      primary = r2;
      secondary = r1;
    }
  }

  public String htmlColor() {
    if (empty) return "FFFFFF";
    String s = "";
    s+=Integer.toString(color.getRed(), 16);
    s+=Integer.toString(color.getGreen(), 16);
    s+=Integer.toString(color.getBlue(), 16);
    return s;
  }

  
  Square lastSquare = null;

  public void addSquare(Square s) {
    if (empty) throw new RuntimeException("oops");
    //System.out.println("fileName:"+fileName);
//    if (fileName.equals("log4.txt.planetlab1.ifi.uio.no")) {
//      System.out.println(nodeName+"Node.addSquare("+s+")"); 
//    }
    if (!s.fileName.equals(fileName)) {
      System.out.println("Warning: nodeId collision ID"+nodeName+" file1:"+fileName+" file2:"+s.fileName);
      return;
    }
    s.fileName = null;
    s.nodeName = null;
    s.t2=s.time+1000;
    
    if (s.time < t1) t1 = s.time;
    if (s.t2 > t2) t2 = s.t2;
    
    if (s.type != 3) {
//      squares.add(s);          
      lastSquare = s;
    }
    
    // don't count the 3 because it will often be after a 1/2
    if (s.type > 3) {
      timeToReady = (int)(s.time-t1); 
    }
    
    // we got a 3, and never got a 1/2, so count the 3
    if (s.type == 3 && lastSquare != null && lastSquare.type>3) {
      timeToReady = (int)(s.time-t1);       
    }
    if (readyTime == 0 && ((s.type == 1) || (s.type == 2))) {
      readyTime = s.time;
      int readyStart = s.left;
      int readyEnd = s.right;
      if (readyStart < readyEnd) {
        primary.readyStart = readyStart;
        primary.readyEnd = readyEnd;
      } else {
        System.out.println("TODO: Wrap around readyLine");
      }
      timeToReady = (int)(s.time-t1);       
    }
    
    if (s.left < s.right) { // typical case
//      if (s.left < left) left = s.left;
//      if (s.right > right) right = s.right;
      // update primary
      if (s.left < primary.left) primary.left = s.left;
      if (s.right > primary.right) primary.right = s.right;
      
      primary.addSquare(s,true);
      secondary.addSquare(s,false);
    } else { // wrap around case      
//      left = 0; 
//      right = ConsRenderer.maxRingSpaceValue;
      
      r1.left = 0;
      r2.right = ConsRenderer.maxRingSpaceValue;
      if (s.right > r1.right) r1.right = s.right;
      if (s.left < r2.left) r2.left = s.left;
      
      r1.addSquare(new Square(s.time,s.t2,0,s.right,s.type), true);
      r2.addSquare(new Square(s.time,s.t2,s.left,ConsRenderer.maxRingSpaceValue,s.type), true);
    }
  }
    
  public Square getSquare(int space, long time) {
    if (empty) return null;    
    // quick check of bounding box
    if (time > t2) return null;
    if (time < t1) return null;
    Square s = primary.getSquare(space, time);
    if (s != null) {
      return s;
    }
    return secondary.getSquare(space, time);    
  }
  
  
  public void renderSelf(Graphics g, double xScale, double yScale, int xOff, long yOff, int maxX, int maxY, boolean selected,int renderType) {
    if (empty) return;
    r1.renderSelf(g, xScale, yScale, xOff, yOff, maxX, maxY, selected, renderType);
    r2.renderSelf(g, xScale, yScale, xOff, yOff, maxX, maxY, selected, renderType);
    
    // TODO: cull based on bounding rectangle... just exit out if fails
//    if (selected) {
//      g.setColor(selectedColor); 
//    } else {
//      g.setColor(color);
//    }
//    System.out.println(nodeName+" numSq:"+squares.size());
//    Iterator i = squares.iterator();
//    while(i.hasNext()) {
//      Square s = (Square)i.next();
//      if ((renderType != ConsRenderer.PL_RENDER_NOT) || s.type < 3) {
//        if (renderType == ConsRenderer.PL_RENDER_LITE) {
//          if (s.type > 3) {
//            g.setColor(liteColor);
//          } else {
//            if (selected) {
//              g.setColor(selectedColor);            
//            } else {
//              g.setColor(color);            
//            }
//          }
//        }
//        int x = (int)((xOff+s.left)*xScale);
//        int w = (int)((xOff+s.right)*xScale) - (int)((xOff+s.left)*xScale);
//        int y = (int)((yOff+s.time)*yScale);
//        int h = (int)((yOff+s.t2)*yScale)-(int)((yOff+s.time)*yScale);
//        //System.out.println("Space["+s.left+","+s.right+"] out:"+x+","+y+","+w+","+h);
//        if (s.left < s.right) { // typcial case
//          g.fillRect(x,y,w,h);
//        } else { // wrapped case
//          // muck with x and w
//          // right side
//          int w2 = (int)((xOff+ConsRenderer.maxRingSpaceValue)*xScale) - (int)((xOff+s.left)*xScale);
//          g.fillRect(x,y,w2,h); 
//          
//          // left side
//          int x2 = (int)((xOff+0)*xScale);
//          int w3 = (int)((xOff+s.right)*xScale) - (int)((xOff+0)*xScale);
//          g.fillRect(x2,y,w3,h);
//        }
//      }
//    }    
//    // draw setReady() line
//    if (readyTime != 0) {
//      g.setColor(readyColor);
//      if (readyStart < readyEnd) { // typical case
//        int y = (int)((yOff+readyTime)*yScale);
//        int x1 = (int)((xOff+readyStart)*xScale);
//        int x2 = (int)((xOff+readyEnd)*xScale);
//        g.drawLine(x1, y, x2, y);
//      } else { // wrapped case
//        int y = (int)((yOff+readyTime)*yScale);
//        int r1 = (int)((xOff+readyStart)*xScale);
//        int r2 = (int)((xOff+ConsRenderer.maxRingSpaceValue)*xScale);
//        g.drawLine(r1, y, r2, y);         
//        
//        int l1 = (int)((xOff+0)*xScale);
//        int l2 = (int)((xOff+readyEnd)*xScale);
//        g.drawLine(l1, y, l2, y);         
//      }
//    }
  }
  
  public String toString() {
    return nodeName+":"+fileName+":"+ip+"ttr:"+timeToReady;
  }

  /**
   * @param absTime
   */
  public void move(long absTime, boolean allOnSameComputer, ConsRenderer cr) {
    long diff = absTime-t1;
    if (allOnSameComputer) {
      List<Node> l = cr.getNodesByFileName(fileName);
      for (Node n : l) {
        n.shift(diff, true, cr);
      }
    } else {
      shift(diff, true, cr);
    }
  }
  
  public void shift(long diff, boolean updateTable, ConsRenderer cr) {
    t1+=diff;
    t2+=diff;
    readyTime+=diff;
    primary.move(diff);
    secondary.move(diff);    
    if (updateTable) {
      Long l = cr.offsets.get(this.nodeName);
      if (l == null) {
        // add the value if it isn't already there
        cr.offsets.put(this.nodeName, new Long(diff));        
      } else {
        // update the value if it is already there
        cr.offsets.put(this.nodeName, new Long(diff+l.longValue()));
      }
    }
  }

  /**
   * @param b
   * @return
   */
  public Collection<Overlap> overlaps(Node that) {
    List<Overlap> l = new LinkedList<Overlap>();
    if (this.t1 > that.t2) return l;
    if (this.t2 < that.t1) return l;
    l.addAll(this.r1.overlaps(that.r1));
    l.addAll(this.r2.overlaps(that.r1));
    l.addAll(this.r1.overlaps(that.r2));
    l.addAll(this.r2.overlaps(that.r2));    
    if (l.size() > 0) {
      long minTime = Long.MAX_VALUE;
      long maxTime = 0;
      Iterator<Overlap> i = l.iterator();
      while(i.hasNext()) {
        Overlap o = (Overlap)i.next(); 
        if (o.t1 < minTime) minTime = o.t1;
        if (o.t2 > maxTime) maxTime = o.t2;
      }
      System.out.println("Found overlaps for "+this+" and "+that+ "from "+minTime+" to "+maxTime+" num:"+l.size()); 
    }
    return l;
  }

  /**
   * @return
   */
  public int getTimeToReady() {
    return timeToReady;
  }
  
}
