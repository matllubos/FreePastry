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

