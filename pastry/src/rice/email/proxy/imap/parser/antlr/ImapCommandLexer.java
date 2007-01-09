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
// $ANTLR 2.7.5 (20050128): "lexer.g" -> "ImapCommandLexer.java"$

package rice.email.proxy.imap.parser.antlr;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.ImapConnection;

import java.io.IOException;


import java.io.InputStream;
import antlr.TokenStreamException;
import antlr.TokenStreamIOException;
import antlr.TokenStreamRecognitionException;
import antlr.CharStreamException;
import antlr.CharStreamIOException;
import antlr.ANTLRException;
import java.io.Reader;
import java.util.Hashtable;
import antlr.CharScanner;
import antlr.InputBuffer;
import antlr.ByteBuffer;
import antlr.CharBuffer;
import antlr.Token;
import antlr.CommonToken;
import antlr.RecognitionException;
import antlr.NoViableAltForCharException;
import antlr.MismatchedCharException;
import antlr.TokenStream;
import antlr.ANTLRHashString;
import antlr.LexerSharedInputState;
import antlr.collections.impl.BitSet;
import antlr.SemanticException;

public class ImapCommandLexer extends antlr.CharScanner implements CommonLexTokenTypes, TokenStream
 {

	boolean expectingCommand = true;
public ImapCommandLexer(InputStream in) {
	this(new ByteBuffer(in));
}
public ImapCommandLexer(Reader in) {
	this(new CharBuffer(in));
}
public ImapCommandLexer(InputBuffer ib) {
	this(new LexerSharedInputState(ib));
}
public ImapCommandLexer(LexerSharedInputState state) {
	super(state);
	caseSensitiveLiterals = false;
	setCaseSensitive(true);
	literals = new Hashtable();
	literals.put(new ANTLRHashString("MIME", this), new Integer(33));
	literals.put(new ANTLRHashString("LIST", this), new Integer(10));
	literals.put(new ANTLRHashString("UNDELETED", this), new Integer(68));
	literals.put(new ANTLRHashString("CREATE", this), new Integer(8));
	literals.put(new ANTLRHashString("NEW", this), new Integer(53));
	literals.put(new ANTLRHashString("FIELDS", this), new Integer(30));
	literals.put(new ANTLRHashString("UID", this), new Integer(19));
	literals.put(new ANTLRHashString("RFC822", this), new Integer(27));
	literals.put(new ANTLRHashString("BCC", this), new Integer(44));
	literals.put(new ANTLRHashString("TO", this), new Integer(66));
	literals.put(new ANTLRHashString("NOOP", this), new Integer(5));
	literals.put(new ANTLRHashString("SINCE", this), new Integer(63));
	literals.put(new ANTLRHashString("ANSWERED", this), new Integer(43));
	literals.put(new ANTLRHashString("NOT", this), new Integer(31));
	literals.put(new ANTLRHashString("UNFLAGGED", this), new Integer(70));
	literals.put(new ANTLRHashString("SUBSCRIBE", this), new Integer(11));
	literals.put(new ANTLRHashString("OLD", this), new Integer(54));
	literals.put(new ANTLRHashString("DRAFT", this), new Integer(48));
	literals.put(new ANTLRHashString("SMALLER", this), new Integer(64));
	literals.put(new ANTLRHashString("SUBJECT", this), new Integer(65));
	literals.put(new ANTLRHashString("FROM", this), new Integer(50));
	literals.put(new ANTLRHashString("KEYWORD", this), new Integer(51));
	literals.put(new ANTLRHashString("CLOSE", this), new Integer(25));
	literals.put(new ANTLRHashString("LOGIN", this), new Integer(15));
	literals.put(new ANTLRHashString("EXAMINE", this), new Integer(14));
	literals.put(new ANTLRHashString("TEXT", this), new Integer(32));
	literals.put(new ANTLRHashString("RENAME", this), new Integer(58));
	literals.put(new ANTLRHashString("BODY", this), new Integer(26));
	literals.put(new ANTLRHashString("AUTHENTICATE", this), new Integer(16));
	literals.put(new ANTLRHashString("OR", this), new Integer(56));
	literals.put(new ANTLRHashString("CAPABILITY", this), new Integer(7));
	literals.put(new ANTLRHashString("UNSEEN", this), new Integer(72));
	literals.put(new ANTLRHashString("ENVELOPE", this), new Integer(39));
	literals.put(new ANTLRHashString("APPEND", this), new Integer(20));
	literals.put(new ANTLRHashString("BEFORE", this), new Integer(45));
	literals.put(new ANTLRHashString("FULL", this), new Integer(37));
	literals.put(new ANTLRHashString("ALL", this), new Integer(35));
	literals.put(new ANTLRHashString("DELETED", this), new Integer(47));
	literals.put(new ANTLRHashString("DELETE", this), new Integer(9));
	literals.put(new ANTLRHashString("SEARCH", this), new Integer(42));
	literals.put(new ANTLRHashString("STORE", this), new Integer(22));
	literals.put(new ANTLRHashString("EXPUNGE", this), new Integer(24));
	literals.put(new ANTLRHashString("RECENT", this), new Integer(57));
	literals.put(new ANTLRHashString("UNANSWERED", this), new Integer(67));
	literals.put(new ANTLRHashString("LSUB", this), new Integer(13));
	literals.put(new ANTLRHashString("SENTBEFORE", this), new Integer(60));
	literals.put(new ANTLRHashString("BODYSTRUCTURE", this), new Integer(38));
	literals.put(new ANTLRHashString("UNKEYWORD", this), new Integer(71));
	literals.put(new ANTLRHashString("SELECT", this), new Integer(17));
	literals.put(new ANTLRHashString("SIZE", this), new Integer(34));
	literals.put(new ANTLRHashString("LARGER", this), new Integer(52));
	literals.put(new ANTLRHashString("CC", this), new Integer(46));
	literals.put(new ANTLRHashString("ON", this), new Integer(55));
	literals.put(new ANTLRHashString("UNSUBSCRIBE", this), new Integer(12));
	literals.put(new ANTLRHashString("STATUS", this), new Integer(23));
	literals.put(new ANTLRHashString("SENTON", this), new Integer(61));
	literals.put(new ANTLRHashString("LOGOUT", this), new Integer(6));
	literals.put(new ANTLRHashString("SEEN", this), new Integer(59));
	literals.put(new ANTLRHashString("SENTSINCE", this), new Integer(62));
	literals.put(new ANTLRHashString("FLAGGED", this), new Integer(49));
	literals.put(new ANTLRHashString("UNDRAFT", this), new Integer(69));
	literals.put(new ANTLRHashString("PEEK", this), new Integer(28));
	literals.put(new ANTLRHashString("INTERNALDATE", this), new Integer(41));
	literals.put(new ANTLRHashString("HEADER", this), new Integer(29));
	literals.put(new ANTLRHashString("CHECK", this), new Integer(4));
	literals.put(new ANTLRHashString("FLAGS", this), new Integer(40));
	literals.put(new ANTLRHashString("FETCH", this), new Integer(18));
	literals.put(new ANTLRHashString("FAST", this), new Integer(36));
	literals.put(new ANTLRHashString("COPY", this), new Integer(21));
}

public Token nextToken() throws TokenStreamException {
	Token theRetToken=null;
tryAgain:
	for (;;) {
		Token _token = null;
		int _ttype = Token.INVALID_TYPE;
		resetText();
		try {   // for char stream error handling
			try {   // for lexical error handling
				switch ( LA(1)) {
				case '.':
				{
					mPERIOD(true);
					theRetToken=_returnToken;
					break;
				}
				case ' ':
				{
					mSPACE(true);
					theRetToken=_returnToken;
					break;
				}
				case '(':
				{
					mLPAREN(true);
					theRetToken=_returnToken;
					break;
				}
				case ')':
				{
					mRPAREN(true);
					theRetToken=_returnToken;
					break;
				}
				case '<':
				{
					mLSANGLE(true);
					theRetToken=_returnToken;
					break;
				}
				case '>':
				{
					mRSANGLE(true);
					theRetToken=_returnToken;
					break;
				}
				case '!':  case '#':  case '$':  case '%':
				case '&':  case '\'':  case '*':  case '+':
				case ',':  case '-':  case '/':  case '0':
				case '1':  case '2':  case '3':  case '4':
				case '5':  case '6':  case '7':  case '8':
				case '9':  case ':':  case ';':  case '=':
				case '?':  case '@':  case 'A':  case 'B':
				case 'C':  case 'D':  case 'E':  case 'F':
				case 'G':  case 'H':  case 'I':  case 'J':
				case 'K':  case 'L':  case 'M':  case 'N':
				case 'O':  case 'P':  case 'Q':  case 'R':
				case 'S':  case 'T':  case 'U':  case 'V':
				case 'W':  case 'X':  case 'Y':  case 'Z':
				case '^':  case '_':  case '`':  case 'a':
				case 'b':  case 'c':  case 'd':  case 'e':
				case 'f':  case 'g':  case 'h':  case 'i':
				case 'j':  case 'k':  case 'l':  case 'm':
				case 'n':  case 'o':  case 'p':  case 'q':
				case 'r':  case 's':  case 't':  case 'u':
				case 'v':  case 'w':  case 'x':  case 'y':
				case 'z':  case '|':  case '}':  case '~':
				{
					mATOM(true);
					theRetToken=_returnToken;
					break;
				}
				case '\\':
				{
					mFLAG(true);
					theRetToken=_returnToken;
					break;
				}
				case '[':
				{
					mLSBRACKET(true);
					theRetToken=_returnToken;
					break;
				}
				case ']':
				{
					mRSBRACKET(true);
					theRetToken=_returnToken;
					break;
				}
				case '"':
				{
					mSTRING(true);
					theRetToken=_returnToken;
					break;
				}
				case '{':
				{
					mLITERAL_START(true);
					theRetToken=_returnToken;
					break;
				}
				default:
				{
					if (LA(1)==EOF_CHAR) {uponEOF(); _returnToken = makeToken(Token.EOF_TYPE);}
				else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
				}
				}
				if ( _returnToken==null ) continue tryAgain; // found SKIP token
				_ttype = _returnToken.getType();
				_ttype = testLiteralsTable(_ttype);
				_returnToken.setType(_ttype);
				return _returnToken;
			}
			catch (RecognitionException e) {
				throw new TokenStreamRecognitionException(e);
			}
		}
		catch (CharStreamException cse) {
			if ( cse instanceof CharStreamIOException ) {
				throw new TokenStreamIOException(((CharStreamIOException)cse).io);
			}
			else {
				throw new TokenStreamException(cse.getMessage());
			}
		}
	}
}

	public final void mPERIOD(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = PERIOD;
		int _saveIndex;
		
		match('.');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mSPACE(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = SPACE;
		int _saveIndex;
		
		match(' ');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mLPAREN(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LPAREN;
		int _saveIndex;
		
		match('(');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mRPAREN(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = RPAREN;
		int _saveIndex;
		
		match(')');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mLSANGLE(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LSANGLE;
		int _saveIndex;
		
		match('<');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mRSANGLE(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = RSANGLE;
		int _saveIndex;
		
		match('>');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mATOM(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = ATOM;
		int _saveIndex;
		
		{
		int _cnt9=0;
		_loop9:
		do {
			if ((_tokenSet_0.member(LA(1)))) {
				mATOM_CHAR(false);
			}
			else {
				if ( _cnt9>=1 ) { break _loop9; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			
			_cnt9++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mATOM_CHAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = ATOM_CHAR;
		int _saveIndex;
		
		{
		match(_tokenSet_0);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mFLAG(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = FLAG;
		int _saveIndex;
		
		match('\\');
		mATOM(false);
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mLSBRACKET(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LSBRACKET;
		int _saveIndex;
		
		match('[');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mRSBRACKET(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = RSBRACKET;
		int _saveIndex;
		
		match(']');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mNUMBER(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = NUMBER;
		int _saveIndex;
		
		{
		int _cnt15=0;
		_loop15:
		do {
			if (((LA(1) >= '0' && LA(1) <= '9'))) {
				matchRange('0','9');
			}
			else {
				if ( _cnt15>=1 ) { break _loop15; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			
			_cnt15++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mQUOTE(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = QUOTE;
		int _saveIndex;
		
		match('\"');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mQUOTED_CHAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = QUOTED_CHAR;
		int _saveIndex;
		
		if ((_tokenSet_1.member(LA(1)))) {
			{
			match(_tokenSet_1);
			}
		}
		else if ((LA(1)=='\\')) {
			{
			match('\\');
			mQUOTED_SPECIALS(false);
			}
		}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mQUOTED_SPECIALS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = QUOTED_SPECIALS;
		int _saveIndex;
		
		switch ( LA(1)) {
		case '"':
		{
			match('\"');
			break;
		}
		case '\\':
		{
			match('\\');
			break;
		}
		default:
		{
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mSTRING(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = STRING;
		int _saveIndex;
		
		mQUOTED(false);
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mQUOTED(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = QUOTED;
		int _saveIndex;
		
		_saveIndex=text.length();
		mQUOTE(false);
		text.setLength(_saveIndex);
		{
		_loop23:
		do {
			if ((_tokenSet_2.member(LA(1)))) {
				mQUOTED_CHAR(false);
			}
			else {
				break _loop23;
			}
			
		} while (true);
		}
		_saveIndex=text.length();
		mQUOTE(false);
		text.setLength(_saveIndex);
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mCHAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = CHAR;
		int _saveIndex;
		
		{
		matchRange('\u0001','\u007f');
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mCTL(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = CTL;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case '\u0000':  case '\u0001':  case '\u0002':  case '\u0003':
		case '\u0004':  case '\u0005':  case '\u0006':  case '\u0007':
		case '\u0008':  case '\t':  case '\n':  case '\u000b':
		case '\u000c':  case '\r':  case '\u000e':  case '\u000f':
		case '\u0010':  case '\u0011':  case '\u0012':  case '\u0013':
		case '\u0014':  case '\u0015':  case '\u0016':  case '\u0017':
		case '\u0018':  case '\u0019':  case '\u001a':  case '\u001b':
		case '\u001c':  case '\u001d':  case '\u001e':  case '\u001f':
		{
			matchRange('\u0000','\u001f');
			break;
		}
		case '\u007f':
		{
			match('\u007f');
			break;
		}
		default:
		{
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mPLUS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = PLUS;
		int _saveIndex;
		
		match('+');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	public final void mLITERAL_START(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LITERAL_START;
		int _saveIndex;
		
		_saveIndex=text.length();
		match('{');
		text.setLength(_saveIndex);
		mNUMBER(false);
		_saveIndex=text.length();
		match('}');
		text.setLength(_saveIndex);
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mUNKNOWN(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = UNKNOWN;
		int _saveIndex;
		
		{
		_loop35:
		do {
			// nongreedy exit test
			if ((LA(1)=='\r')) break _loop35;
			if (((LA(1) >= '\u0000' && LA(1) <= '\u00ff'))) {
				matchNot(EOF_CHAR);
			}
			else {
				break _loop35;
			}
			
		} while (true);
		}
		match('\r');
		match('\n');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	
	private static final long[] mk_tokenSet_0() {
		long[] data = new long[8];
		data[0]=-5764681216083099648L;
		data[1]=8646911283611828223L;
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = new long[8];
		data[0]=-17179878401L;
		data[1]=-268435457L;
		for (int i = 2; i<=3; i++) { data[i]=-1L; }
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = new long[8];
		data[0]=-17179878401L;
		for (int i = 1; i<=3; i++) { data[i]=-1L; }
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	
	}
