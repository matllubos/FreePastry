header {
package rice.email.proxy.imap.parser.antlr;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.ImapConnection;

import java.io.IOException;

}
class ImapCommandLexer extends Lexer;

options {
	exportVocab=CommonLex;
	//codeGenMakeSwitchThreshold=999;
	//codeGenBitsetTestThreshold=999;
	defaultErrorHandler=false;
	caseSensitiveLiterals=false;
}

tokens {
	//PATTERN;
	CHECK="CHECK";
	NOOP="NOOP";
	LOGOUT="LOGOUT";
	CAPABILITY="CAPABILITY";
	CREATE="CREATE";
	DELETE="DELETE";
	LIST="LIST";
	SUBSCRIBE="SUBSCRIBE";
	UNSUBSCRIBE="UNSUBSCRIBE";
	LSUB="LSUB";
	EXAMINE="EXAMINE";
	LOGIN="LOGIN";
	SELECT="SELECT";
	FETCH="FETCH";
	UID="UID";
	APPEND="APPEND";
	COPY="COPY";
	STORE="STORE";
	STATUS="STATUS";
	EXPUNGE="EXPUNGE";
	CLOSE="CLOSE";
	BODY="BODY";
	BODYPEEK="BODY.PEEK";
  RFC822="RFC822";
  RFC822HEADER="RFC822.HEADER";
  RFC822TEXT="RFC822.TEXT";
}

{
	boolean expectingCommand = true;
	
	public int testLiteralsTable(int ttype) {
		if (expectingCommand) {
		  int value = super.testLiteralsTable(ttype);
		  if (value != ttype) {
		  	if (value != UID)
		  	  expectingCommand = false;
		  }
		  return value;
		} else {
      String text = getText();
		  if (text.equals("BODY")) return BODY;
		  if (text.equals("BODY.PEEK")) return BODYPEEK;
		  if (text.equals("RFC822")) return RFC822;
		  if (text.equals("RFC822.HEADER")) return RFC822HEADER;
		  if (text.equals("RFC822.TEXT")) return RFC822TEXT;
		  return ttype;
		}
	}
}

SPACE	:	' '
	;
	
LPAREN	:	'('
	;
	
RPAREN	:	')'
	;

ATOM :
		(ATOM_CHAR | '%' | '*')+
	; 

FLAG :	'\\' ATOM
	;

LSBRACKET : '['
	;

RSBRACKET : ']'
	;

protected
QUOTE	:	'\"'
	;

protected
QUOTED_CHAR :	~('\"' | '\\' | '\r' | '\n') | ('\\' QUOTED_SPECIALS)
	;

STRING	:	QUOTED // | LITERAL
	;

protected
QUOTED	:	QUOTE! (QUOTED_CHAR)* QUOTE!
	;


protected
QUOTED_SPECIALS :	'\"' | '\\'
	;
  
protected
ATOM_CHAR : ~('(' | ')' | '{' | '[' | ']' | ' ' | '*' | '%' | '\\' | '\"' | '\u007f'..'\u00FF' | '\u0000'..'\u001f')
  ;

protected
CHAR	:	('\u0001'..'\u007f')
	;

protected
CTL	:	('\u0000'..'\u001f' | '\u007f')
	;

protected
PLUS	:	'+'
	;

protected
NUMBER	:	('0'..'9')+
	;

LITERAL_START	:	'{'! NUMBER '}'!
	;

protected
UNKNOWN :	(options {greedy=false;}:.)* '\r' '\n'
	;