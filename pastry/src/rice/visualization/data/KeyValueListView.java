package rice.visualization.data;

import java.util.*;

import rice.p2p.commonapi.*;

public class KeyValueListView extends DataView {
  
  protected Properties properties;
  
  public KeyValueListView(String name, int width, int height, Constraints constraints) {
    super(name, width, height, constraints);
    
    this.properties = new Properties();
  }
    
  public void add(String key, String value) {
    properties.setProperty(key, value);
  }
  
  public String getValue(String key) {
    return properties.getProperty(key);
  }
  
  public Enumeration getKeyNames() {
    return properties.propertyNames();
  }
  
}
  
  