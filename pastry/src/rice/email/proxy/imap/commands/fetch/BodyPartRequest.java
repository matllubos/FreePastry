package rice.email.proxy.imap.commands.fetch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class BodyPartRequest {

  String _name;
  List _type = new ArrayList();
  boolean _peek = false;
  List _parts = new ArrayList();
  int _rangeStart = -1;
  int _rangeLength = -1;

  public String getName() {
    return _name;
  }

  public List getType() {
    return _type;
  }

  public boolean getPeek() {
    return _peek;
  }

  public void setName(String name) {
    _name = name;
  }

  public void appendType(String type) {
    _type.add(type);
  }

  public void reAppendType(String type) {
    _type.remove(_type.size() - 1);
    _type.add(type);
  }

  public void setPeek(boolean peek) {
    _peek = peek;
  }

  public void addPart(String part) {
    _parts.add(part);
  }

  public boolean hasRange() {
    return ((_rangeStart >= 0) && (_rangeLength > 0));
  }

  public int getRangeStart() {
    return _rangeStart;
  }

  public int getRangeLength() {
    return _rangeLength;
  }

  public void setRange(String start, String length) {
    try {
      _rangeStart = Integer.parseInt(start);
      _rangeLength = Integer.parseInt(length);
    } catch (NumberFormatException e) {
    }
  }
  
  public Iterator getPartIterator() {
    return _parts.iterator();
  }

  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append(_name);
    result.append("[");

    for (int i=0; i<_type.size(); i++) {
      result.append(_type.get(i) + "");

      if (i < _type.size()-1) {
        result.append(".");
      }
    }

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