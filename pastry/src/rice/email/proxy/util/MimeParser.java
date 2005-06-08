package rice.email.proxy.util;

import java.io.*;
import java.util.*;

/** 
 * originally from http://www.nsftools.com/tips/MimeParser.java 
 */
public class MimeParser {
  
  /**
   * static flags reporting the type of event just hit
   */
  public static final int START_DOCUMENT = 1;
  public static final int START_HEADERS_PART = 3;
  public static final int START_MULTIPART = 5;
  public static final int END_MULTIPART = 6;
  public static final int END_DOCUMENT = 2;
  public static final int SINGLE_PART = 7;
  
  /**
   * The initial size of the buffer to use
   */
  public static final int BUFFER_SIZE = 1024;
  
  /**
   * Where we are reading the data from
   */
	protected InputStream in;
  
  /**
   * The current event
   */
  protected int event;
  
  /**
   * The internal buffer used to store the parsed data
   */
  protected byte[] buffer;
  protected int bufferLength;

  /**
   * The parsed header, part, and boundary data, if it exists
   */
  protected byte[] header;
  protected byte[] part;
  protected byte[] boundary;
  
  /**
   * The stack of all boundaries
   */
  protected Stack boundaries;
    
	/**
	 * The sole constructor for this class, which takes any kind
	 * of InputStream as a parameter. 
	 *
	 * @param    inStream    an InputStream that contains a Multi-part MIME message
	 */
	public MimeParser(InputStream inStream) {
		this.in = new BufferedInputStream(inStream, 4096);
    this.event = START_DOCUMENT;
    this.boundaries = new Stack();
    this.bufferLength = 0;
    this.buffer = new byte[BUFFER_SIZE];
	}  
  
	/**
   * ----- PUBLIC METHODS -----
   */
  
  /**
   * Returns the type of the current event
   *
   * @return The type of the current event
   */
  public int getEventType() {
    return event;
  }
	
	/**
	 * Advances to the next part of the message, if there is a
	 * next part. When you create an instance of a MimeParser,
	 * you need to call nextPart() before you start getting data.
	 *
	 * @return    true if there is a next part, false if there isn't 
	 *            (which generally means you're at the end of the
	 *            message)
	 */
	public int next() throws MimeException {
    switch (event) {
      case START_DOCUMENT:
        parseHeader();
        return (event = START_HEADERS_PART);
      case START_HEADERS_PART:
        String type = getHeaderValue("content-type");
        
        if (type == null) {
          parsePart();
          return (event = SINGLE_PART);
        } else if (type.toLowerCase().indexOf("multipart/") >= 0) {
          boundaries.push(retrieveBoundary());
          parseBoundary();
          return (event = START_MULTIPART);
        } else if (type.toLowerCase().indexOf("message/rfc822") >= 0) {
          parseHeader();
          return (event = START_HEADERS_PART);
        } else {
          parsePart();
          return (event = SINGLE_PART);
        }
      case START_MULTIPART:
        parseHeader();
        return (event = START_HEADERS_PART);
      case END_MULTIPART:
        boundaries.pop();
        
        if (boundaries.size() > 0) {
          parseBoundary();
          
          if (isEndBoundary()) {
            return (event = END_MULTIPART);
          } else {
            parseHeader();
            return (event = START_HEADERS_PART);
          } 
        } else {
          return (event = END_DOCUMENT);       
        }
      case SINGLE_PART:
        part = null;
        
        if (isEndBoundary()) {
          return (event = END_MULTIPART);
        } else if (isBoundary()) {
          parseHeader();
          return (event = START_HEADERS_PART);
        } else {
          if (boundaries.size() > 0)
            return (event = END_MULTIPART);
          else
            return (event = END_DOCUMENT);
        }
      case END_DOCUMENT:
        return event;
    }
    
    throw new MimeException("UNKNOWN STATE " + event);
  }
	
	/**
   * Returns the current boundary 
	 *
	 * @return    The current boundary
	 */
	public String getCurrentBoundary() {
    return (boundaries.size() == 0 ? null : (String) boundaries.peek());
	}
	
	/**
    * Get the boundary that we're breaking the message up on
	 *
	 * @return    a String containing the message boundary,
	 *            or an empty String if the boundary isn't available
	 */
	public String getBoundary() throws MimeException {
    assertEvent(START_MULTIPART);
    return ((String) boundaries.peek()).substring(2);
	}
	
	/**
	 * Get the header of the current message part that we're
	 * looking at
	 *
	 * @return    a String containing the current part's header,
	 *            or an empty String if the header isn't available
	 */
	public byte[] getHeader() throws MimeException {
    assertEvent(START_HEADERS_PART);
    return header;
	}
	
