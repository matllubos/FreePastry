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
	int SPACE = 25;
	int LPAREN = 26;
	int RPAREN = 27;
	int ATOM = 28;
	int FLAG = 29;
	int LSBRACKET = 30;
	int RSBRACKET = 31;
	int QUOTE = 32;
	int QUOTED_CHAR = 33;
	int STRING = 34;
	int QUOTED = 35;
	int QUOTED_SPECIALS = 36;
	int ATOM_CHAR = 37;
	int CHAR = 38;
	int CTL = 39;
	int PLUS = 40;
	int NUMBER = 41;
	int LITERAL_START = 42;
	int UNKNOWN = 43;
}
