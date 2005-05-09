/*
 * Created on Apr 20, 2005
 */
package rice.testing.routeconsistent.viewer;

import java.awt.Color;
import java.awt.Graphics;


class Overlap {
  long t1,t2;
  int left,right;
  
  Color color = Color.RED;
  
  public void renderSelf(Graphics g, double xScale, double yScale, int xOff, long yOff, int maxX, int maxY) {
    
    g.setColor(color); 
    
    int x = (int)((xOff+left)*xScale);
    int w = (int)((xOff+right)*xScale) - (int)((xOff+left)*xScale);
    int y = (int)((yOff+t1)*yScale);
    int h = (int)((yOff+t2)*yScale)-(int)((yOff+t1)*yScale);
    //System.out.println("Overlap["+left+","+right+"] out:"+x+","+y+","+w+","+h);
    g.fillRect(x,y,w,h);

  }
}