	/**
	 * Gets the data contained in the current message part as
	 * a byte array (this will return an empty byte array if you've already 
	 * got the data from this message part)
	 *
	 * @return    a byte array containing the data in this message part,
	 *            or an empty byte array if you've already read this data
	 */
	public byte[] getPart() throws MimeException {
    assertEvent(SINGLE_PART);
    return part;
  }
	
	/**
	 * Gets the specified value from a specified header, or null if
	 * the entry does not exist
	 *
	 * @param header    the header to look at
	 * @param entry      the name of the entry you're looking for
	 * @return    a String containing the value you're looking for,
	 *            or null if the entry cannot be found
	 */
	public String getHeaderValue(String entry) throws MimeException {
    assertEvent(START_HEADERS_PART);
		String value = null;
		boolean gotit = false;
		
		// use the lowercase version of the name, to avoid any case issues
		entry = entry.toLowerCase();
		if (! entry.endsWith(":"))
			entry = entry + ":";
		
		StringTokenizer st = new StringTokenizer(new String(header), "\r\n");
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
      
			if (line.toLowerCase().startsWith(entry)) {
				value = line.substring(entry.length()).trim();
				gotit = true;
			} else if ((gotit) && (line.length() > 0)) {
				// headers can actually span multiple lines, as long as
				// the next line starts with whitespace
				if (Character.isWhitespace(line.charAt(0)))
					value += " " + line.trim();
				else
					gotit = false;
			}
		}
		
