package rice.visualization.data;

import java.awt.*;
import java.io.*;

public class Constraints implements Serializable {
 
  public static final int HORIZONTAL = 2;
  public static final int VERTICAL = 2;
  
  public int gridx;
  public int gridy;
  public int fill;
  
  public GridBagConstraints trans() {
    GridBagConstraints result = new GridBagConstraints();
    result.gridx = gridx;
    result.gridy = gridy;
    result.fill = fill;
    
    return result;
  }
  
}