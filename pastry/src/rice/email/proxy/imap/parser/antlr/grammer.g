header {
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

}

class ImapCommandParser extends Parser;

options {
	defaultErrorHandler=false;
	//codeGenMakeSwitchThreshold=999;
	//codeGenBitsetTestThreshold=999;
	importVocab=CommonLex;
}

{
  AbstractImapCommand command;

  public AbstractImapCommand getCommand() {
    return command;
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

tag returns [Token ret]	{ret=null;}:	at:ATOM
	{
	  //if (at.getText().indexOf('+') != -1)
	  //    throw new SemanticException("'+' not allowed in tags");
	  ret = at;
	}
	;
	
astring	returns [Token ret] {ret=null;}:	a:ATOM {ret = a;} | b:STRING {ret = b;}
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

flags returns [List flags] {flags = new ArrayList();} :
	LPAREN ff:FLAG {flags.add(ff.getText());}
		(SPACE lf:FLAG {flags.add(lf.getText());})* RPAREN
	;

atom_list returns [List list] {list = new ArrayList();} :
	LPAREN fa:ATOM {list.add(fa.getText());}
		(SPACE la:ATOM {list.add(la.getText());})* RPAREN
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

command_auth :
create |
subscribe | unsubscribe | list | lsub |
examine | status | select |
uid | fetch[false] | copy[false] | store[false] |
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

append	{Token date, folder; int len; List flags=new ArrayList();}:
	APPEND SPACE folder=astring SPACE
		(flags=flags SPACE)?
		((astring SPACE)=>(date=astring SPACE len=literal)
		| len=literal)
	{
	  AppendCommand cmd = new AppendCommand();
	  cmd.setFolder(folder.getText());
	  cmd.setFlags(flags);
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
	UID SPACE (fetch[true] | copy[true] | store[true])
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
		List flags;
	}:
	STORE SPACE range=range[isUID] SPACE type=astring SPACE flags=flags
	{
		cmd.setFlags(flags);
		cmd.setType(type.getText());
		cmd.setRange(range);
		command = cmd;
	}
	;

fetch [boolean isUID]
	{
		FetchCommand cmd = new FetchCommand();
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
		Token part;
	    BodyPartRequest req = new BodyPartRequest();
	}
	:
	part=astring
	{
		cmd.appendPartRequest(part.getText());
	}
	|
	b:BODY {req.setName(b.getText());}
	  (LSBRACKET
	    (h:ATOM {req.setType(h.getText());}
	      (SPACE LPAREN
	        (a:ATOM {req.addPart(a.getText());}
	          (SPACE at:ATOM {req.addPart(at.getText());})+)
	      RPAREN)?)?
	  RSBRACKET)?
  {
    if (req.getType().equals("")) {
      cmd.appendPartRequest(b.getText());
    } else {
      cmd.appendPartRequest(req);
    }
  }
;
/*
 * Commands for unauthorized people
 */
 
command_nonauth :	login
	;
	
login	{Token usr, pass;}:	LOGIN SPACE usr=astring SPACE pass=astring
	{
	  LoginCommand cmd = new LoginCommand();
	  cmd.setUser(usr.getText());
	  cmd.setPassword(pass.getText());
	  command = cmd;
	}
	;
