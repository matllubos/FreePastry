/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
        result.append("\"" + i.next().toString().toUpperCase() + "\"");
        if (i.hasNext())
          result.append(' ');
      }

      result.append(")");
    }

    result.append("]");
    
    if (_rangeStart > -1)
      result.append("<" + _rangeStart + ">");

    return result.toString();
  }
}