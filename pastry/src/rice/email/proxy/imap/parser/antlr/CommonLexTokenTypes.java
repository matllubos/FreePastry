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
	int RFC822HEADER = 27;
	int RFC822TEXT = 28;
	int SPACE = 29;
	int LPAREN = 30;
	int RPAREN = 31;
	int ATOM = 32;
	int FLAG = 33;
	int LSBRACKET = 34;
	int RSBRACKET = 35;
	int QUOTE = 36;
	int QUOTED_CHAR = 37;
	int STRING = 38;
	int QUOTED = 39;
	int QUOTED_SPECIALS = 40;
	int ATOM_CHAR = 41;
	int CHAR = 42;
	int CTL = 43;
	int PLUS = 44;
	int NUMBER = 45;
	int LITERAL_START = 46;
	int UNKNOWN = 47;
}
