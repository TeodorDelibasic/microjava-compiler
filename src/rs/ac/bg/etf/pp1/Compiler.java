package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import java_cup.runtime.Symbol;

public class Compiler {
	
	public static void main(String[] args) {
		File source = new File("test/test303.mj");
		try (Reader reader = new BufferedReader(new FileReader(source));) {
			
			Yylex lexer = new Yylex(reader);
			Symbol current = null;
			
			while ((current = lexer.next_token()).sym != sym_manual.EOF) {
				if (current != null && current.value != null) {
					System.out.println(current.toString() + " " + current.value.toString());
				}
			}
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
