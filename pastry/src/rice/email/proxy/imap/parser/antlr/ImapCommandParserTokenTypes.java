// $ANTLR 2.7.2a2 (20020112-1): "grammer.g" -> "ImapCommandParser.java"$

package rice.email.proxy.imap.parser.antlr;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.commands.fetch.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;

import antlr.TokenStreamRecognitionException;
import antlr.CharStreamException;
import antlr.InputBuffer;

import java.io.Reader;
import java.util.*;


public interface ImapCommandParserTokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int CHECK = 4;
	int NOOP = 5;
	int LOGOUT = 6;
	int CAPABILITY = 7;
	int CREATE = 8;
	int LIST = 9;
	int SUBSCRIBE = 10;
	int UNSUBSCRIBE = 11;
	int LSUB = 12;
	int EXAMINE = 13;
	int LOGIN = 14;
	int SELECT = 15;
	int FETCH = 16;
	int UID = 17;
	int APPEND = 18;
	int COPY = 19;
	int STORE = 20;
	int STATUS = 21;
	int EXPUNGE = 22;
	int CLOSE = 23;
	int BODY = 24;
	int BODYPEEK = 25;
	int RFC822 = 26;
	int SPACE = 27;
	int LPAREN = 28;
	int RPAREN = 29;
	int ATOM = 30;
	int FLAG = 31;
	int LSBRACKET = 32;
	int RSBRACKET = 33;
	int QUOTE = 34;
	int QUOTED_CHAR = 35;
	int STRING = 36;
	int QUOTED = 37;
	int QUOTED_SPECIALS = 38;
	int ATOM_CHAR = 39;
	int CHAR = 40;
	int CTL = 41;
	int PLUS = 42;
	int NUMBER = 43;
	int LITERAL_START = 44;
	int UNKNOWN = 45;
	int PERIOD = 46;
}
