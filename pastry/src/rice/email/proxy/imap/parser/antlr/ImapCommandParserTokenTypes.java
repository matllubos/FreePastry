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
	int DELETE = 9;
	int LIST = 10;
	int SUBSCRIBE = 11;
	int UNSUBSCRIBE = 12;
	int LSUB = 13;
	int EXAMINE = 14;
	int LOGIN = 15;
	int SELECT = 16;
	int FETCH = 17;
	int UID = 18;
	int APPEND = 19;
	int COPY = 20;
	int STORE = 21;
	int STATUS = 22;
	int EXPUNGE = 23;
	int CLOSE = 24;
	int BODY = 25;
	int RFC822 = 26;
	int PEEK = 27;
	int HEADER = 28;
	int FIELDS = 29;
	int NOT = 30;
	int TEXT = 31;
	int MIME = 32;
	int SIZE = 33;
	int ALL = 34;
	int FAST = 35;
	int FULL = 36;
	int BODYSTRUCTURE = 37;
	int ENVELOPE = 38;
	int FLAGS = 39;
	int INTERNALDATE = 40;
	int PERIOD = 41;
	int SPACE = 42;
	int LPAREN = 43;
	int RPAREN = 44;
	int ATOM = 45;
	int FLAG = 46;
	int LSBRACKET = 47;
	int RSBRACKET = 48;
	int LSANGLE = 49;
	int RSANGLE = 50;
	int NUMBER = 51;
	int QUOTE = 52;
	int QUOTED_CHAR = 53;
	int STRING = 54;
	int QUOTED = 55;
	int QUOTED_SPECIALS = 56;
	int ATOM_CHAR = 57;
	int CHAR = 58;
	int CTL = 59;
	int PLUS = 60;
	int LITERAL_START = 61;
	int UNKNOWN = 62;
}
