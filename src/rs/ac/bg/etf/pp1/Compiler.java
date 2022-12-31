package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.ast.Program;

public class Compiler {
	
	public static void main(String[] args) {
		File source = new File("test/program.mj");
		try (Reader reader = new BufferedReader(new FileReader(source));) {
			
			Yylex lexer = new Yylex(reader);
			MJParser parser = new MJParser(lexer);
			
//			parser.parse();
			
			Symbol symbol = parser.parse();
			Program prog = (Program)(symbol.value);
			
			System.out.println(prog.toString(""));
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
