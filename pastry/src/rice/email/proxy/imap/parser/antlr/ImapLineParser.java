package rice.email.proxy.imap.parser.antlr;

import antlr.NoViableAltException;
import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.TokenStreamRecognitionException;

import rice.email.proxy.imap.commands.AbstractImapCommand;
import rice.email.proxy.imap.commands.BadSyntaxCommand;

import java.io.StringReader;



public class ImapLineParser
{

    void lexEatUnknowns(ImapCommandLexer lexer)
                 throws RecognitionException, TokenStreamException
    {
        try
        {
            lexer.mUNKNOWN(true);
            lexer.expectingCommand = true;
        }
        catch (antlr.CharStreamException cse)
        {
            RecognitionException re = new RecognitionException(cse.toString());
            re.initCause(cse);
            throw re;
        }
    }

    public AbstractImapCommand parseCommand(String line)
    {
        ImapCommandLexer lexer = new ImapCommandLexer(new StringReader(
                                                              line));
        ImapCommandParser parser = new ImapCommandParser(lexer);
        try
        {

            return parseCommand(parser, lexer);
        }
        catch (TokenStreamException e)
        {

            return new BadSyntaxCommand();
        }
    }

    public AbstractImapCommand parseCommand(ImapCommandParser parser, 
                                            ImapCommandLexer lexer)
                                     throws TokenStreamException
    {
        try
        {
            try
            {
                parser.command_line();
            }
            catch (TokenStreamRecognitionException tsre)
            {
                parser.resetState();
                lexEatUnknowns(lexer);

                return new BadSyntaxCommand();
            }
            catch (NoViableAltException nvae)
            {
                parser.unknown();

                return new BadSyntaxCommand();
            }
        }
        catch (RecognitionException re)
        {
            return new BadSyntaxCommand();
        }

        return parser.getCommand();
    }
}