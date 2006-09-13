/*
 * Created on Sep 13, 2006
 */
package rice.visualization.server;

import java.util.HashMap;

public class MessageNamingService {

  HashMap<Integer, String> appNames;
  HashMap<Integer,HashMap<Short,String>> typeTable;
  
  public MessageNamingService() {
    appNames = new HashMap<Integer, String>();
    typeTable = new HashMap<Integer,HashMap<Short,String>>();
  }
  
  public String addAppName(int address, String name) {
    appNames.put(address, name);
    return name;
  }
  
  public String addMessageType(int address, short type, String name) {
    if (!typeTable.containsKey(address)) {
      typeTable.put(address,new HashMap<Short,String>());
    }    
    typeTable.get(address).put(type,name);
    return name;
  }
  
  public String getMessageName(int address, short type) {    
    String appName;
    if (appNames.containsKey(address)) {
      appName = appNames.get(address);
    } else {
      appName = addAppName(address, Integer.toString(address));
    }
    
    String typeName;
    if (typeTable.containsKey(address)) {
      HashMap<Short,String> subTable = typeTable.get(address);
      if (subTable.containsKey(type)) {
        typeName = subTable.get(type);
      } else {
        typeName = addMessageType(address, type, Short.toString(type));        
      }      
    } else {
      typeName = addMessageType(address, type, Short.toString(type));        
    }    
    
    return appName+":"+typeName;
  }
  
}
