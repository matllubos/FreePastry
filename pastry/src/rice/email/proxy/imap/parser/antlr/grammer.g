header {
package rice.email.proxy.imap.parser.antlr;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.commands.fetch.*;
import rice.email.proxy.imap.commands.search.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;
import rice.environment.Environment;

import antlr.TokenStreamRecognitionException;
import antlr.CharStreamException;
import antlr.InputBuffer;

import java.io.Reader;
import java.util.*;

}

class ImapCommandParser extends Parser;

options {
	defaultErrorHandler=false;
	//codeGenMakeSwitchThreshold=999;
	//codeGenBitsetTestThreshold=999;
	importVocab=CommonLex;
}

{
  Environment env; 
  
  AbstractImapCommand command;

  public AbstractImapCommand getCommand() {
    return command;
  }
  
  public void setEnvironment(Environment env) {
    this.env = env;
  }
  
  public void resetState() {
    inputState.guessing = 0;
  }

}

command_line	{Token t; command = null;} :
	(tag SPACE command EOF)=>
	(
		t=tag SPACE command EOF
		{ command.setTag(t.getText()); }
	)
	|
	(tag)=>
	(
	  t=tag unknown
	  {
	  	command = new BadSyntaxCommand();
	  	command.setTag(t.getText());
	  }
	)
	| unknown
	{
		command = new BadSyntaxCommand();
	}
	;

command	:	(command_any | command_auth | command_nonauth)
	;

command_any:
	(
	CAPABILITY {command = new CapabilityCommand();}
	| LOGOUT  {command = new LogoutCommand();}
	| NOOP {command = new NoopCommand();}
  | CHECK {command = new CheckCommand();}
	)
	;

tag returns [Token ret]	{Token at; ret=null;}:	at=astring
	{
	  if (at.getText().indexOf('+') >= 0)
	      throw new SemanticException("'+' not allowed in tags");
    
    ret=at;
	}
	;
	
lname returns [String ret] {ret=""; Token a,c;}:  
    a=astring {ret += a.getText();} (b:PERIOD^ c=astring {ret += b.getText()+c.getText();})* 
    ;
    
astring	returns [Token ret] {ret=null;}:
												a:ATOM {ret = a; } |
												b:STRING {ret = b; } |
												c:CHECK {ret = c; } |
												d:NOOP {ret = d; } |
												e:LOGOUT {ret = e; } |
												f:CAPABILITY {ret = f; } |
												g:CREATE {ret = g; } |
												h:DELETE {ret = h; } |
												i:LIST {ret = i; } |
												j:SUBSCRIBE {ret = j; } |
												k:UNSUBSCRIBE {ret = k; } |
												l:LSUB {ret = l; } |
												m:EXAMINE {ret = m; } |
												n:LOGIN {ret = n; } |
												n1:AUTHENTICATE {ret = n1; } |
												o:SELECT {ret = o; } |
												p:FETCH {ret = p; } |
												q:UID {ret = q; } |
												r:APPEND {ret = r; } |
												s:COPY {ret = s; } |
												t:STORE {ret = t; } |
												u:STATUS {ret = u; } |
												v:EXPUNGE {ret = v; } |
												w:CLOSE {ret = w; } |
												x:BODY {ret = x; } |
												y:RFC822 {ret = y; } |
												z:PEEK {ret = z; } |
												aa:HEADER {ret = aa; } |
												ab:FIELDS {ret = ab; } |
												ac:NOT {ret = ac; } |
												ad:TEXT {ret = ad; } |
												ae:MIME {ret = ae; } |
												af:SIZE {ret = af; } |
												ag:ALL {ret = ag; } |
												ah:FAST {ret = ah; } |
												ai:FULL {ret = ai; } |
												aj:BODYSTRUCTURE {ret = aj; } |
												ak:ENVELOPE {ret = ak; } |
												al:FLAGS {ret = al; } |
												am:INTERNALDATE {ret = am; } |
												an:SEARCH {ret = an; } |
												ao:ANSWERED {ret = ao; } |
												ap:BCC {ret = ap; } |
												aq:BEFORE {ret = aq; } |
												ar:CC {ret = ar; } |
												as:DELETED {ret = as; } |
												at:DRAFT {ret = at; } |
												au:FLAGGED {ret = au; } |
												av:FROM {ret = av; } |
												aw:KEYWORD {ret = aw; } |
												ax:LARGER {ret = ax; } |
												ay:NEW {ret = ay; } |
												az:OLD {ret = az; } |
												ba:ON {ret = ba; } |
												bb:OR {ret = bb; } |
												bc:RECENT {ret = bc; } |
												bd:SEEN {ret = bd; } |
												be:SENTBEFORE {ret = be; } |
												bf:SENTON {ret = bf; } |
												bg:SENTSINCE {ret = bg; } |
												bh:SINCE {ret = bh; } |
												bi:SMALLER {ret = bi; } |
												bj:SUBJECT {ret = bj; } |
												bk:TO {ret = bk; } |
												bl:UNANSWERED {ret = bl; } |
												bm:UNDELETED {ret = bm; } |
												bn:UNDRAFT {ret = bn; } |
												bo:UNFLAGGED {ret = bo; } |
												bp:UNKEYWORD {ret = bp; } |
												bq:UNSEEN {ret = bq; } |
                        br:RENAME {ret = br; }
            ;

