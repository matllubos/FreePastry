package rice.visualization.data;

import java.awt.*;
import java.io.*;

public class Color implements Serializable {

  public static final Color blue = new Color(0);
  public static final Color red = new Color(1);
  public static final Color yellow = new Color(2);
  public static final Color green = new Color(3);
  public static final Color lightGray = new Color(4);
  public static final Color orange = new Color(5);
  public static final Color pink = new Color(6);
  public static final Color darkGray = new Color(7);
  public static final Color white = new Color(8);
  public static final Color black = new Color(9);
  public static final Color magenta = new Color(10);
  public static final Color cyan = new Color(11);
  public static final Color gray = new Color(12);

  private int color;
  
  private Color(int color) {
    this.color = color;
  }
  
  public boolean equals(Object o) {
    return ((Color) o).color == color;
  }
  
  public java.awt.Color trans() {
    if (this.equals(blue))
      return java.awt.Color.blue;
    else if (this.equals(red))
      return java.awt.Color.red;
    else if (this.equals(yellow))
      return java.awt.Color.yellow;
    else if (this.equals(green))
      return java.awt.Color.green;
    else if (this.equals(lightGray))
      return java.awt.Color.lightGray;
    else if (this.equals(orange))
      return java.awt.Color.orange;
    else if (this.equals(pink))
      return java.awt.Color.pink;
    else if (this.equals(darkGray))
      return java.awt.Color.darkGray;
    else if (this.equals(white))
      return java.awt.Color.white;
    else if (this.equals(black))
      return java.awt.Color.black;
    else if (this.equals(magenta))
      return java.awt.Color.magenta;
    else if (this.equals(cyan))
      return java.awt.Color.cyan;
    else if (this.equals(gray))
      return java.awt.Color.gray;
    
    return java.awt.Color.red;
  }
}