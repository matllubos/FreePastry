package rice.p2p.util;

import org.xmlpull.v1.*;
import java.io.*;
import java.util.*;

/** 
* This class is a memory-efficient implementation of most of the XML
* pull parsing API.
*/
public class XMLParser implements XmlPullParser {

  /**
   * The size of the internal buffer to allocate
   */
  public static final int BUFFER_SIZE = 32000;
  
  public static final char[] WHITESPACE_OR_TAG_END = new char[] {' ', '\t', '\n', '\r', '>', '/'};
  public static final char[] WHITESPACE = new char[] {' ', '\t', '\n', '\r'};
  public static final char[] WHITESPACE_OR_EQUALS = new char[] {' ', '\t', '\n', '\r', '='};

  /**
   * The internal reader used to read data
   */
  protected Reader reader;
  
  /**
   * The internal buffer used to process data
   */
  protected char[] buffer;
  
  /**
   * Internal pointers into the buffer
   */
  protected int bufferPosition;
  protected int bufferLimit;
  
  /**
   * The StringCache used to reduce the memory requirements
   */
  protected StringCache cache;
  
  /**
   * The internal stack of tags which have been read
   */
  protected Stack tags;
  
  /**
   * If the tag parsed was a start/end, the name of the tag
   */
  protected String name;
  
  /**
   * If the tag parsed was text, the text
   */
  protected String text;
  
  /**
   * If the tag parsed was a start tag, the list of attribute-> value pairs
   */
  protected HashMap attributes;
  
  /**
   * Constructor
   */
  public XMLParser() {
    this.buffer = new char[BUFFER_SIZE];
    this.bufferPosition = 0;
    this.bufferLimit = 0;
    this.tags = new Stack();
    this.cache = new StringCache();
    this.attributes = new HashMap();
  }
  
  /**
   * Set the input source for parser to the given reader and
   * resets the parser. The event type is set to the initial value
   * START_DOCUMENT.
   * Setting the reader to null will just stop parsing and
   * reset parser state,
   * allowing the parser to free internal resources
   * such as parsing buffers.
   */
  public void setInput(Reader in) throws XmlPullParserException {
    this.reader = in;
  }
  
  /**
   * Returns the text content of the current event as String.
   * The value returned depends on current event type,
   * for example for TEXT event it is element content
   * (this is typical case when next() is used).
   *
   * See description of nextToken() for detailed description of
   * possible returned values for different types of events.
   *
   * <p><strong>NOTE:</strong> in case of ENTITY_REF, this method returns
   * the entity replacement text (or null if not available). This is
   * the only case where
   * getText() and getTextCharacters() return different values.
   *
   * @see #getEventType
   * @see #next
   * @see #nextToken
   */
  public String getText() {
    return this.text;
  }
  
  /**
   * For START_TAG or END_TAG events, the (local) name of the current
   * element is returned when namespaces are enabled. When namespace
   * processing is disabled, the raw name is returned.
   * For ENTITY_REF events, the entity name is returned.
   * If the current event is not START_TAG, END_TAG, or ENTITY_REF,
   * null is returned.
   * <p><b>Please note:</b> To reconstruct the raw element name
   *  when namespaces are enabled and the prefix is not null,
   * you will need to  add the prefix and a colon to localName..
   *
   */
  public String getName() {
    return this.name;
  }
  
  /**
   * Returns the attributes value identified by namespace URI and namespace localName.
   * If namespaces are disabled namespace must be null.
   * If current event type is not START_TAG then IndexOutOfBoundsException will be thrown.
   *
   * <p><strong>NOTE:</strong> attribute value must be normalized
   * (including entity replacement text if PROCESS_DOCDECL is false) as described in
   * <a href="http://www.w3.org/TR/REC-xml#AVNormalize">XML 1.0 section
   * 3.3.3 Attribute-Value Normalization</a>
   *
   * @see #defineEntityReplacementText
   *
   * @param namespace Namespace of the attribute if namespaces are enabled otherwise must be null
   * @param name If namespaces enabled local name of attribute otherwise just attribute name
   * @return value of attribute or null if attribute with given name does not exist
   */
  public String getAttributeValue(String namespace, String name) {
    return (String) attributes.get(name);
  }
  
  /**
   * Returns the type of the current event (START_TAG, END_TAG, TEXT, etc.)
   *
   * @see #next()
   * @see #nextToken()
   */
  public int getEventType() throws XmlPullParserException {
    return 0;
  }
  