pattern returns [Token ret] {ret=null;}:	ret=astring
	;

range [boolean isUID] returns [MsgFilter range] {range=null;}:
	rng:ATOM
	{   try {
			range = new MsgSetFilter(rng.getText(), isUID);
		} catch (RuntimeException e) {
			System.out.println("BAD CLIENT!");
			e.printStackTrace();
		}
	}
	;

flags returns [List flags]
  {
    flags = new ArrayList();
    Token fs;
	// XXX - Note here we're deviating from the IMAP Spec, but FireFox, or whatever
	//       it's called does not conform...
  }:
	LPAREN ( 
	        (f:FLAG {flags.add(f.getText());} | fs=astring {flags.add(fs.getText());})
		    (SPACE (l:FLAG {flags.add(l.getText());} | fs=astring {flags.add(fs.getText());}))*
		    )? RPAREN
	;

atom_list returns [List list]
  {
    Token f;
    list = new ArrayList();
  }:
	LPAREN f=astring {list.add(f.getText());}
		(SPACE f=astring {list.add(f.getText());})* RPAREN
	;

literal returns [int len] {len = -1;} :
	num:LITERAL_START
{
	return Integer.parseInt(num.getText());
}
	;

unknown : (.)* EOF
	;

/*
 * Commands for logged in people
 */

command_auth:
create | delete | rename |
subscribe | unsubscribe | list | lsub |
examine | status | select |
uid | fetch[false] | copy[false] | store[false] | search[false] |
append |
expunge | close
	;
	
create	{Token folder;}:	CREATE SPACE folder=astring
	{
	  CreateCommand cmd = new CreateCommand();
	  cmd.setFolder(folder.getText());
	  command = cmd;
	}
	;
	
delete	{Token folder;}:	DELETE SPACE folder=astring
	{
	  DeleteCommand cmd = new DeleteCommand();
	  cmd.setFolder(folder.getText());
	  command = cmd;
	}
	;
  
rename	{Token old_folder, new_folder;}:	RENAME SPACE old_folder=astring SPACE new_folder=astring
	{
	  RenameCommand cmd = new RenameCommand();
	  cmd.setOldFolder(old_folder.getText());
	  cmd.setNewFolder(new_folder.getText());
	  command = cmd;
	}
	;

subscribe	{Token folder;}:	SUBSCRIBE SPACE folder=astring
	{
	  SubscribeCommand cmd = new SubscribeCommand();
	  cmd.setFolder(folder.getText());
	  command = cmd;
	}
	;

unsubscribe	{Token folder;}:	UNSUBSCRIBE SPACE folder=astring
	{
	  UnsubscribeCommand cmd = new UnsubscribeCommand();
	  cmd.setFolder(folder.getText());
	  command = cmd;
	}
	;

list	{Token ref, folder;}:	LIST SPACE ref=astring SPACE folder=pattern
	{
	  ListCommand cmd = new ListCommand();
	  cmd.setReference(ref.getText());
	  cmd.setFolder(folder.getText());
	  command = cmd;
	}
	;

lsub	{Token ref,folder;}:	LSUB SPACE ref=astring SPACE folder=pattern
	{
	  LsubCommand cmd = new LsubCommand();
	  cmd.setReference(ref.getText());
	  cmd.setFolder(folder.getText());
	  command = cmd;
	}
	;

examine	{Token folder;}:	EXAMINE SPACE folder=astring
	{
	  ExamineCommand cmd = new ExamineCommand();
	  cmd.setFolder(folder.getText());
	  command = cmd;
	}
	;

select	{Token folder;}:	SELECT SPACE folder=astring
	{
	  SelectCommand cmd = new SelectCommand();
	  cmd.setFolder(folder.getText());
	  command = cmd;
	}
	;

append	{Token date=null, folder; int len; List flags=new ArrayList();}:
	APPEND SPACE folder=astring SPACE
		(flags=flags SPACE)?
		((astring SPACE)=>(date=astring SPACE len=literal)
		| len=literal)
	{
	  AppendCommand cmd = new AppendCommand();
	  cmd.setFolder(folder.getText());
	  cmd.setFlags(flags);
    if (date != null)
      cmd.setDate(date.getText());
	  cmd.setContentLength(len);
	  command = cmd;
	}
	;

