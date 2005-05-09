/*
 * Created on Apr 8, 2005
 */
package rice.testing.routeconsistent.viewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;

/**
 * @author Jeff Hoye
 */
public class ConsRenderer extends JPanel implements SquareConsumer, NodeConsumer {
  long startTime = Long.MAX_VALUE;
  long endTime = 0;
  
  HashSet selected = new HashSet();

  
  /**
   * String nodeName -> List of Square, ordered by time
   */
  Hashtable nodes = new Hashtable();
  
  /**
   * True if nodeList is stale.  In other words, a new 
   * nodeName has been added to the set, and therefore a 
   * new nodeList must be generated.
   */
  boolean needToGenerateNodeList = false;
  
  JeffReader reader = null;
  
  public static final int maxRingSpaceValue = Integer.valueOf("FFFFFF", 16).intValue();

  JLabel statusBar;
  
  HashSet leafSets = new HashSet();
  HashSet overlaps = new HashSet();
  
  public String selectedString = "";
  
  File lsdir;
  
  public ConsRenderer(JLabel statusBar, File lsdir) {
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
              moveSelected(d);
            } else {
              if (e.isShiftDown()) {
                toggleSelect(d); 
              } else {
                select(d,e.isControlDown());
              }
            }
            repaint();
          } else if (e.getButton() == MouseEvent.BUTTON3) {
            if (e.isAltDown() && e.isControlDown()) {
              Iterator i = nodes.values().iterator();
              while(i.hasNext()) {
                Node n = (Node)i.next(); 
                if (n.t1 < d.absTime) {
                  n.move(d.absTime); 
                }
              }  
              recalcBounds(null);
              return;
            }
            d1 = new Details(e.getX(), e.getY());
          } else if (e.getButton() == MouseEvent.BUTTON2) {
            System.out.println("removing");
            Details det = new Details(e.getX(), e.getY());
            ArrayList reverseList = new ArrayList(selected.size());
  //          reverseList.
            Iterator i = selected.iterator();
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
                recalcBounds(null);
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
  
  private void moveSelected(Details d) {
    Iterator i = selected.iterator();
    while(i.hasNext()) {
      Node n = (Node)i.next(); 
      n.move(d.absTime);            
    }
    recalcBounds(null);
  }
  
  private void selectNone() {
    selected.clear();    
  }
  
  private void toggleSelect(Details d) {
    List squares = getSquares(d.space, d.absTime);
    String s = d.toString();    
    Iterator i = squares.iterator();
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
    List squares = getSquares(d.space, d.absTime);
    String s = d.toString();
    selectNone();
    selectedString = " N:"+squares.size();
    Iterator i = squares.iterator();
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
      i = selected.iterator();
      while(i.hasNext()) {
        Node n = (Node)i.next(); 
        Object o = getLeafSet(n.nodeName, d.absTime);
        if (o != null) {
          leafSets.add(o);
          System.out.println(o);
        }
      }          
    }
  }
  
  private Object getLeafSet(String nodeName, long absTime) {
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
      ArrayList leftSide = new ArrayList();
      ArrayList rightSide = new ArrayList();
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
        Collection c = a.overlaps(b);
        if (c != null) {
          overlaps.addAll(c);
        }
      }      
    }
    System.out.println("Overlaps: "+overlaps.size());
  }
  
  class LeafSet {
    ArrayList left,right;
    String owner;
    long time;
    /**
     * @param leftSide
     * @param owner2
     * @param rightSide
     */
    public LeafSet(long time, ArrayList leftSide, String owner, ArrayList rightSide) {
      this.left = leftSide;
      this.right = rightSide;
      this.owner = owner;
      this.time = time;
    }    
    
    public String toString() {
      String ret = time+":";
      Iterator i = left.iterator();
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
      Iterator i = left.iterator();            
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
  public void recalcBounds(Vector removed) {
    synchronized(this) {
      startTime = Long.MAX_VALUE;      
      endTime = 0;
      Iterator i = nodes.values().iterator();
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
  public List getSquares(int space, long time, Collection nodes) {
    ArrayList ret = new ArrayList();
    
    Iterator i = nodes.iterator();
    while(i.hasNext()) {
      Node n = (Node)i.next();
      Square s = n.getSquare(space, time);
      if (s != null) {
        ret.add(new SuperSquare(n,s));
      }
    }
    Collections.sort(ret, new Comparator() {
      public boolean equals(Object arg0) {
        return false;
      }

      public int compare(Object a0, Object a1) {
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
//      return "<html>["+x+","+y+"] T:<font color=\"FF0000\">"+absTime+"</font> S:"+spaceString+"</html>";
      return "["+x+","+y+"] t:"+timeString+" T:"+absTime+" S:"+spaceString;
    }
  }
  
  /**
   * Set the reader so we can control input.
   * @param reader
   */
  private void setReader(JeffReader reader) {
    this.reader = reader;
  }
  
  int maxNumSquares = 100000;
  int numSquares = 0;
  int TIME_ADD = 1000;
  
  /**
   * How the reader tells us about new data.
   */
  public synchronized void addSquare(Square s) {
    if (numSquares >= maxNumSquares) return;
    //System.out.println(s);
    numSquares++;
    Node n = (Node)nodes.get(s.nodeName);  
    if (n == null) {
      n = new Node(s.nodeName, s.fileName); 
      nodes.put(s.nodeName,n);
    } else if (n.empty) {
      // replace crappy old node with the new good one
      Node n1 = new Node(s.nodeName, s.fileName); 
      n1.ip = n.ip;
      n = n1;
      nodes.put(s.nodeName,n);      
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

  double timeFactor = 1;
  double spaceFactor = 1;

  long renderStartTime = startTime;
  long renderEndTime = endTime;
  int renderStartSpace = 0;
  int renderEndSpace = maxRingSpaceValue;

  
  protected void zoomToGlobal() {
    System.out.println("zoomToGlobal()");
    renderStartTime = startTime;
    renderEndTime = endTime;
    renderStartSpace = 0;
    renderEndSpace = maxRingSpaceValue;
  }
  
  protected void zoom(Details d1, Details d2) {
    System.out.println("zoom("+d1+","+d2+")");
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
      Iterator i = nodes.keySet().iterator();
      while(i.hasNext()) {
        String nodeName = (String)i.next(); 
        Node n = (Node)nodes.get(nodeName);
        if (!selected.contains(n)) {
          n.renderSelf(g, spaceFactor, timeFactor, -renderStartSpace, -renderStartTime, size.width, size.height, false, prelifeRenderType);
        }
      }
      i = selected.iterator(); 
      while(i.hasNext()) {
        Node n = (Node)i.next(); 
        n.renderSelf(g, spaceFactor, timeFactor, -renderStartSpace, -renderStartTime, size.width, size.height, true, prelifeRenderType);
      }
      i = leafSets.iterator(); 
      while(i.hasNext()) {
        LeafSet l = (LeafSet)i.next(); 
        l.renderSelf(g, spaceFactor, timeFactor, -renderStartSpace, -renderStartTime, size.width, size.height, true);
      }
      if (renderOverlaps) {
        i = overlaps.iterator();
        while(i.hasNext()) {
          Overlap o = (Overlap)i.next(); 
          o.renderSelf(g, spaceFactor, timeFactor, -renderStartSpace, -renderStartTime, size.width, size.height);
        }
      }
    }
//    System.out.println("paintComponent end");
  }  
  
  public static void main(String[] args) throws Exception {
    JFrame frame = new JFrame("ConsRenderer");
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
    JMenuItem removeSelected = new JMenuItem("Remove Selected");
    editMenu.add(removeSelected);
    JMenuItem moveSelected = new JMenuItem("Move Selected");
    editMenu.add(moveSelected);

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
            Iterator i = cr.nodes.values().iterator();
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
            Iterator i = cr.nodes.values().iterator();
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
        Iterator i = cr.selected.iterator();
        while(i.hasNext()) {
          Node n = (Node)i.next();
          cr.nodes.remove(n.nodeName);
          i.remove();
        }
        cr.selectedString = "";
        cr.recalcBounds(null);
        cr.setText("");
        cr.repaint();
      }
    });
    
    moveSelected.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        cr.setClickState(MouseEvent.BUTTON1, new Clicker() {
          public void click(Details d) {
            cr.moveSelected(d);
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
    Iterator i = selected.iterator();
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
    Iterator i = nodes.values().iterator();
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

}
