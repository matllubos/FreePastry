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
		case DELETE:
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
		case DELETE:
		{
			delete();
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
	
	public final void delete() throws RecognitionException, TokenStreamException {
		
		Token folder;
		
		match(DELETE);
		match(SPACE);
		folder=astring();
		if ( inputState.guessing==0 ) {
			
				  DeleteCommand cmd = new DeleteCommand();
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
		case UID:
		case BODY:
		case RFC822:
		case ALL:
		case FAST:
		case FULL:
		case BODYSTRUCTURE:
		case ENVELOPE:
		case FLAGS:
		case INTERNALDATE:
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
			_loop52:
			do {
				if ((LA(1)==SPACE)) {
					match(SPACE);
					fetch_part(cmd);
				}
				else {
					break _loop52;
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
		Token  a1 = null;
		Token  a2 = null;
		Token  r = null;
		Token  flags = null;
		Token  uid = null;
		Token  all = null;
		Token  fast = null;
		Token  full = null;
		Token  bodystructure = null;
		Token  envelope = null;
		Token  internaldate = null;
		
		boolean realBody = false;
		BodyPartRequest breq = new BodyPartRequest();
		RFC822PartRequest rreq = new RFC822PartRequest();
			
		
		switch ( LA(1)) {
		case BODY:
		{
			b = LT(1);
			match(BODY);
			if ( inputState.guessing==0 ) {
				breq.setName(b.getText());
			}
			{
			switch ( LA(1)) {
			case PERIOD:
			{
				match(PERIOD);
				match(PEEK);
				if ( inputState.guessing==0 ) {
					breq.setPeek(true);
				}
				break;
			}
			case EOF:
			case SPACE:
			case RPAREN:
			case LSBRACKET:
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
			case LSBRACKET:
			{
				match(LSBRACKET);
				if ( inputState.guessing==0 ) {
					realBody = true;
				}
				{
				switch ( LA(1)) {
				case HEADER:
				case TEXT:
				case MIME:
				case ATOM:
				{
					body_part(breq);
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
				{
				switch ( LA(1)) {
				case LSANGLE:
				{
					match(LSANGLE);
					a1 = LT(1);
					match(ATOM);
					match(PERIOD);
					a2 = LT(1);
					match(ATOM);
					match(RSANGLE);
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
				
				if (realBody) {
				cmd.appendPartRequest(breq);
				} else {
				cmd.appendPartRequest("BODY");
				}  
				
			}
			break;
		}
		case RFC822:
		{
			r = LT(1);
			match(RFC822);
			if ( inputState.guessing==0 ) {
				rreq.setName(r.getText());
			}
			{
			switch ( LA(1)) {
			case PERIOD:
			{
				match(PERIOD);
				{
				switch ( LA(1)) {
				case HEADER:
				{
					match(HEADER);
					if ( inputState.guessing==0 ) {
						rreq.setType("HEADER");
					}
					break;
				}
				case TEXT:
				{
					match(TEXT);
					if ( inputState.guessing==0 ) {
						rreq.setType("TEXT");
					}
					break;
				}
				case SIZE:
				{
					match(SIZE);
					if ( inputState.guessing==0 ) {
						rreq.setType("SIZE");
					}
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
				
				cmd.appendPartRequest(rreq);
				
			}
			break;
		}
		case FLAGS:
		{
			flags = LT(1);
			match(FLAGS);
			if ( inputState.guessing==0 ) {
				cmd.appendPartRequest(flags.getText());
			}
			break;
		}
		case UID:
		{
			uid = LT(1);
			match(UID);
			if ( inputState.guessing==0 ) {
				cmd.appendPartRequest(uid.getText());
			}
			break;
		}
		case ALL:
		{
			all = LT(1);
			match(ALL);
			if ( inputState.guessing==0 ) {
				cmd.appendPartRequest(all.getText());
			}
			break;
		}
		case FAST:
		{
			fast = LT(1);
			match(FAST);
			if ( inputState.guessing==0 ) {
				cmd.appendPartRequest(fast.getText());
			}
			break;
		}
		case FULL:
		{
			full = LT(1);
			match(FULL);
			if ( inputState.guessing==0 ) {
				cmd.appendPartRequest(full.getText());
			}
			break;
		}
		case BODYSTRUCTURE:
		{
			bodystructure = LT(1);
			match(BODYSTRUCTURE);
			if ( inputState.guessing==0 ) {
				cmd.appendPartRequest(bodystructure.getText());
			}
			break;
		}
		case ENVELOPE:
		{
			envelope = LT(1);
			match(ENVELOPE);
			if ( inputState.guessing==0 ) {
				cmd.appendPartRequest(envelope.getText());
			}
			break;
		}
		case INTERNALDATE:
		{
			internaldate = LT(1);
			match(INTERNALDATE);
			if ( inputState.guessing==0 ) {
				cmd.appendPartRequest(internaldate.getText());
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final void body_part(
		BodyPartRequest breq
	) throws RecognitionException, TokenStreamException {
		
		
		switch ( LA(1)) {
		case ATOM:
		{
			non_final_body_part(breq);
			{
			switch ( LA(1)) {
			case PERIOD:
			{
				match(PERIOD);
				body_part(breq);
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
		case HEADER:
		case TEXT:
		case MIME:
		{
			final_body_part(breq);
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final void non_final_body_part(
		BodyPartRequest breq
	) throws RecognitionException, TokenStreamException {
		
		Token  num = null;
		
		num = LT(1);
		match(ATOM);
		if ( inputState.guessing==0 ) {
			breq.setType(num.getText());
		}
	}
	
	public final void final_body_part(
		BodyPartRequest breq
	) throws RecognitionException, TokenStreamException {
		
		Token  a = null;
		Token  at = null;
		Token  mime = null;
		Token  text = null;
		
		switch ( LA(1)) {
		case HEADER:
		{
			match(HEADER);
			if ( inputState.guessing==0 ) {
				breq.setType("HEADER");
			}
			{
			switch ( LA(1)) {
			case PERIOD:
			{
				match(PERIOD);
				match(FIELDS);
				if ( inputState.guessing==0 ) {
					breq.setType("HEADER.FIELDS");
				}
				{
				switch ( LA(1)) {
				case PERIOD:
				{
					match(PERIOD);
					match(NOT);
					if ( inputState.guessing==0 ) {
						breq.setType("HEADER.FIELDS.NOT");
					}
					break;
				}
				case SPACE:
				{
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				match(SPACE);
				match(LPAREN);
				a = LT(1);
				match(ATOM);
				if ( inputState.guessing==0 ) {
					breq.addPart(a.getText());
				}
				{
				_loop67:
				do {
					if ((LA(1)==SPACE)) {
						match(SPACE);
						at = LT(1);
						match(ATOM);
						if ( inputState.guessing==0 ) {
							breq.addPart(at.getText());
						}
					}
					else {
						break _loop67;
					}
					
				} while (true);
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
		case MIME:
		{
			mime = LT(1);
			match(MIME);
			if ( inputState.guessing==0 ) {
				breq.setType(mime.getText());
			}
			break;
		}
		case TEXT:
		{
			text = LT(1);
			match(TEXT);
			if ( inputState.guessing==0 ) {
				breq.setType(text.getText());
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
		"\"DELETE\"",
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
		"\"RFC822\"",
		"\"PEEK\"",
		"\"HEADER\"",
		"\"FIELDS\"",
		"\"NOT\"",
		"\"TEXT\"",
		"\"MIME\"",
		"\"SIZE\"",
		"\"ALL\"",
		"\"FAST\"",
		"\"FULL\"",
		"\"BODYSTRUCTURE\"",
		"\"ENVELOPE\"",
		"\"FLAGS\"",
		"\"INTERNALDATE\"",
		"PERIOD",
		"SPACE",
		"LPAREN",
		"RPAREN",
		"ATOM",
		"FLAG",
		"LSBRACKET",
		"RSBRACKET",
		"LSANGLE",
		"RSANGLE",
		"NUMBER",
		"QUOTE",
		"QUOTED_CHAR",
		"STRING",
		"QUOTED",
		"QUOTED_SPECIALS",
		"ATOM_CHAR",
		"CHAR",
		"CTL",
		"PLUS",
		"LITERAL_START",
		"UNKNOWN"
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 9223372036854775794L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	
	}