status	{Token folder; List requests=new ArrayList();}:
	STATUS SPACE folder=astring SPACE
		requests=atom_list
	{
	  StatusCommand cmd = new StatusCommand();
	  cmd.setFolder(folder.getText());
	  cmd.setRequests(requests);
	  command = cmd;
	}
	;

expunge : EXPUNGE
	{
	  ExpungeCommand cmd = new ExpungeCommand();
	  command = cmd;
	}
	;

close : CLOSE
	{
	  CloseCommand cmd = new CloseCommand();
	  command = cmd;
	}
	;

uid :
	UID SPACE (fetch[true] | copy[true] | store[true] | search[true])
	;

copy [boolean isUID]
	{
		CopyCommand cmd = new CopyCommand();
		MsgFilter range;
		Token folder;
	}:
	COPY SPACE range=range[isUID] SPACE folder=astring
	{
		cmd.setFolder(folder.getText());
		cmd.setRange(range);
		command = cmd;
	}
	;

store [boolean isUID]
	{
		StoreCommand cmd = new StoreCommand();
		MsgFilter range;
		Token type;
    Token type2 = null;
		List flags;
	}:
	STORE SPACE range=range[isUID] SPACE type=astring (PERIOD type2=astring)? SPACE flags=flags
	{
		cmd.setFlags(flags);
		cmd.setType(type.getText());
    if (type2 != null) { cmd.setType(type.getText() + "." + type2.getText()); }
		cmd.setRange(range);
		command = cmd;
	}
	;
  
search [boolean isUID]
	{
		SearchCommand cmd = new SearchCommand(isUID);
    AndSearchPart part = new AndSearchPart();
    SearchPart oPart;
	}:
	SEARCH SPACE oPart =search_group[isUID] {part.addArgument(oPart);} 
                (SPACE oPart=search_group[isUID] {part.addArgument(oPart);})*
	{
    cmd.setPart(part);
		command = cmd;
	}
  ;
  
search_group [boolean isUID] returns [AndSearchPart part]
  {
    part = new AndSearchPart();
    SearchPart oPart;
  }
  :
 (oPart=search_part[isUID] {part.addArgument(oPart);}
  |
  LPAREN oPart=search_group[isUID] {part.addArgument(oPart);} 
           (SPACE oPart=search_group[isUID] {part.addArgument(oPart);})*
  RPAREN)
  ;

search_part [boolean isUID] returns [SearchPart part]
  {
     part = null;
  }
  :
 (part=search_part_no_arg
  |
  part=search_part_str_arg 
  |
  part=search_part_num_arg
  |
  part=search_part_date_arg
  |
  part=search_part_other[isUID])
  ;
  
search_part_other [boolean isUID] returns [SearchPart part] 
  {
    part = null;
    SearchPart a1,a2;
    Token field, string;
    MsgFilter range;
  }
  :
 (OR SPACE a1=search_group[isUID] SPACE a2=search_group[isUID] 
   { part = new OrSearchPart(a1, a2); }
  | 
  NOT SPACE a1=search_group[isUID]
   { part = new NotSearchPart(a1); } 
  |
  HEADER SPACE field=astring SPACE string=astring
   { part = new HeaderSearchPart(field.getText(), string.getText()); }
  |
  UID SPACE range=range[true]
   { part = new FilterSearchPart(range); }
  |
  range=range[false]
   { part = new FilterSearchPart(range); })
  ;
  
search_part_no_arg returns [NoArgSearchPart part]
  {
    part = new NoArgSearchPart();
  }:
  (a:ALL {part.setType(a.getText());}
   |
   b:ANSWERED {part.setType(b.getText());}
   |
   c:DELETED {part.setType(c.getText());}
   |
   d:DRAFT {part.setType(d.getText());}
   |
   e:FLAGGED {part.setType(e.getText());}
   |
   f:NEW {part.setType(f.getText());}
   |
   g:OLD {part.setType(g.getText());}
   |
   h:RECENT {part.setType(h.getText());}
   |
   i:SEEN {part.setType(i.getText());}
   |
   j:UNANSWERED {part.setType(j.getText());}
   |
   k:UNDELETED {part.setType(k.getText());}
   |
   l:UNDRAFT {part.setType(l.getText());}
   |
   m:UNFLAGGED {part.setType(m.getText());}
   |
   n:UNSEEN {part.setType(n.getText());})
   ;
   
search_part_str_arg returns [StringArgSearchPart part]
  {
    part = new StringArgSearchPart();
    Token type;
  }:
  (a:BCC {part.setType(a.getText());}
   |
   b:BODY {part.setType(b.getText());}
   |
   c:CC {part.setType(c.getText());}
   |
   d:FROM {part.setType(d.getText());}
   |
   e:KEYWORD {part.setType(e.getText());}
   |
   f:SUBJECT {part.setType(f.getText());}
   |
   g:TEXT {part.setType(g.getText());}
   |
   h:TO {part.setType(h.getText());}
   |
   i:UNKEYWORD {part.setType(i.getText());})
   
   SPACE type=astring {part.setArgument(type.getText());}
   ;
   
