package rice.visualization.data;

import java.awt.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import rice.p2p.commonapi.*;

public abstract class DataView implements Serializable {
  
  protected String name;
  
  protected int width;
  
  protected int height;
    
  protected GridBagConstraints constraints;
  
  protected Data data;
  
  public DataView(String name, int width, int height, GridBagConstraints constraints) {
    this.name = name;
    this.width = width;
    this.height = height;
    this.constraints = constraints;
  }
  
  public void setData(Data data) {
    this.data = data;
    
    data.addView(name, this);
  }
  
  public String getName() {
    return name;
  }
  
  public int getWidth() {
    return width;
  }
  
  public int getHeight() {
    return height;
  }
  
  public GridBagConstraints getConstraints() {
    return constraints;
  }
  
}