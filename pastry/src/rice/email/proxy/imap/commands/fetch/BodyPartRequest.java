package rice.email.proxy.imap.commands.fetch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class BodyPartRequest {

  String _name;
  String _type = "";
  boolean _peek = false;
  List _parts = new ArrayList();

  public String getName() {
    return _name;
  }

  public String getType() {
    return _type;
  }

  public boolean getPeek() {
    return _peek;
  }

  public void setName(String name) {
    _name = name;
  }

  public void setType(String type) {
    _type = type;
  }

  public void setPeek(boolean peek) {
    _peek = peek;
  }

  public void addPart(String part) {
    _parts.add(part);
  }

  public Iterator getPartIterator() {
    return _parts.iterator();
  }

  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append(_name);
    result.append("[");
    result.append(_type);

    if ((_parts != null) && (_parts.iterator().hasNext()))
    {
      result.append(" (");

      for (Iterator i = getPartIterator(); i.hasNext();)
      {
        result.append("\"" + i.next() + "\"");
        if (i.hasNext())
          result.append(' ');
      }

      result.append(")");
    }

    result.append("]");

    return result.toString();
  }
}