search_part_num_arg returns [NumberArgSearchPart part]
  {
    part = new NumberArgSearchPart();
    Token num;
  }:
  (a:LARGER {part.setType(a.getText());}
   |
   b:SMALLER {part.setType(b.getText());})
   
   SPACE num=astring {part.setArgument(Integer.parseInt(num.getText()));}
   ;   
   
search_part_date_arg returns [DateArgSearchPart part]
  {
    part = new DateArgSearchPart();
    Token date;
  }:
  (a:BEFORE {part.setType(a.getText());}
   |
   b:ON {part.setType(b.getText());}
   |
   c:SENTBEFORE {part.setType(c.getText());}
   |
   d:SENTON {part.setType(d.getText());}
   |
   e:SINCE {part.setType(e.getText());})
   
   SPACE date=astring {part.setArgument(date.getText());}
   ; 

fetch [boolean isUID]
	{
		FetchCommand cmd = new FetchCommand(isUID, env);
		MsgFilter range;
	}:
	FETCH SPACE range=range[isUID] SPACE
	(fetch_part[cmd]
		| 
	(LPAREN fetch_part[cmd]
		(SPACE fetch_part[cmd])* RPAREN)
	)
	{
	    cmd.setRange(range);
	    command = cmd;
	}
	;
	
  fetch_part[FetchCommand cmd]
	{
    boolean realBody = false;
    BodyPartRequest breq = new BodyPartRequest();
    RFC822PartRequest rreq = new RFC822PartRequest();
	}
	:
	b:BODY {breq.setName(b.getText()); }
    (PERIOD PEEK {breq.setPeek(true);})?
	  (LSBRACKET {realBody = true;} 
      (body_part[breq])?
	  RSBRACKET
    
      (LSANGLE a1:ATOM PERIOD a2:ATOM {breq.setRange(a1.getText(), a2.getText());} RSANGLE)?
    )?
  {
    if (realBody) {
      cmd.appendPartRequest(breq);
    } else {
      cmd.appendPartRequest("BODY");
    }  
  }
  |
	r:RFC822 {rreq.setName(r.getText());}
    (PERIOD (HEADER {rreq.setType("HEADER");} |
             TEXT {rreq.setType("TEXT");} |
             SIZE {rreq.setType("SIZE");}))?
  {
    cmd.appendPartRequest(rreq);
  }
  |
  flags:FLAGS { cmd.appendPartRequest(flags.getText());} 
  |
  uid:UID { cmd.appendPartRequest(uid.getText());} 
  |
  all:ALL { cmd.appendPartRequest(all.getText());} 
  |
  fast:FAST { cmd.appendPartRequest(fast.getText());} 
  |
  full:FULL { cmd.appendPartRequest(full.getText());} 
  |
  bodystructure:BODYSTRUCTURE { cmd.appendPartRequest(bodystructure.getText());} 
  |
  envelope:ENVELOPE { cmd.appendPartRequest(envelope.getText());} 
  |
  internaldate:INTERNALDATE { cmd.appendPartRequest(internaldate.getText());}
  ;


  body_part[BodyPartRequest breq] :
    non_final_body_part[breq] (PERIOD body_part[breq])?
    |
    final_body_part[breq]
  ;
  
  non_final_body_part[BodyPartRequest breq] :
    num:ATOM {breq.appendType(num.getText());}
  ;
  
  final_body_part[BodyPartRequest breq] 
    {
      Token a;
    }:
    HEADER {breq.appendType("HEADER");}
      (PERIOD FIELDS {breq.reAppendType("HEADER.FIELDS");}
        (PERIOD NOT {breq.reAppendType("HEADER.FIELDS.NOT");})?

        SPACE LPAREN 
          a=astring {breq.addPart(a.getText());} 
            (SPACE a=astring {breq.addPart(a.getText());})*
          RPAREN
      )?
    |
    MIME {breq.appendType("MIME");}
    |
    TEXT {breq.appendType("TEXT");}
  ; 

/*
 * Commands for unauthorized people
 */
 
command_nonauth :	login | authenticate
	;
	
login	{String usr; Token pass;}:	LOGIN SPACE usr=lname SPACE pass=astring
	{
	  LoginCommand cmd = new LoginCommand();
	  cmd.setUser(usr);
	  cmd.setPassword(pass.getText());
	  command = cmd;
	}
	;
  
authenticate	{Token type;}:	AUTHENTICATE SPACE type=astring
	{
	  AuthenticateCommand cmd = new AuthenticateCommand();
	  cmd.setType(type.getText());
	  command = cmd;
	}
	;