  /**
   * Get next parsing event - element content wil be coalesced and only one
   * TEXT event must be returned for whole element content
   * (comments and processing instructions will be ignored and emtity references
   * must be expanded or exception mus be thrown if entity reerence can not be exapnded).
   * If element content is empty (content is "") then no TEXT event will be reported.
   *
   * <p><b>NOTE:</b> empty element (such as &lt;tag/>) will be reported
   *  with  two separate events: START_TAG, END_TAG - it must be so to preserve
   *   parsing equivalency of empty element to &lt;tag>&lt;/tag>.
   *  (see isEmptyElementTag ())
   *
   * @see #isEmptyElementTag
   * @see #START_TAG
   * @see #TEXT
   * @see #END_TAG
   * @see #END_DOCUMENT
   */
  public int next() throws XmlPullParserException, IOException {
    if (bufferPosition == bufferLimit) fillBuffer();
    if (bufferPosition == bufferLimit) return END_DOCUMENT;
    
    char next = buffer[bufferPosition];
    
    switch (next) {
      case '<':
        char next2 = buffer[bufferPosition+1];
        
        switch (next2) {
          case '/':
            return parseEndTag();
          default:
            return parseStartTag();
        }
      case '/':
        return parseEndTag((String) tags.pop());
      default:
        return parseText();
    }
  }
  
  /**
   * Checks whether the current TEXT event contains only whitespace
   * characters.
   * For IGNORABLE_WHITESPACE, this is always true.
   * For TEXT and CDSECT, false is returned when the current event text
   * contains at least one non-white space character. For any other
   * event type an exception is thrown.
   *
   * <p><b>Please note:</b> non-validating parsers are not
   * able to distinguish whitespace and ignorable whitespace,
   * except from whitespace outside the root element. Ignorable
   * whitespace is reported as separate event, which is exposed
   * via nextToken only.
   *
   */
  public boolean isWhitespace() throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  /**
   *   ----- INTERNAL PARSING METHODS -----
   */
  
  /**
   * Internal method which actually fills the buffer
   */
  protected void fillBuffer() throws IOException {
    int read = reader.read(buffer);
    
    if (read > 0) {
      bufferLimit = read;
      bufferPosition = 0;
    }
  }
  
  /**
   * Internal method which clears the list of attributes
   */
  protected void clearAttributes() {
    this.attributes.clear();
  }
  
  /**
   * Internal method which adds an attributes
   */
  protected void addAttribute(String key, String value) {
    this.attributes.put(key, value);
  }
  
  /**
   * An assertion method
   *
   * @param the expected char
   */
  protected void expect(char c) throws XmlPullParserException {
    if (buffer[bufferPosition] != c)
      throw new XmlPullParserException("Expected character '" + c + "' got '" + buffer[bufferPosition] + "'");
    else
      bufferPosition++;
  }
  
  /**
   * Internal method which checks for existence
   *
   * @param chars The chars to check for
   * @param char The char
   */
  protected boolean isWhitespace(char[] chars, int off, int len) {
    for (int i=off; i<off+len; i++)
      if (! contains(WHITESPACE, chars[i]))
        return false;
    
    return true;
  }
  
  /**
   * Internal method which checks for existence
   *
   * @param chars The chars to check for
   * @param char The char
   */
  protected boolean contains(char[] chars, char c) {
    for (int i=0; i<chars.length; i++)
      if (chars[i] == c)
        return true;
    
    return false;
  }
  
  /**
   * Method which parses and returns up to the next token
   *
   * @return The token
   */
  protected String parseUntil(char[] chars, boolean ignoreWhitespace) {
    int pos = bufferPosition;
    
    while (true) {
      char next = buffer[bufferPosition];
      
      if (contains(chars, next))
        break;
      else
        bufferPosition++;
    }
    
    if (ignoreWhitespace && isWhitespace(buffer, pos, (bufferPosition - pos)))
      return null;
    else
      return cache.get(buffer, pos, (bufferPosition - pos));
  }
  
  /**
   * Method which parses up to the next token
   */
  protected void parseUntilNot(char[] chars) {
    int pos = bufferPosition;
    
    while (true) {
      char next = buffer[bufferPosition];
      
      if (! contains(chars, next))
        break;
      else
        bufferPosition++;
    }    
  }
    
  /**
   * Method which parses an end tag of the form <tag />
   *
   * @param The name of the parsed tag
   */
  protected int parseEndTag(String tag) throws XmlPullParserException, IOException {
    expect('/');
    expect('>');

    this.name = tag;
    return END_TAG;
  }
  
  /**
   * Method which parses an end tag of the form <tag />
   *
   * @param The name of the parsed tag
   */
  protected int parseEndTag() throws XmlPullParserException, IOException {
    expect('<');
    expect('/');
    parseUntilNot(WHITESPACE);
 
    clearAttributes();
    this.name = parseUntil(WHITESPACE_OR_TAG_END, false);
    tags.pop();
    
    parseUntilNot(WHITESPACE);
    expect('>');

    return END_TAG;
  }
  
