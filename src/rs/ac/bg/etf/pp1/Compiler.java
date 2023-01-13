package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.Reader;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.etf.pp1.mj.runtime.Code;

public class Compiler {
	
	public static void main(String[] args) {
		
		if (args == null || args.length != 2) {
			return;
		}
		
		File source = new File(args[0]);
		File destination = new File(args[1]);
		
		try (Reader reader = new BufferedReader(new FileReader(source));) {
			
			Lexer lexer = new Lexer(reader);
			Parser parser = new Parser(lexer);
			
			if (parser.errorDetected) {
				return;
			}
			
			Symbol symbol = parser.parse();
			Program program = (Program)(symbol.value);
			
			System.out.println(program.toString(""));
			
			SymbolTable.init();
			
			SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
			
			program.traverseBottomUp(semanticAnalyzer);
			
			SymbolTable.dump(new DumpVisitor());
			
			if (parser.errorDetected || semanticAnalyzer.hasErrors()) {
				System.out.println("Neuspesno");
				return;
			}
			
			if (destination.exists()) destination.delete();
			
			CodeGenerator codeGenerator = new CodeGenerator();
			
			program.traverseBottomUp(codeGenerator);
			
			Code.dataSize = semanticAnalyzer.getDataSize();
			Code.write(new FileOutputStream(destination));
			
			System.out.println("Uspesno");
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
