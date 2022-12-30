package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%{

	private Symbol create_symbol(int type) {
		return new Symbol(type, yyline + 1, yycolumn);
	}
	
	private Symbol create_symbol(int type, Object value) {
		return new Symbol(type, yyline + 1, yycolumn, value);
	}

%}

%cup
%line
%column

%xstate COMMENT

%eofval{
	
	return create_symbol(sym.EOF);
	
%eofval}


%%

" "		{ }
"\b"	{ }
"\t"	{ }
"\r\n"	{ }
"\f"	{ }

"program"	{ return create_symbol(sym.PROG, 		yytext()); }
"if"		{ return create_symbol(sym.IF, 			yytext()); }
"else"		{ return create_symbol(sym.ELSE, 		yytext()); }
"while"		{ return create_symbol(sym.WHILE, 		yytext()); }
"foreach"	{ return create_symbol(sym.FOREACH, 	yytext()); }
"continue"	{ return create_symbol(sym.CONTINUE,	yytext()); }
"break"		{ return create_symbol(sym.BREAK, 		yytext()); }
"const"		{ return create_symbol(sym.CONST, 		yytext()); }
"void"		{ return create_symbol(sym.VOID, 		yytext()); }
"return"	{ return create_symbol(sym.RETURN, 		yytext()); }
"new"		{ return create_symbol(sym.NEW, 		yytext()); }
"print"		{ return create_symbol(sym.PRINT, 		yytext()); }
"read"		{ return create_symbol(sym.READ, 		yytext()); }
"class"		{ return create_symbol(sym.CLASS, 		yytext()); }
"extends"	{ return create_symbol(sym.EXTENDS, 	yytext()); }

"+"		{ return create_symbol(sym.PLUS, 	yytext()); }
"-"		{ return create_symbol(sym.MINUS, 	yytext()); }
"*"		{ return create_symbol(sym.MUL, 	yytext()); }
"/"		{ return create_symbol(sym.DIV, 	yytext()); }
"%"		{ return create_symbol(sym.MOD, 	yytext()); }
"=="	{ return create_symbol(sym.EQ, 		yytext()); }
"!="	{ return create_symbol(sym.NE, 		yytext()); }
">"		{ return create_symbol(sym.GT, 		yytext()); }
">="	{ return create_symbol(sym.GE, 		yytext()); }
"<"		{ return create_symbol(sym.LT, 		yytext()); }
"<="	{ return create_symbol(sym.LE, 		yytext()); }
"&&"	{ return create_symbol(sym.AND, 	yytext()); }
"||"	{ return create_symbol(sym.OR, 		yytext()); }
"="		{ return create_symbol(sym.EQUAL, 	yytext()); }
"++"	{ return create_symbol(sym.INC, 	yytext()); }
"--"	{ return create_symbol(sym.DEC, 	yytext()); }
";"		{ return create_symbol(sym.SEMI, 	yytext()); }
","		{ return create_symbol(sym.COMMA, 	yytext()); }
"."		{ return create_symbol(sym.DOT, 	yytext()); }
"("		{ return create_symbol(sym.LPAREN, 	yytext()); }
")"		{ return create_symbol(sym.RPAREN, 	yytext()); }
"{"		{ return create_symbol(sym.LCURLY, 	yytext()); }
"}"		{ return create_symbol(sym.RCURLY, 	yytext()); }
"["		{ return create_symbol(sym.LSQUARE,	yytext()); }
"]"		{ return create_symbol(sym.RSQUARE,	yytext()); }
"=>"	{ return create_symbol(sym.LAMBDA, 	yytext()); }

"//" 				{ yybegin(COMMENT); }
<COMMENT> . 		{ yybegin(COMMENT); }
<COMMENT> "\r\n" 	{ yybegin(YYINITIAL); }

[0-9]+ 						{ return create_symbol(sym.NUMCONST, 	Integer.parseInt(yytext())); }
\'([\x00-\x7F]|\\n|\\t)\'	{ return create_symbol(sym.CHARCONST, 	yytext().charAt(1)); }
(true|false) 				{ return create_symbol(sym.BOOLCONST, 	Boolean.parseBoolean(yytext())); }

[a-zA-Z][a-zA-Z0-9_]* 	{ return create_symbol(sym.IDENT, yytext()); }

. { System.err.println("Leksicka greska (" + yytext() + ") u liniji " + (yyline + 1) + " i koloni " + yycolumn); }