  /**
   * Method which parses a start tag
   */
  protected int parseStartTag() throws XmlPullParserException {
    expect('<');
    parseUntilNot(WHITESPACE);
    
    this.name = parseUntil(WHITESPACE_OR_TAG_END, false);
    tags.push(this.name);
    
    parseUntilNot(WHITESPACE);

    parseAttributes();
    
    parseUntilNot(WHITESPACE);
    
    char next = buffer[bufferPosition];
    
    if (next != '/') {
      expect('>');
    }
    
    return START_TAG;
  }
  
  /**
   * Method which parses all of the attributes of a start tag
   */
  protected void parseAttributes() throws XmlPullParserException {
    clearAttributes();

    while (true) {
      parseUntilNot(WHITESPACE);
      
      char next = buffer[bufferPosition];
      
      switch (next) {
        case '>':
          return;
        case '/':
          return;
        default:
          String key = parseUntil(WHITESPACE_OR_EQUALS, false);
          parseUntilNot(WHITESPACE);
          expect('=');
          parseUntilNot(WHITESPACE);
          String value = null;
          char quote = buffer[bufferPosition];

          if ((quote == '\'') || (quote == '"')) {
            expect(quote);
            value = parseUntil(new char[] {quote}, false);
            expect(quote);
          } else {
            value = parseUntil(WHITESPACE_OR_TAG_END, false);
          }
            
          addAttribute(key, value);
      }
    }
  }
  
  /**
   * Method which parses an end tag of the form <tag />
   *
   * @param The name of the parsed tag
   */
  protected int parseText() throws XmlPullParserException, IOException {
    String temp = parseUntil(new char[] {'<'}, true);
    
    if (temp == null) {
      return next();
    } else {
      clearAttributes();
      this.text = temp;
      return TEXT;
    }
  }
    
  /**
   *   ----- UNSUPPORTED METHODS -----
   */
  
  public void setFeature(String name, boolean state) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public boolean getFeature(String name) {
    throw new UnsupportedOperationException();
  }
  
  public void setProperty(String name, Object value) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public Object getProperty(String name) {
    throw new UnsupportedOperationException();
  }
  
  public void setInput(InputStream inputStream, String inputEncoding) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public String getInputEncoding() {
    throw new UnsupportedOperationException();
  }
  
  public void defineEntityReplacementText(String entityName, String replacementText ) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public int getNamespaceCount(int depth) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public String getNamespacePrefix(int pos) throws XmlPullParserException  {
    throw new UnsupportedOperationException();
  }
  
  public String getNamespaceUri(int pos) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public String getNamespace (String prefix) {
    throw new UnsupportedOperationException();
  }
  
  public int getDepth() {
    throw new UnsupportedOperationException();
  }
    
  public String getPositionDescription () {
    throw new UnsupportedOperationException();
  }
  
  public int getLineNumber() {
    throw new UnsupportedOperationException();
  }
  
  public int getColumnNumber() {
    throw new UnsupportedOperationException();
  }
    
  public char[] getTextCharacters(int [] holderForStartAndLength) {
    throw new UnsupportedOperationException();
  }
  
  public String getNamespace () {
    throw new UnsupportedOperationException();
  }
  
  public String getPrefix() {
    throw new UnsupportedOperationException();
  }
  
  public boolean isEmptyElementTag() throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public String getAttributeNamespace (int index) {
    throw new UnsupportedOperationException();
  }
  
  public String getAttributePrefix(int index) {
    throw new UnsupportedOperationException();
  }
  
  public String getAttributeType(int index) {
    throw new UnsupportedOperationException();
  }
  
  public boolean isAttributeDefault(int index) {
    throw new UnsupportedOperationException();
  }
  
  public int nextToken() throws XmlPullParserException, IOException {
    throw new UnsupportedOperationException();
  }
  
  public void require(int type, String namespace, String name) throws XmlPullParserException, IOException {
    throw new UnsupportedOperationException();
  }
  
  public String nextText() throws XmlPullParserException, IOException {
    throw new UnsupportedOperationException();
  }
  
  public int nextTag() throws XmlPullParserException, IOException {
    throw new UnsupportedOperationException();
  }
  
  public int getAttributeCount() {
    throw new UnsupportedOperationException();
  }
  
  public String getAttributeName(int index) {
    throw new UnsupportedOperationException();
  } 
  
  public String getAttributeValue(int index) {
    throw new UnsupportedOperationException();
  }
}

