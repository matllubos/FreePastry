package rice.email.proxy.imap.commands.fetch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class RFC822PartRequest {

  String _name;
  String _type = "";
  
  public String getName() {
    return _name;
  }

  public String getType() {
    return _type;
  }

  public void setName(String name) {
    _name = name;
  }

  public void setType(String type) {
    _type = type;
  }

  public String toString()
  {
    StringBuffer result = new StringBuffer();
    result.append(_name);

    if (! _type.equals("")) {
      result.append("." + _type);
    }
    
    return result.toString();
  }
}