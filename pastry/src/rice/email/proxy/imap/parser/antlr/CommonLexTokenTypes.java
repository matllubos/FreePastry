// $ANTLR 2.7.2a2 (20020112-1): "lexer.g" -> "ImapCommandLexer.java"$

package rice.email.proxy.imap.parser.antlr;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.ImapConnection;

import java.io.IOException;


public interface CommonLexTokenTypes {
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
	int BODYPEEK = 26;
	int RFC822 = 27;
	int RFC822HEADER = 28;
	int RFC822TEXT = 29;
	int SPACE = 30;
	int LPAREN = 31;
	int RPAREN = 32;
	int ATOM = 33;
	int FLAG = 34;
	int LSBRACKET = 35;
	int RSBRACKET = 36;
	int QUOTE = 37;
	int QUOTED_CHAR = 38;
	int STRING = 39;
	int QUOTED = 40;
	int QUOTED_SPECIALS = 41;
	int ATOM_CHAR = 42;
	int CHAR = 43;
	int CTL = 44;
	int PLUS = 45;
	int NUMBER = 46;
	int LITERAL_START = 47;
	int UNKNOWN = 48;
}
