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


import antlr.TokenBuffer;
import antlr.TokenStreamException;
import antlr.TokenStreamIOException;
import antlr.ANTLRException;
import antlr.LLkParser;
import antlr.Token;
import antlr.TokenStream;
import antlr.RecognitionException;
import antlr.NoViableAltException;
import antlr.MismatchedTokenException;
import antlr.SemanticException;
import antlr.ParserSharedInputState;
import antlr.collections.impl.BitSet;

public class ImapCommandParser extends antlr.LLkParser
       implements ImapCommandParserTokenTypes
 {

  AbstractImapCommand command;

  public AbstractImapCommand getCommand() {
    return command;
  }
  
  public void resetState() {
    inputState.guessing = 0;
  }


protected ImapCommandParser(TokenBuffer tokenBuf, int k) {
  super(tokenBuf,k);
  tokenNames = _tokenNames;
}

public ImapCommandParser(TokenBuffer tokenBuf) {
  this(tokenBuf,1);
}

protected ImapCommandParser(TokenStream lexer, int k) {
  super(lexer,k);
  tokenNames = _tokenNames;
}

public ImapCommandParser(TokenStream lexer) {
  this(lexer,1);
}

public ImapCommandParser(ParserSharedInputState state) {
  super(state,1);
  tokenNames = _tokenNames;
}

	public final void command_line() throws RecognitionException, TokenStreamException {
		
		Token t; command = null;
		
		boolean synPredMatched3 = false;
		if (((LA(1)==ATOM))) {
			int _m3 = mark();
			synPredMatched3 = true;
			inputState.guessing++;
			try {
				{
				tag();
				match(SPACE);
				command();
				match(Token.EOF_TYPE);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched3 = false;
			}
			rewind(_m3);
			inputState.guessing--;
		}
		if ( synPredMatched3 ) {
			{
			t=tag();
			match(SPACE);
			command();
			match(Token.EOF_TYPE);
			if ( inputState.guessing==0 ) {
				command.setTag(t.getText());
			}
			}
		}
		else {
			boolean synPredMatched6 = false;
			if (((LA(1)==ATOM))) {
				int _m6 = mark();
				synPredMatched6 = true;
				inputState.guessing++;
				try {
					{
					tag();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched6 = false;
				}
				rewind(_m6);
				inputState.guessing--;
			}
			if ( synPredMatched6 ) {
				{
				t=tag();
				unknown();
				if ( inputState.guessing==0 ) {
					
						  	command = new BadSyntaxCommand();
						  	command.setTag(t.getText());
						
				}
				}
			}
			else if ((_tokenSet_0.member(LA(1)))) {
				unknown();
				if ( inputState.guessing==0 ) {
					
							command = new BadSyntaxCommand();
						
				}
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		
	public final Token  tag() throws RecognitionException, TokenStreamException {
		Token ret;
		
		Token  at = null;
		ret=null;
		
		at = LT(1);
		match(ATOM);
		if ( inputState.guessing==0 ) {
			
				  //if (at.getText().indexOf('+') != -1)
				  //    throw new SemanticException("'+' not allowed in tags");
				  ret = at;
				
		}
		return ret;
	}
	
	public final void command() throws RecognitionException, TokenStreamException {
		
		
		{
		switch ( LA(1)) {
		case CHECK:
		case NOOP:
		case LOGOUT:
		case CAPABILITY:
		{
			command_any();
			break;
		}
		case CREATE:
		case LIST:
		case SUBSCRIBE:
		case UNSUBSCRIBE:
		case LSUB:
		case EXAMINE:
		case SELECT:
		case FETCH:
		case UID:
		case APPEND:
		case COPY:
		case STORE:
		case STATUS:
		case EXPUNGE:
		case CLOSE:
		{
			command_auth();
			break;
		}
		case LOGIN:
		{
			command_nonauth();
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
	}
	
	public final void unknown() throws RecognitionException, TokenStreamException {
		
		
		{
		_loop25:
		do {
			if (((LA(1) >= CHECK && LA(1) <= UNKNOWN))) {
				matchNot(EOF);
			}
			else {
				break _loop25;
			}
			
		} while (true);
		}
		match(Token.EOF_TYPE);
	}
	
	public final void command_any() throws RecognitionException, TokenStreamException {
		
		
		{
		switch ( LA(1)) {
		case CAPABILITY:
		{
			match(CAPABILITY);
			if ( inputState.guessing==0 ) {
				command = new CapabilityCommand();
			}
			break;
		}
		case LOGOUT:
		{
			match(LOGOUT);
			if ( inputState.guessing==0 ) {
				command = new LogoutCommand();
			}
			break;
		}
		case NOOP:
		{
			match(NOOP);
			if ( inputState.guessing==0 ) {
				command = new NoopCommand();
			}
			break;
		}
		case CHECK:
		{
			match(CHECK);
			if ( inputState.guessing==0 ) {
				command = new CheckCommand();
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
	}
	
	public final void command_auth() throws RecognitionException, TokenStreamException {
		
		
		switch ( LA(1)) {
		case CREATE:
		{
			create();
			break;
		}
		case SUBSCRIBE:
		{
			subscribe();
			break;
		}
		case UNSUBSCRIBE:
		{
			unsubscribe();
			break;
		}
		case LIST:
		{
			list();
			break;
		}
		case LSUB:
		{
			lsub();
			break;
		}
		case EXAMINE:
		{
			examine();
			break;
		}
		case STATUS:
		{
			status();
			break;
		}
		case SELECT:
		{
			select();
			break;
		}
		case UID:
		{
			uid();
			break;
		}
		case FETCH:
		{
			fetch(false);
			break;
		}
		case COPY:
		{
			copy(false);
			break;
		}
		case STORE:
		{
			store(false);
			break;
		}
		case APPEND:
		{
			append();
			break;
		}
		case EXPUNGE:
		{
			expunge();
			break;
		}
		case CLOSE:
		{
			close();
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final void command_nonauth() throws RecognitionException, TokenStreamException {
		
		
		login();
	}
	
	public final Token  astring() throws RecognitionException, TokenStreamException {
		Token ret;
		
		Token  a = null;
		Token  b = null;
		ret=null;
		
		switch ( LA(1)) {
		case ATOM:
		{
			a = LT(1);
			match(ATOM);
			if ( inputState.guessing==0 ) {
				ret = a;
			}
			break;
		}
		case STRING:
		{
			b = LT(1);
			match(STRING);
			if ( inputState.guessing==0 ) {
				ret = b;
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return ret;
	}
	
	public final Token  pattern() throws RecognitionException, TokenStreamException {
		Token ret;
		
		ret=null;
		
		ret=astring();
		return ret;
	}
	
	public final MsgFilter  range(
		boolean isUID
	) throws RecognitionException, TokenStreamException {
		MsgFilter range;
		
		Token  rng = null;
		range=null;
		
		rng = LT(1);
		match(ATOM);
		if ( inputState.guessing==0 ) {
			try {
						range = new MsgSetFilter(rng.getText(), isUID);
					} catch (RuntimeException e) {
						System.out.println("BAD CLIENT!");
						e.printStackTrace();
					}
				
		}
		return range;
	}
	
	public final List  flags() throws RecognitionException, TokenStreamException {
		List flags;
		
		Token  ff = null;
		Token  lf = null;
		flags = new ArrayList();
		
		match(LPAREN);
		ff = LT(1);
		match(FLAG);
		if ( inputState.guessing==0 ) {
			flags.add(ff.getText());
		}
		{
		_loop18:
		do {
			if ((LA(1)==SPACE)) {
				match(SPACE);
				lf = LT(1);
				match(FLAG);
				if ( inputState.guessing==0 ) {
					flags.add(lf.getText());
				}
			}
			else {
				break _loop18;
			}
			
		} while (true);
		}
		match(RPAREN);
		return flags;
	}
	
	public final List  atom_list() throws RecognitionException, TokenStreamException {
		List list;
		
		Token  fa = null;
		Token  la = null;
		list = new ArrayList();
		
		match(LPAREN);
		fa = LT(1);
		match(ATOM);
		if ( inputState.guessing==0 ) {
			list.add(fa.getText());
		}
		{
		_loop21:
		do {
			if ((LA(1)==SPACE)) {
				match(SPACE);
				la = LT(1);
				match(ATOM);
				if ( inputState.guessing==0 ) {
					list.add(la.getText());
				}
			}
			else {
				break _loop21;
			}
			
		} while (true);
		}
		match(RPAREN);
		return list;
	}
	
	public final int  literal() throws RecognitionException, TokenStreamException {
		int len;
		
		Token  num = null;
		len = -1;
		
		num = LT(1);
		match(LITERAL_START);
		if ( inputState.guessing==0 ) {
			
				return Integer.parseInt(num.getText());
			
		}
		return len;
	}
	
	public final void create() throws RecognitionException, TokenStreamException {
		
		Token folder;
		
		match(CREATE);
		match(SPACE);
		folder=astring();
		if ( inputState.guessing==0 ) {
			
				  CreateCommand cmd = new CreateCommand();
				  cmd.setFolder(folder.getText());
				  command = cmd;
				
		}
	}
	
	public final void subscribe() throws RecognitionException, TokenStreamException {
		
		Token folder;
		
		match(SUBSCRIBE);
		match(SPACE);
		folder=astring();
		if ( inputState.guessing==0 ) {
			
				  SubscribeCommand cmd = new SubscribeCommand();
				  cmd.setFolder(folder.getText());
				  command = cmd;
				
		}
	}
	
	public final void unsubscribe() throws RecognitionException, TokenStreamException {
		
		Token folder;
		
		match(UNSUBSCRIBE);
		match(SPACE);
		folder=astring();
		if ( inputState.guessing==0 ) {
			
				  UnsubscribeCommand cmd = new UnsubscribeCommand();
				  cmd.setFolder(folder.getText());
				  command = cmd;
				
		}
	}
	
	public final void list() throws RecognitionException, TokenStreamException {
		
		Token ref, folder;
		
		match(LIST);
		match(SPACE);
		ref=astring();
		match(SPACE);
		folder=pattern();
		if ( inputState.guessing==0 ) {
			
				  ListCommand cmd = new ListCommand();
				  cmd.setReference(ref.getText());
				  cmd.setFolder(folder.getText());
				  command = cmd;
				
		}
	}
	
	public final void lsub() throws RecognitionException, TokenStreamException {
		
		Token ref,folder;
		
		match(LSUB);
		match(SPACE);
		ref=astring();
		match(SPACE);
		folder=pattern();
		if ( inputState.guessing==0 ) {
			
				  LsubCommand cmd = new LsubCommand();
				  cmd.setReference(ref.getText());
				  cmd.setFolder(folder.getText());
				  command = cmd;
				
		}
	}
	
	public final void examine() throws RecognitionException, TokenStreamException {
		
		Token folder;
		
		match(EXAMINE);
		match(SPACE);
		folder=astring();
		if ( inputState.guessing==0 ) {
			
				  ExamineCommand cmd = new ExamineCommand();
				  cmd.setFolder(folder.getText());
				  command = cmd;
				
		}
	}
	
	public final void status() throws RecognitionException, TokenStreamException {
		
		Token folder; List requests=new ArrayList();
		
		match(STATUS);
		match(SPACE);
		folder=astring();
		match(SPACE);
		requests=atom_list();
		if ( inputState.guessing==0 ) {
			
				  StatusCommand cmd = new StatusCommand();
				  cmd.setFolder(folder.getText());
				  cmd.setRequests(requests);
				  command = cmd;
				
		}
	}
	
	public final void select() throws RecognitionException, TokenStreamException {
		
		Token folder;
		
		match(SELECT);
		match(SPACE);
		folder=astring();
		if ( inputState.guessing==0 ) {
			
				  SelectCommand cmd = new SelectCommand();
				  cmd.setFolder(folder.getText());
				  command = cmd;
				
		}
	}
	
	public final void uid() throws RecognitionException, TokenStreamException {
		
		
		match(UID);
		match(SPACE);
		{
		switch ( LA(1)) {
		case FETCH:
		{
			fetch(true);
			break;
		}
		case COPY:
		{
			copy(true);
			break;
		}
		case STORE:
		{
			store(true);
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
	}
	
	public final void fetch(
		boolean isUID
	) throws RecognitionException, TokenStreamException {
		
		
				FetchCommand cmd = new FetchCommand();
				MsgFilter range;
			
		
		match(FETCH);
		match(SPACE);
		range=range(isUID);
		match(SPACE);
		{
		switch ( LA(1)) {
		case BODY:
		case ATOM:
		case STRING:
		{
			fetch_part(cmd);
			break;
		}
		case LPAREN:
		{
			{
			match(LPAREN);
			fetch_part(cmd);
			{
			_loop51:
			do {
				if ((LA(1)==SPACE)) {
					match(SPACE);
					fetch_part(cmd);
				}
				else {
					break _loop51;
				}
				
			} while (true);
			}
			match(RPAREN);
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		if ( inputState.guessing==0 ) {
			
				    cmd.setRange(range);
				    command = cmd;
				
		}
	}
	
	public final void copy(
		boolean isUID
	) throws RecognitionException, TokenStreamException {
		
		
				CopyCommand cmd = new CopyCommand();
				MsgFilter range;
				Token folder;
			
		
		match(COPY);
		match(SPACE);
		range=range(isUID);
		match(SPACE);
		folder=astring();
		if ( inputState.guessing==0 ) {
			
					cmd.setFolder(folder.getText());
					cmd.setRange(range);
					command = cmd;
				
		}
	}
	
	public final void store(
		boolean isUID
	) throws RecognitionException, TokenStreamException {
		
		
				StoreCommand cmd = new StoreCommand();
				MsgFilter range;
				Token type;
				List flags;
			
		
		match(STORE);
		match(SPACE);
		range=range(isUID);
		match(SPACE);
		type=astring();
		match(SPACE);
		flags=flags();
		if ( inputState.guessing==0 ) {
			
					cmd.setFlags(flags);
					cmd.setType(type.getText());
					cmd.setRange(range);
					command = cmd;
				
		}
	}
	
	public final void append() throws RecognitionException, TokenStreamException {
		
		Token date, folder; int len; List flags=new ArrayList();
		
		match(APPEND);
		match(SPACE);
		folder=astring();
		match(SPACE);
		{
		switch ( LA(1)) {
		case LPAREN:
		{
			flags=flags();
			match(SPACE);
			break;
		}
		case ATOM:
		case STRING:
		case LITERAL_START:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		{
		switch ( LA(1)) {
		case ATOM:
		case STRING:
		{
			{
			date=astring();
			match(SPACE);
			len=literal();
			}
			break;
		}
		case LITERAL_START:
		{
			len=literal();
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		if ( inputState.guessing==0 ) {
			
				  AppendCommand cmd = new AppendCommand();
				  cmd.setFolder(folder.getText());
				  cmd.setFlags(flags);
				  cmd.setContentLength(len);
				  command = cmd;
				
		}
	}
	
	public final void expunge() throws RecognitionException, TokenStreamException {
		
		
		match(EXPUNGE);
		if ( inputState.guessing==0 ) {
			
				  ExpungeCommand cmd = new ExpungeCommand();
				  command = cmd;
				
		}
	}
	
	public final void close() throws RecognitionException, TokenStreamException {
		
		
		match(CLOSE);
		if ( inputState.guessing==0 ) {
			
				  CloseCommand cmd = new CloseCommand();
				  command = cmd;
				
		}
	}
	
	public final void fetch_part(
		FetchCommand cmd
	) throws RecognitionException, TokenStreamException {
		
		Token  b = null;
		Token  h = null;
		Token  a = null;
		Token  at = null;
		
				Token part;
			    BodyPartRequest req = new BodyPartRequest();
			
		
		switch ( LA(1)) {
		case ATOM:
		case STRING:
		{
			part=astring();
			if ( inputState.guessing==0 ) {
				
						cmd.appendPartRequest(part.getText());
					
			}
			break;
		}
		case BODY:
		{
			b = LT(1);
			match(BODY);
			if ( inputState.guessing==0 ) {
				req.setName(b.getText());
			}
			{
			switch ( LA(1)) {
			case LSBRACKET:
			{
				match(LSBRACKET);
				{
				switch ( LA(1)) {
				case ATOM:
				{
					h = LT(1);
					match(ATOM);
					if ( inputState.guessing==0 ) {
						req.setType(h.getText());
					}
					{
					switch ( LA(1)) {
					case SPACE:
					{
						match(SPACE);
						match(LPAREN);
						{
						a = LT(1);
						match(ATOM);
						if ( inputState.guessing==0 ) {
							req.addPart(a.getText());
						}
						{
						int _cnt58=0;
						_loop58:
						do {
							if ((LA(1)==SPACE)) {
								match(SPACE);
								at = LT(1);
								match(ATOM);
								if ( inputState.guessing==0 ) {
									req.addPart(at.getText());
								}
							}
							else {
								if ( _cnt58>=1 ) { break _loop58; } else {throw new NoViableAltException(LT(1), getFilename());}
							}
							
							_cnt58++;
						} while (true);
						}
						}
						match(RPAREN);
						break;
					}
					case RSBRACKET:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					break;
				}
				case RSBRACKET:
				{
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				match(RSBRACKET);
				break;
			}
			case EOF:
			case SPACE:
			case RPAREN:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			if ( inputState.guessing==0 ) {
				
				if (req.getType().equals("")) {
				cmd.appendPartRequest(b.getText());
				} else {
				cmd.appendPartRequest(req);
				}
				
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final void login() throws RecognitionException, TokenStreamException {
		
		Token usr, pass;
		
		match(LOGIN);
		match(SPACE);
		usr=astring();
		match(SPACE);
		pass=astring();
		if ( inputState.guessing==0 ) {
			
				  LoginCommand cmd = new LoginCommand();
				  cmd.setUser(usr.getText());
				  cmd.setPassword(pass.getText());
				  command = cmd;
				
		}
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"\"CHECK\"",
		"\"NOOP\"",
		"\"LOGOUT\"",
		"\"CAPABILITY\"",
		"\"CREATE\"",
		"\"LIST\"",
		"\"SUBSCRIBE\"",
		"\"UNSUBSCRIBE\"",
		"\"LSUB\"",
		"\"EXAMINE\"",
		"\"LOGIN\"",
		"\"SELECT\"",
		"\"FETCH\"",
		"\"UID\"",
		"\"APPEND\"",
		"\"COPY\"",
		"\"STORE\"",
		"\"STATUS\"",
		"\"EXPUNGE\"",
		"\"CLOSE\"",
		"\"BODY\"",
		"SPACE",
		"LPAREN",
		"RPAREN",
		"ATOM",
		"FLAG",
		"LSBRACKET",
		"RSBRACKET",
		"QUOTE",
		"QUOTED_CHAR",
		"STRING",
		"QUOTED",
		"QUOTED_SPECIALS",
		"ATOM_CHAR",
		"CHAR",
		"CTL",
		"PLUS",
		"NUMBER",
		"LITERAL_START",
		"UNKNOWN"
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 17592186044402L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	
	}