		return value;
	}
  
	/**
   * ----- INTERNAL PARSING METHODS -----
   */
  
  /**
   * An assertion method
   *
   * @param the expected event
   */
  protected void assertEvent(int e) throws MimeException {
    if (event != e) 
      throw new MimeException("Expected mime event '" + e + "' got '" + event + "'");
  }
  
  /**
    * Method which returns whether or not we've actually hit a boundary
   */
  protected boolean isBoundary() {
    return ((boundary != null) && (boundary.length > 0));
  }
  
  /**
   * Method which returns whether or not we've actually hit a boundary
   */
  protected boolean isEndBoundary() {
    if (! isBoundary())
      return false;
    
    return new String(boundary).startsWith(getCurrentBoundary() + "--");
  }
	
	/**
	 * A private method to attempt to read the MIME boundary from the
	 * Content-Type entry in the first header it finds. This should be
	 * called once, when the class is first instantiated.
	 */
	protected String retrieveBoundary() throws MimeException {
		String value = getHeaderValue("content-type");
    
		if (value != null) {
			int pos1 = value.toLowerCase().indexOf("boundary");
			int pos2 = value.indexOf(";", pos1);
			if (pos2 < 0)
				pos2 = value.length();
			if ((pos1 > 0) && (pos2 > pos1))
				value = value.substring(pos1+9, pos2);
		}
    
    // now, trim any whitespace off of the end
    value = value.trim();
		
		// you're allowed to enclose your boundary in quotes too,
		// so we need to account for that possibility
		if (value.startsWith("\"") || value.startsWith("'"))
			value = value.substring(1);
		if (value.endsWith("\"") || value.endsWith("'"))
			value = value.substring(0, value.length()-1);
    
    return "--" + value;
	}
  
  /**
   * Internal method for parsing a MIME boundary
   */
  protected void parseBoundary() throws MimeException {
    part = null;
    header = null;
    boundary = null;
    
    if (readLine() == 0) return;
    
    while ((new String(buffer, 0, bufferLength)).indexOf(getCurrentBoundary()) < 0)
      if (readLine() == 0) return;
    
    storeBoundary();
  }
  
  /**
   * Internal method which grabs the MIME boundary from the 
   * current line in the buffer.
   */
  protected void storeBoundary() {
    boundary = new byte[(buffer[bufferLength-2] == (byte) '\r' ? bufferLength-2 : bufferLength-1)];
    System.arraycopy(buffer, 0, boundary, 0, boundary.length);
  }
  
	/**
	 * A private method to get the next header block on the InputStream.
	 * For our purposes, a header is a block of text that ends with a
	 * blank line.
	 */
	protected void parseHeader() throws MimeException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    part = null;
    boundary = null;
    
    while (true) {
      // check for EOF
      if (readLine() == 0) break;
      
      if ((buffer[0] == '\n') || ((buffer[0] == '\r') && (buffer[1] == '\n')))
        break;
      
      baos.write(buffer, 0, bufferLength);
    }
		
		header = baos.toByteArray();
	}
  
  /**
   * Internal method which parses and stores the given part. NOTE:
   * this method also parses the MIME boundary following the part
   * and stores the result into the boundary field.
   */
  protected void parsePart() throws MimeException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    header = null;
    boundary = null;
    
		byte[] other = new byte[buffer.length];
    int otherLength = 0;
		
		// start getting data -- this is going to seem a little cumbersome because
		// technically the CRLF (\r\n) that is supposed to appear just before the
		// boundary actually belongs to the boundary, not to the body data (if the
		// body is binary, an extra CRLF at the end could screw it up), so we're
		// always writing the previous line until we find the boundary
    
		while (true) {
      // check to see if we've reached the EOF
      if (readLine() == 0) break;
      
      // once we've found the next boundary, make sure we write the
      // data in the last line, minus the CRLF that's supposed to be
      // at the end (just to be nice, we'll even try to act properly
      // if the line terminates with a \n instead of a \r\n)
			if (startsWith(buffer, getCurrentBoundary())) {
				if (otherLength > 1) 
					baos.write(other, 0, (other[otherLength-2] == (byte) '\r' ? otherLength-2 : otherLength-1));
        
        storeBoundary();
        part = baos.toByteArray();
				return;
			} else {
				baos.write(other, 0, otherLength);
			}
			
			byte[] tmp = other;
      other = buffer;
      otherLength = bufferLength;
      buffer = tmp;
		}
		
		// if we hit the end of the file, make sure we write the blineLast
		// data before we finish up
    baos.write(other, 0, otherLength);
		
    part = baos.toByteArray();
  }
  
  /**
   * Internal method for startsWith
   *
   * @param byte[] THe array
   * @param s The stirng
   */
  protected boolean startsWith(byte[] array, String s) {
    if (s == null)
      return false;
    
    if (array.length < s.length())
      return false;
    
    for (int i=0; i<s.length(); i++) 
      if (array[i] != (byte) s.charAt(i))
        return false;
    
    return true;
  }
  
  /**
   * A way to read a single "line" of bytes from an InputStream.
	 * The byte array that is returned will include the line
	 * terminator (\n), unless we reached the end of the stream.
   *
   * @return the number of characters read
	 */
	private int readLine() throws MimeException {
    try {
      bufferLength = 0;
      int c;
      
      // read the bytes one-by-one until we hit a line terminator
      // or the end of the file 
      while ((c = in.read()) != -1) {
        appendToBuffer((byte) c);
        
        if (c == '\n')
          break;
      }
      
      return bufferLength;
    } catch (IOException e) {
      throw new MimeException(e);
    }
  }
  
  /**
   * Internal method which appends a byte to the internal buffer
   *
   * @param b The byte
   */
  protected void appendToBuffer(byte b) {
    if (bufferLength == buffer.length) {
      byte[] tmp = new byte[buffer.length * 2];
      System.arraycopy(buffer, 0, tmp, 0, buffer.length);
      buffer = tmp;
    }
    
    buffer[bufferLength++] = b;
  }
  
  /**
   * ----- TESTING METHOD -----
   */
  
  /**
    * A simple main method, in case you want to test the basic
   * functionality of this class by running it stand-alone.
   */
  public static void main (String args[]) throws Exception {
    if (args.length == 0) {
      System.out.println("USAGE: java MimeParser file [file2] [file3]...");
      return;
    }
    
    try {
      for (int i=0; i<args.length; i++) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(args[i]);
        byte[] tmp = new byte[1024];
        int c = 0;
        
        while ((c = fis.read(tmp)) > 0) 
          baos.write(tmp, 0, c);
        
        fis.close(); 
        
        long startTime = System.currentTimeMillis();
        MimeParser smr = new MimeParser(new ByteArrayInputStream(baos.toByteArray()));
        boolean done = false;
        
        while (true) {          
          switch (smr.next()) {
            case START_DOCUMENT:
              System.out.println("START_DOCUMENT");
              break;
            case START_HEADERS_PART:
              System.out.println("START_HEADERS_PART");
              System.out.println(new String(smr.getHeader()));
              break;
            case START_MULTIPART:
              System.out.println("START_MULTIPART");
              System.out.println(new String(smr.getBoundary()));
              break;
            case SINGLE_PART:
              System.out.println("SINGLE_PART");
              System.out.println(new String(smr.getPart()));
              break;
            case END_MULTIPART:
              System.out.println("END_MULTIPART");
              break;
            case END_DOCUMENT:
              System.out.println("END_DOCUMENT");
              done = true;
              break;
          }
          
          if (done) break;
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println(String.valueOf(endTime - startTime) + " ms");
      }
    } catch (Exception e) {
      e.printStackTrace();
    } 
  }
}
