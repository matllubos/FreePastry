package rice.p2p.util.testing;

import rice.p2p.util.*;
import java.io.*;
import java.util.*;

public class XMLParserUnit {
  
  public static void main(String[] argv) throws Exception {
    System.out.println("XMLParser Test Suite");
    System.out.println("-------------------------------------------------------------");
    System.out.println("  Running Tests");
    
    System.out.print("    Testing Simple (1)\t\t\t\t");
    
    XMLParser parser = new XMLParser();
    parser.setInput(new StringReader("<test></test>"));
    int i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();
      
      if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
        i = parser.next();
        
        if (i == XMLParser.END_DOCUMENT) {
          System.out.println("[ PASSED ]");
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(5):\t" + i);
        }
      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    System.out.print("    Testing Simple (2)\t\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test/>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();

      if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
        i = parser.next();
        
        if (i == XMLParser.END_DOCUMENT) {
          System.out.println("[ PASSED ]");
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i);
        } 
      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    System.out.print("    Testing Simple Attribute (1)\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test foo=bar/>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();
      
      if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
        i = parser.next();
        
        if (i == XMLParser.END_DOCUMENT) {
          System.out.println("[ PASSED ]");
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i);
        }
      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    System.out.print("    Testing Simple Attribute (2)\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test foo='bar'/>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();
      
      if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
        i = parser.next();
        
        if (i == XMLParser.END_DOCUMENT) {
          System.out.println("[ PASSED ]");
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i);
        }    
      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    System.out.print("    Testing Simple Attribute (3)\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test foo=\"bar\"/>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();
      
      if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
        i = parser.next();
        
        if (i == XMLParser.END_DOCUMENT) {
          System.out.println("[ PASSED ]");
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i);
        }      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    System.out.print("    Testing Simple Attribute (4)\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test foo=\"bar\"></test>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();
      
      if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
        i = parser.next();
        
        if (i == XMLParser.END_DOCUMENT) {
          System.out.println("[ PASSED ]");
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i);
        }      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    System.out.print("    Testing Simple Attribute (5)\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test foo=\"bar\" baz=blah goo=29.33   ></test>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();
      
      if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
        i = parser.next();
        
        if (i == XMLParser.END_DOCUMENT) {
          System.out.println("[ PASSED ]");
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i);
        }      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    
    System.out.print("    Testing Simple Attribute (6)\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test foo=\"bar\" baz=blah goo=29.33   ></test>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      
      if (parser.getAttributeValue(null, "foo").equals("bar") && 
          parser.getAttributeValue(null, "baz").equals("blah") && 
          parser.getAttributeValue(null, "goo").equals("29.33")) {
        i = parser.next();
        
        if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
          i = parser.next();
          
          if (i == XMLParser.END_DOCUMENT) {
            System.out.println("[ PASSED ]");
          } else {
            System.out.println("[ FAILED ]");
            System.out.println("    Output(4):\t" + i);
          }      
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i + " " + parser.getName());
        }
      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getAttributeValue(null, "foo").equals("bar") + " " + 
                           parser.getAttributeValue(null, "baz").equals("blah") + " " + 
                           parser.getAttributeValue(null, "goo").equals("29.33"));
      }  
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }    
    
    System.out.print("    Testing Recursive\t\t\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test foo=\"bar\" baz=blah goo=29.33   >\n\t<bar/>\t\t\t\n\t</test>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();
      
      if ((i == XMLParser.START_TAG) && (parser.getName().equals("bar"))) {
        i = parser.next();

        if ((i == XMLParser.END_TAG) && (parser.getName().equals("bar"))) {
          i = parser.next();
          
          if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
            i = parser.next();
            
            if (i == XMLParser.END_DOCUMENT) {
              System.out.println("[ PASSED ]");
            } else {
              System.out.println("[ FAILED ]");
              System.out.println("    Output(5):\t" + i);
            }
          } else {
            System.out.println("[ FAILED ]");
            System.out.println("    Output(4):\t" + i + " " + parser.getName());
          }
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i + " " + parser.getName());
        }
      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    
    System.out.print("    Testing Nasty\t\t\t\t\t");
    
    parser = new XMLParser();
    parser.setInput(new StringReader("<test foo=\"bar\" baz=   6 goo=  \t29.33   >\n\t<bar   lah\n=\n\n\ndofdo/>\t\t\t\n\t</test>"));
    i = parser.next();
    
    if ((i == XMLParser.START_TAG) && (parser.getName().equals("test"))) {
      i = parser.next();
      
      if ((i == XMLParser.START_TAG) && (parser.getName().equals("bar"))) {
        i = parser.next();
        
        if ((i == XMLParser.END_TAG) && (parser.getName().equals("bar"))) {
          i = parser.next();
          
          if ((i == XMLParser.END_TAG) && (parser.getName().equals("test"))) {
            i = parser.next();
            
            if (i == XMLParser.END_DOCUMENT) {
              System.out.println("[ PASSED ]");
            } else {
              System.out.println("[ FAILED ]");
              System.out.println("    Output(5):\t" + i);
            }
          } else {
            System.out.println("[ FAILED ]");
            System.out.println("    Output(4):\t" + i + " " + parser.getName());
          }
        } else {
          System.out.println("[ FAILED ]");
          System.out.println("    Output(3):\t" + i + " " + parser.getName());
        }
      } else {
        System.out.println("[ FAILED ]");
        System.out.println("    Output(2):\t" + i + " " + parser.getName());
      }
    } else {
      System.out.println("[ FAILED ]");
      System.out.println("    Output(1):\t" + i + " " + parser.getName());
    }
    
    
    System.out.println("-------------------------------------------------------------");
  }

}