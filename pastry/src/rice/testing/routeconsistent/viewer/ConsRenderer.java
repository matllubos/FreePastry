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
/*
 * Created on Apr 8, 2005
 */
package rice.testing.routeconsistent.viewer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.*;

/**
 * @author Jeff Hoye
 */
public class ConsRenderer extends JPanel implements SquareConsumer, NodeConsumer {
  long startTime = Long.MAX_VALUE;
  long endTime = 0;
  
  HashSet<Node> selected = new HashSet<Node>();

  
  /**
   * String nodeName -> List of Square, ordered by time
   */
  Hashtable<String, Node> nodes = new Hashtable<String, Node>();
  
  /**
   * True if nodeList is stale.  In other words, a new 
   * nodeName has been added to the set, and therefore a 
   * new nodeList must be generated.
   */
  boolean needToGenerateNodeList = false;
  
  JeffReader reader = null;
  
  public static final int maxRingSpaceValue = Integer.valueOf("FFFFFF", 16).intValue();

  JLabel statusBar;
  
  HashSet<LeafSet> leafSets = new HashSet<LeafSet>();
  HashSet<Overlap> overlaps = new HashSet<Overlap>();
  
  public String selectedString = "";
  
  File lsdir;
  
  public ConsRenderer(JLabel statusBar, File lsdir) throws Exception {
    this.lsdir = lsdir;
    this.statusBar = statusBar; 
//    System.out.println("maxRingSpaceValue = "+maxRingSpaceValue);
    this.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        setText(new Details(e.getX(), e.getY()).toString()+selectedString);
      }
    });
    
    this.addMouseListener(new MouseAdapter() {
      Details d1;
      
      public void mousePressed(MouseEvent e) {
        Details d = new Details(e.getX(), e.getY());
        if (clickState[e.getButton()] != null) {
          clickState[e.getButton()].click(d);
        } else {
          if (e.getButton() == MouseEvent.BUTTON1) {
            if (e.isAltDown()) {
              // move 
              moveSelected(d,e.isControlDown());
            } else {
              if (e.isShiftDown()) {
                toggleSelect(d); 
              } else {
                select(d,e.isControlDown());
              }
            }
            repaint();
          } else if (e.getButton() == MouseEvent.BUTTON3) {
            // don't know what this was for
            if (e.isAltDown() && e.isControlDown()) {
              Iterator<Node> i = nodes.values().iterator();
              while(i.hasNext()) {
                Node n = (Node)i.next(); 
                if (n.t1 < d.absTime) {
                  n.move(d.absTime,false,ConsRenderer.this); 
                }
              }  
              recalcBounds();
              return;
            }
            d1 = new Details(e.getX(), e.getY());
          } else if (e.getButton() == MouseEvent.BUTTON2) {
            System.out.println("removing");
            Details det = new Details(e.getX(), e.getY());
            ArrayList<Node> reverseList = new ArrayList<Node>(selected.size());
  //          reverseList.
            Iterator<Node> i = selected.iterator();
            while(i.hasNext()) {
              reverseList.add(i.next()); 
            }
            Collections.reverse(reverseList);
            i = reverseList.iterator();
            while(i.hasNext()) {
              Node n = (Node)i.next();
              Object o = n.getSquare(det.space, det.absTime);
              if (o != null) {
                selected.remove(n);
                nodes.remove(n.nodeName);
                delme.add(n.nodeName);
                recalcBounds();
                repaint();
                return;
              }
            }
          }
        }
      }
      public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON3) {
          Details d2 = new Details(e.getX(), e.getY());
          zoom(d1, d2);
        }
      }      
    });
  }
  
  private void moveSelected(Details d, boolean allOnSameComputer) {
    Iterator<Node> i = selected.iterator();
    while(i.hasNext()) {
      Node n = (Node)i.next(); 
      n.move(d.absTime, allOnSameComputer, ConsRenderer.this);            
    }
    recalcBounds();
  }
  
  private void selectNone() {
    selected.clear();    
  }
  
  private void toggleSelect(Details d) {
    List<SuperSquare> squares = getSquares(d.space, d.absTime);
    String s = d.toString();    
    Iterator<SuperSquare> i = squares.iterator();
    while(i.hasNext()) {
      SuperSquare ss = (SuperSquare)i.next(); 
      if (selected.contains(ss.node)) {
        selected.remove(ss.node); 
      } else {
        selected.add(ss.node);
      }
    }
    setSelectedLabel();
    leafSets.clear();    
  }
  
  private void select(Details d, boolean showLeafSet) {
    List<SuperSquare> squares = getSquares(d.space, d.absTime);
    String s = d.toString();
    selectNone();
    selectedString = " N:"+squares.size();
    Iterator<SuperSquare> i = squares.iterator();
    while(i.hasNext()) {
      SuperSquare ss = (SuperSquare)i.next(); 
      selectedString+=",<font color=\""+ss.node.htmlColor()+"\">\u2588</font>"+ss.toString();  
      selected.add(ss.node);
    }
        
    setText(s+selectedString);
    
    leafSets.clear();
    if (showLeafSet) {
      //System.out.println("finding leafsets");
      // build leafsets
      Iterator<Node> i2 = selected.iterator();
      while(i2.hasNext()) {
        Node n = (Node)i2.next(); 
        LeafSet o = getLeafSet(n.nodeName, d.absTime);
        if (o != null) {
          leafSets.add(o);
          System.out.println(o);
        }
      }          
    }
  }
  
  private LeafSet getLeafSet(String nodeName, long absTime) {
    try {
      //System.out.println("getLeafSet("+nodeName+")");
      String fname = "ls."+nodeName+".txt";
      File f = new File(lsdir, fname);
      // parse the file super fast
      InputStream is = new FileInputStream(f);
      BufferedReader r = new BufferedReader(new InputStreamReader(is));
      StreamTokenizer st = new StreamTokenizer(r);
      st.eolIsSignificant(true);
      
      long lastTime = 0;
      long time = 0;
      ArrayList<String> leftSide = new ArrayList<String>();
      ArrayList<String> rightSide = new ArrayList<String>();
      String owner = null;
      while (st.ttype != StreamTokenizer.TT_EOF) {
        lastTime = time;
        st.nextToken();
        time = (long)st.nval;
        if (time > absTime) {
          break; 
        }
        st.resetSyntax();
        
        //# All byte values 'A' through 'Z', 'a' through 'z', and '\u00A0' through '\u00FF' are considered to be alphabetic.
        st.eolIsSignificant(true);
        st.wordChars('A','Z');
        st.wordChars('a','z');
        st.wordChars('\u00A0','\u00FF');
        st.wordChars('0','9');
        st.wordChars('.','.');
        st.wordChars('-','-');    
        st.wordChars(':',':');    
        st.whitespaceChars('\u0000','\u0020');
        st.wordChars('[',']');    
        
        leftSide.clear();
        st.nextToken();
        while(st.sval == null) st.nextToken();
        while(!st.sval.equals("[")) {
          String s = st.sval; 
          leftSide.add(s);
          st.nextToken();
        }
        st.nextToken();
        owner = st.sval;
        
        st.nextToken(); // ]
        
        rightSide.clear();
        
        st.nextToken(); 
        while(st.ttype != StreamTokenizer.TT_EOL) {
          String s = st.sval; 
          rightSide.add(s);
          st.nextToken();
        }
        st.parseNumbers();
      }
          
      return new LeafSet(lastTime, leftSide, owner, rightSide);
    } catch (Exception e) {
      e.printStackTrace();
      return null; 
    }
  }
  
  public void findLiveOverlaps() {
    Node[] nodeList = (Node[])nodes.values().toArray(new Node[0]);
    overlaps.clear();
    for (int n1 = 0; n1 < nodeList.length-1; n1++) {
      Node a = nodeList[n1];
      for (int n2 = n1+1; n2 < nodeList.length; n2++) {
        Node b = nodeList[n2];
        Collection<Overlap> c = a.overlaps(b);
        if (c != null) {
          overlaps.addAll(c);
        }
      }      
    }
    System.out.println("Overlaps: "+overlaps.size());
  }
  
  class LeafSet {
    ArrayList<String> left,right;
    String owner;
    long time;
    /**
     * @param leftSide
     * @param owner2
     * @param rightSide
     */
    public LeafSet(long time, ArrayList<String> leftSide, String owner, ArrayList<String> rightSide) {
      this.left = leftSide;
      this.right = rightSide;
      this.owner = owner;
      this.time = time;
    }    
    
    public String toString() {
      String ret = time+":";
      Iterator<String> i = left.iterator();
      while(i.hasNext()) {
        ret+=" "+i.next(); 
      }
      ret+=" ["+owner+"]";
      i = right.iterator();
      while(i.hasNext()) {
        ret+=" "+i.next(); 
      }
      
      ret+="\n";
      if (left.size() > 0) {        
        String leftName = (String)left.get(left.size()-1);
        ret+=(Node)nodes.get(leftName);
      } else {
        ret+="null";
      }
      ret+="  ";
      if (right.size() > 0) {        
        String rightName = (String)right.get(0);
        ret+=(Node)nodes.get(rightName);
      } else {
        ret+="null";
      }
      return ret;
    }

    public int getIntVal(String node) {
      return Integer.valueOf(node.substring(2), 16).intValue();
    }
    
    public void renderSelf(Graphics g, double xScale, double yScale, int xOff, long yOff, int maxX, int maxY, boolean selected) {
      g.setColor(Color.BLACK);
      int y = (int)((yOff+time)*yScale);
      int ownerX = (int)((xOff+getIntVal(owner))*xScale);
      int firstX = ownerX;
      int lastX = ownerX;
      Iterator<String> i = left.iterator();            
      while(i.hasNext()) {
        String node = (String)i.next();
        int x = (int)((xOff+getIntVal(node))*xScale);        
        if (firstX == ownerX) {
          firstX = x; 
        }
        g.drawLine(x, y-10, x, y+10);
      }
      i = right.iterator();      
      while(i.hasNext()) {
        String node = (String)i.next();
        int x = (int)((xOff+getIntVal(node))*xScale);        
        lastX = x;
        g.drawLine(x, y-10, x, y+10);
      }
      // render horizontal line
      if (firstX < lastX) { // typical case
        g.drawLine(firstX, y, lastX, y);
      } else { // wrapped case
        // right side
        g.drawLine(firstX, y, maxX, y);        
        g.drawLine(0, y, lastX, y);        
      }
      // render owner
      g.setColor(Color.RED);
      int x = ownerX;
      g.drawLine(x, y-10, x, y+10);
    }
    
  }
  
  // recalculate endTime, fristTime based on the nodes that were removed
  public void recalcBounds() {
    synchronized(this) {
      startTime = Long.MAX_VALUE;      
      endTime = 0;
      Iterator<Node> i = nodes.values().iterator();
      while(i.hasNext()) {
        Node n = (Node)i.next();
        if (n.t1 < startTime) startTime = n.t1; 
        if (n.t2 > endTime) endTime = n.t2; 
      }
    }
  }
  
  public void setText(String s) {
    ConsRenderer.this.statusBar.setText("<html>"+s+"</html>");
  }
  
    
  public List getSquares(int space, long time) {
    return getSquares(space, time, nodes.values());
  }
  
  /**
   * The idea being that we can eventually break down into areas which area has each node.  For better
   * performance.
   * @param space
   * @param time
   * @param nodes
   * @return
   */
  public List<SuperSquare> getSquares(int space, long time, Collection<Node> nodes) {
    ArrayList<SuperSquare> ret = new ArrayList<SuperSquare>();
    
    Iterator<Node> i = nodes.iterator();
    while(i.hasNext()) {
      Node n = (Node)i.next();
      Square s = n.getSquare(space, time);
      if (s != null) {
        ret.add(new SuperSquare(n,s));
      }
    }
    Collections.sort(ret, new Comparator<SuperSquare>() {
      public boolean equals(Object arg0) {
        return false;
      }

      public int compare(SuperSquare a0, SuperSquare a1) {
        SuperSquare s1 = (SuperSquare)a0;
        SuperSquare s2 = (SuperSquare)a1;
        
        return s1.node.nodeName.compareTo(s2.node.nodeName);
      }
    });
    return ret;
  }
  
  class SuperSquare {
    public Node node;
    public Square square;
    public SuperSquare(Node n, Square s) {
      node = n;
      square = s;
    }
    
    public String toString() {
      String alive = "ready";
      if ((square.type == 4) || (square.type == 5)) {
        alive = "notready";
      }
      return alive+" "+node.nodeName+"@"+node.fileName+":"+square.lineNum+"["+square.getLeft()+"-"+square.getRight()+"]";
    }
  }
  
  class Details {
    int x,y;
    int space;
    long time;
    String spaceString;
    int millis;
    int seconds;
    int mins;
    int hours;
    String timeString;
    long absTime;
    
    public Details(int x, int y) {
      this.x = x;
      this.y = y;
      space = (int)(x/spaceFactor)+renderStartSpace;
      time = (int)(y/timeFactor)+renderStartTime-startTime;
      absTime = time+startTime;
      spaceString = Integer.toString(space,16).toUpperCase();
      millis = (int)(time%1000);
      seconds = (int)((time/1000)%60);
      mins = (int)((time/60000)%60);
      hours = (int)((time/(60*60*1000))%24);
      timeString = hours+":"+mins+":"+seconds+":"+millis;
    }
    
    public String toString() {
      long now = absTime;
      now/=1000; 
      now%=86400; //1 day's seconds
      now/=3600; // hour 0-23
      now/=2; // 0-11;
      now*=2; // 0-22 by 2
      

//      return "<html>["+x+","+y+"] T:<font color=\"FF0000\">"+absTime+"</font> S:"+spaceString+"</html>";
      return now+"["+x+","+y+"] t:"+timeString+" T:"+absTime+" S:"+spaceString;
    }
  }
  
  /**
   * Set the reader so we can control input.
   * @param reader
   */
  private void setReader(JeffReader reader) {
    this.reader = reader;
  }
  
  int maxNumSquares = 300000;
  int numSquares = 0;
  int TIME_ADD = 1000;
  
  /**
   * How the reader tells us about new data.
   */
  public synchronized void addSquare(Square s) {
    if (numSquares >= maxNumSquares) return;
    
//    if (s.nodeName.equals("0x5E008C"))
//    if ((numSquares > 166891) && (numSquares < 166956))
//      System.out.println(s);
    numSquares++;
    Node n = (Node)nodes.get(s.nodeName);  
    if (n == null) {
      try {
        n = new Node(s.nodeName, s.fileName); 
      } catch (NumberFormatException nfe) {
        System.out.println("NodeName: "+s.nodeName+" "+s);
        throw nfe;
      }
      nodes.put(s.nodeName,n);
      addToNodesByFileName(n);
    } else if (n.empty) {
      // replace old node with the new good one
      Node n1 = new Node(s.nodeName, s.fileName); 
      n1.ip = n.ip;
      n = n1;
      nodes.put(s.nodeName,n);      
      addToNodesByFileName(n);
    }
    n.addSquare(s);
    if (s.time < startTime) startTime = s.time; 
    if (s.time > endTime) endTime = s.time; 
  }
  
  public synchronized void addNode(Node s) {
    if (s == null) {
      return; 
    }
    Node n = (Node)nodes.get(s.nodeName);  
    if (n == null) {
      // if there's not one, add our hollow one
      nodes.put(s.nodeName,s);
    } else if (!n.empty) {
      // there is a non-hollow one, so just set the ip
      n.ip = s.ip;
    }
  }

  HashMap<String, List<Node>> nodesByFile = new HashMap<String, List<Node>>();
  
  private void addToNodesByFileName(Node s) {
//    System.out.println("addToNodesByFileName("+s.fileName+")");
    List<Node> l = nodesByFile.get(s.fileName);
    if (l == null) {
      l = new ArrayList<Node>();
      nodesByFile.put(s.fileName,l);
    }
    l.add(s);    
  }
  
  public List<Node> getNodesByFileName(String s) {
    System.out.println("getNodesByFileName("+s+")");
    return nodesByFile.get(s);
  }

  double timeFactor = 1;
  double spaceFactor = 1;

  long renderStartTime = startTime;
  long renderEndTime = endTime;
  int renderStartSpace = 0;
  int renderEndSpace = maxRingSpaceValue;

  
  protected void zoomToGlobal() {
//    System.out.println("zoomToGlobal()");
    renderStartTime = startTime;
    renderEndTime = endTime;
    renderStartSpace = 0;
    renderEndSpace = maxRingSpaceValue;
  }
  
  protected void zoom(Details d1, Details d2) {
//    System.out.println("zoom("+d1+","+d2+")");
    if ((d1.time == d2.time) || (d1.space == d2.space)) {
      zoomToGlobal(); 
    } else {
      renderStartTime = d1.absTime;
      renderEndTime = d1.absTime;
      renderStartSpace = d1.space;
      renderEndSpace = d1.space;
      if (d2.absTime < renderStartTime) renderStartTime = d2.absTime;
      if (d2.absTime > renderEndTime) renderEndTime = d2.absTime;
      if (d2.space < renderStartSpace) renderStartSpace = d2.space;
      if (d2.space > renderEndSpace) renderEndSpace = d2.space;
    }
    
    repaint();
  }
  
  boolean firstTime = true;
  protected void paintComponent(Graphics gr) {
//    System.out.println("paintComponent begin");
    if (firstTime) zoomToGlobal();
    firstTime = false;
    
    super.paintComponent(gr);
    Dimension size = getSize();

    Graphics2D g = (Graphics2D)gr;
    g.setPaint(Color.WHITE);
    g.fillRect(0,0,size.width,size.height);
    
//    g.setPaint(new Color(200,0,0));
//    g.fillRect(10, 10, 100, 100);
    //System.out.println(size);
    timeFactor = (double)size.height/(renderEndTime-renderStartTime);
//    timeFactor*=3;
    spaceFactor = (double)size.width/(renderEndSpace-renderStartSpace);
    
    
    // paint nodes
    synchronized(this) {
//      System.out.println("paintComponent.synch");
//      System.out.println("Time:["+startTime+","+endTime+"]F:"+timeFactor+" Space:["+0+","+maxRingSpaceValue+"]F:"+spaceFactor+" NumNodes:"+nodes.size());
      Iterator<String> i = nodes.keySet().iterator();
      while(i.hasNext()) {
        String nodeName = (String)i.next(); 
        Node n = (Node)nodes.get(nodeName);
        if (!selected.contains(n)) {
          n.renderSelf(g, spaceFactor, timeFactor, -renderStartSpace, -renderStartTime, size.width, size.height, false, prelifeRenderType);
        }
      }
      Iterator<Node> i2 = selected.iterator(); 
      while(i2.hasNext()) {
        Node n = (Node)i2.next(); 
        n.renderSelf(g, spaceFactor, timeFactor, -renderStartSpace, -renderStartTime, size.width, size.height, true, prelifeRenderType);
      }
      Iterator<LeafSet> i3 = leafSets.iterator(); 
      while(i3.hasNext()) {
        LeafSet l = (LeafSet)i3.next(); 
        l.renderSelf(g, spaceFactor, timeFactor, -renderStartSpace, -renderStartTime, size.width, size.height, true);
      }
      if (renderOverlaps) {
        Iterator<Overlap> i4 = overlaps.iterator();
        while(i4.hasNext()) {
          Overlap o = (Overlap)i4.next(); 
          o.renderSelf(g, spaceFactor, timeFactor, -renderStartSpace, -renderStartTime, size.width, size.height);
        }
      }
    }
//    System.out.println("paintComponent end");
  }  
  
  static String ourNodeName = "/de/mpi-sws/ConsRenderer";
  
  static class ReturnValue {
    boolean done = false;
    int val = 0;
    String dir = null;
  }
  
  static class DoubleComponent extends JPanel {
    public DoubleComponent(Component a, Component b) {
      super();
      setLayout(new FlowLayout(FlowLayout.CENTER));
      add(a);
      add(b);
    }
    
    public DoubleComponent(String label, Component component) {
      this(new JLabel(label), component);
    }
  }
  
  public static void center(JFrame frame) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    Point center = ge.getCenterPoint();
    Rectangle bounds = ge.getMaximumWindowBounds();
    int w = Math.max(bounds.width/2, Math.min(frame.getWidth(), bounds.width));
    int h = Math.max(bounds.height/2, Math.min(frame.getHeight(), bounds.height));
    int x = center.x - w/2, y = center.y - h/2;
    frame.setBounds(x, y, w, h);
    if (w == bounds.width && h == bounds.height)
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    frame.validate();
  }

  
  public static void main(String[] args) throws Exception {

    String dir = "n:/planetlab/cons";
//    String dir = "c:/planetlab/cons";
    
    if (args.length <= 1) {
      String number = "1";
      if (args.length == 1) {
        number = args[0];
      } else {
        System.out.println("opening dialog");
//        number = JOptionPane.showInputDialog(new JFrame(), "Enter the run number", "1"); 
        final Preferences prefs = Preferences.userRoot().node(ourNodeName);
        dir = prefs.get("dirname","n:/planetlab/cons");
        int num = prefs.getInt("runnum",1);
   
        final ReturnValue ret = new ReturnValue();
        
        JFrame option = new JFrame();
        option.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container container = option.getContentPane();
        container.setLayout(new GridLayout(3,1));
        
        String[] dirOptions = {
            "m:/unix-home/planetlab/cons",
            "c:/planetlab/cons",
        };
        final JComboBox dirChooser = new JComboBox(dirOptions);
        dirChooser.setEditable(true);
        dirChooser.setSelectedItem(dir);       
        container.add(new DoubleComponent("Directory:",dirChooser));

        Format integerFormat = NumberFormat.getIntegerInstance();
        final JFormattedTextField integerChooser = new JFormattedTextField(integerFormat);
        integerChooser.setColumns(9);
        integerChooser.setValue(num);
        
        container.add(new DoubleComponent("Val:",integerChooser));
        
        JButton ok = new JButton("ok");
        JButton cancel = new JButton("cancel");
        
        cancel.addActionListener(new ActionListener() {
        
          public void actionPerformed(ActionEvent e) {
            System.exit(0);
          }        
        });
        
        ok.addActionListener(new ActionListener() {
        
          public void actionPerformed(ActionEvent e) {
            synchronized(ret) {
              ret.dir = (String)dirChooser.getSelectedItem();
              ret.val = new Integer(integerChooser.getValue().toString()).intValue();
              prefs.put("dirname",ret.dir);
              prefs.putInt("runnum",ret.val);
              ret.done = true;
              ret.notifyAll();
            }                    
          }        
        });
        
        container.add(new DoubleComponent(ok, cancel));
        

        // wait until done
        option.pack();
        option.setLocationRelativeTo( null );
//        center(option);
        option.setVisible(true);
        synchronized(ret) {
          while(!ret.done) {
            ret.wait();
          }
        }
        option.dispose();
        dir = ret.dir;
        number = ""+ret.val;
        
        System.out.println("dir:"+dir+" number:"+number);
      }
      args = new String[3];
      // m:/planetlab/plcons/cons1/viz m:/planetlab/plcons/cons1/node_index.txt m:/planetlab/plcons/cons1/
      args[0] = dir+number+"/viz";
      args[1] = dir+number+"/node_index.txt";
      args[2] = dir+number+"/";
    }
    
    final JFrame frame = new JFrame("ConsRenderer");
    JMenuBar menuBar = new JMenuBar();
    JMenu preLifeMenu = new JMenu("preReady");
    menuBar.add(preLifeMenu);
    ButtonGroup group = new ButtonGroup();    
    JRadioButtonMenuItem renderNormal = new JRadioButtonMenuItem("render normal");
    group.add(renderNormal);
    preLifeMenu.add(renderNormal);    
    JRadioButtonMenuItem renderLite = new JRadioButtonMenuItem("render lite");
    group.add(renderLite);
    preLifeMenu.add(renderLite);
    JRadioButtonMenuItem renderNot = new JRadioButtonMenuItem("don't render");
    group.add(renderNot);
    preLifeMenu.add(renderNot);
    frame.setJMenuBar(menuBar);
    renderNormal.setSelected(true);
    
    JMenu viewMenu = new JMenu("view");
    menuBar.add(viewMenu);
    JMenuItem zoomOutItem = new JMenuItem("Zoom out (right click)");
    viewMenu.add(zoomOutItem);
    
    JMenu selectMenu = new JMenu("select");
    menuBar.add(selectMenu);    
    JMenuItem selectNone = new JMenuItem("Select none");
    selectMenu.add(selectNone);
    JMenuItem selectBeforeItem = new JMenuItem("Select all nodes before");
    selectMenu.add(selectBeforeItem);
    JMenuItem selectAfterItem = new JMenuItem("Select all nodes after");
    selectMenu.add(selectAfterItem);
    
    JMenu editMenu = new JMenu("edit");
    menuBar.add(editMenu);
    JMenuItem removeSelected = new JMenuItem("Remove Selected (mmb)");
    editMenu.add(removeSelected);
    JMenuItem moveSelected = new JMenuItem("Move Selected (alt lmb)");
    editMenu.add(moveSelected);

    JMenuItem moveSelectedAll = new JMenuItem("Move Selected (all nodes on computer) (ctl alt lmb)");
    editMenu.add(moveSelectedAll);

    
    JMenu searchMenu = new JMenu("search");
    menuBar.add(searchMenu);
    JMenuItem liveOverlaps = new JMenuItem("Live Overlaps");
    searchMenu.add(liveOverlaps);
    final JCheckBoxMenuItem showOverlaps = new JCheckBoxMenuItem("Show Overlaps");
    searchMenu.add(showOverlaps);
    JMenuItem selectLongestToReady = new JMenuItem("Select longest to be ready");
    searchMenu.add(selectLongestToReady);
    
    
    JLabel statusBar = new JLabel("status");
    File lsdir = null;
    if (args.length > 2) {
      lsdir = new File(args[2]);
    }
    final ConsRenderer cr = new ConsRenderer(statusBar, lsdir);
    JeffReader reader = new JeffReader(cr);
    JeffReader2 indexReader = new JeffReader2(cr);
    cr.setReader(reader);
    frame.getContentPane().setLayout(new BorderLayout());
    frame.getContentPane().add(cr,BorderLayout.CENTER);
    frame.getContentPane().add(statusBar, BorderLayout.SOUTH);
    statusBar.setPreferredSize(new Dimension(1000,75));
    frame.setSize(1000, 1000);
    frame.setVisible(true);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
    
      @Override
      public void windowClosing(WindowEvent e) {
        super.windowClosing(e);
        int ret = JOptionPane.showConfirmDialog(frame,"Save?","Save?",JOptionPane.YES_NO_OPTION);
        if (ret == JOptionPane.YES_OPTION) {
          try {
            cr.saveOffsets(); 
          } catch (Exception ex) {
             ex.printStackTrace();
          }
        }
      }    
    });
    
    if (args.length > 0) {
      File f = new File(args[0]);
      reader.read(f);
    }
    if (args.length > 1) {
      File f = new File(args[1]);
      indexReader.read(f);
    }
    renderNormal.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.renderNormal();
      }
    });
    renderLite.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.renderLite();
      }
    });
    renderNot.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.renderNot();
      }
    });
    
    zoomOutItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.zoomToGlobal();
        cr.repaint();
      }
    });
    
    selectBeforeItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.setClickState(MouseEvent.BUTTON1, new Clicker() {
          public void click(Details d) {
            cr.selectNone();
            String s = d.toString();
            cr.selectedString = "";
            Iterator<Node> i = cr.nodes.values().iterator();
            while(i.hasNext()) {              
              Node n = (Node)i.next();
              if (n.t1 <= d.absTime) {
                cr.selectedString+=",<font color=\""+n.htmlColor()+"\">\u2588</font>"+n.toString();  
                cr.selected.add(n);                
              }
            }            
            cr.setClickState(MouseEvent.BUTTON1, null);
            cr.setText(s+cr.selectedString);
            cr.repaint();
          }
        });
      }
    });
    
    selectAfterItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.setClickState(MouseEvent.BUTTON1, new Clicker() {
          public void click(Details d) {
            cr.selectNone();
            String s = d.toString();
            cr.selectedString = "";
            Iterator<Node> i = cr.nodes.values().iterator();
            while(i.hasNext()) {              
              Node n = (Node)i.next();
              if (n.t2 >= d.absTime) {
                cr.selectedString+=",<font color=\""+n.htmlColor()+"\">\u2588</font>"+n.toString();  
                cr.selected.add(n);                
              }
            }            
            cr.setClickState(MouseEvent.BUTTON1, null);            
            cr.setText(s+cr.selectedString);
            cr.repaint();
          }
        });
      }
    });
    
    selectNone.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.selectNone();
        cr.repaint();
      }
    });
    
    removeSelected.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        Iterator<Node> i = cr.selected.iterator();
        while(i.hasNext()) {
          Node n = (Node)i.next();
          cr.nodes.remove(n.nodeName);
          cr.delme.add(n.nodeName);
          i.remove();
        }
        cr.selectedString = "";
        cr.recalcBounds();
        cr.setText("");
        cr.repaint();
      }
    });
    
    moveSelected.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.setClickState(MouseEvent.BUTTON1, new Clicker() {
          public void click(Details d) {
            cr.moveSelected(d, false);
            cr.setClickState(MouseEvent.BUTTON1, null);
            cr.repaint();
          }
        });
      }
    });
    
    moveSelectedAll.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.setClickState(MouseEvent.BUTTON1, new Clicker() {
          public void click(Details d) {
            cr.moveSelected(d, true);
            cr.setClickState(MouseEvent.BUTTON1, null);
            cr.repaint();
          }
        });
      }
    });
    
    liveOverlaps.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.findLiveOverlaps();
        showOverlaps.setSelected(true);
        cr.renderOverlaps(showOverlaps.isSelected());
        cr.repaint();
      }
    });
    
    showOverlaps.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        //System.out.println("here");
        cr.renderOverlaps(showOverlaps.isSelected());
        cr.repaint();
      }
    });
    
    selectLongestToReady.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.selectNone();
        Node n = cr.getLongestToBeReady();
        cr.select(n);
        cr.setSelectedLabel();
        cr.repaint();
      }
    });
    
  }

  HashMap<String,Long> offsets = new HashMap<String,Long>();
  List<String> delme = new ArrayList<String>();
  
  protected void saveOffsets() throws FileNotFoundException, IOException {
    for(String s: delme) {
      offsets.remove(s); 
    }
    File f = new File(lsdir,"offsets");
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
    oos.writeObject(delme);
    oos.writeObject(offsets);
    oos.close();
  }

  protected void loadOffsets() {
    File f = new File(lsdir,"offsets");
    if (f.exists()) {
      try {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
        delme = (List<String>)ois.readObject();
        offsets = (HashMap<String,Long>)ois.readObject();
        ois.close();
        
        for(String s:delme) {
          nodes.remove(s);
          recalcBounds();
        }
        for(String s:offsets.keySet()) {
          Node n = (Node)nodes.get(s);
          Long l = offsets.get(s);
//          try {
            n.shift(l.longValue(), false, this);
//          } catch (NullPointerException npe) {
//            System.err.println(s+":"+n);
//            npe.printStackTrace(); 
//          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        f.delete(); 
      }
    }
  }

  /**
   * Adds n to the selected set.
   * @param n
   */
  protected void select(Node n) {
    if (n == null) return;
    selected.add(n);

  }
  
  protected void setSelectedLabel() {
    selectedString = " N:"+selected.size();
    Iterator<Node> i = selected.iterator();
    while(i.hasNext()) {
      Node n = (Node)i.next(); 
      selectedString+=",<font color=\""+n.htmlColor()+"\">\u2588</font>"+n.toString();  
      selected.add(n);
    }
    setText(selectedString);    
  }

  /**
   * 
   */
  protected Node getLongestToBeReady() {
    Iterator<Node> i = nodes.values().iterator();
    Node longest = (Node)i.next();
    while(i.hasNext()) {
      Node n = (Node)i.next();
      if (longest.getTimeToReady() < n.getTimeToReady()) {
        longest = n; 
      }
    }
    return longest;    
  }

  boolean renderOverlaps = false;
  /**
   * @param b
   */
  protected void renderOverlaps(boolean b) {
    renderOverlaps = b;
  }

  Clicker[] clickState = new Clicker[4]; // 3 buttons, indexed starting at 1
  
  /**
   * @param button1
   * @param clicker
   */
  protected void setClickState(int button, Clicker clicker) {
    clickState[button] = clicker;
  }

  public interface Clicker {
    public void click(Details d); 
  }
  
  public static final int PL_RENDER_NORMAL = 0;
  public static final int PL_RENDER_LITE = 1;
  public static final int PL_RENDER_NOT = 2;
  int prelifeRenderType = PL_RENDER_NORMAL;

  protected void renderNormal() {
    prelifeRenderType = PL_RENDER_NORMAL;
    repaint();
  }

  protected void renderLite() {
    prelifeRenderType = PL_RENDER_LITE;
    repaint();
  }

  protected void renderNot() {
    prelifeRenderType = PL_RENDER_NOT;
    repaint();
  }

  public void done() {
    try {
      loadOffsets();    
    } catch (Exception e) {
      e.printStackTrace(); 
    }
  }

}
