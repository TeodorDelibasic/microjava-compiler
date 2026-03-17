package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.Reader;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp2.CodeReset;
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

			program.traverseBottomUp(new ErrorVisitor());
			program.traverseBottomUp(semanticAnalyzer);

			SymbolTable.dump(new DumpVisitor());

			if (parser.errorDetected || semanticAnalyzer.hasErrors()) {
				System.out.println("COMPILE TIME ERROR");
				return;
			}

			if (destination.exists()) destination.delete();

			// Path 1: direct code generation
			Code.dataSize = semanticAnalyzer.getDataSize();

			CodeGenerator codeGenerator = new CodeGenerator();
			program.traverseBottomUp(codeGenerator);
			Code.write(new FileOutputStream(destination));

			System.out.println("Path 1 (direct): SUCCESSFUL COMPILATION -> " + destination.getName());

			// ====== Path 2: AST -> IR -> Bytecode ======
			CodeReset.reset();
			Code.dataSize = semanticAnalyzer.getDataSize();

			// TODO: IRGenerator + IRCodeEmitter
			// IRGenerator irGen = new IRGenerator();
			// program.traverseBottomUp(irGen);
			// IRProgram irProgram = irGen.getProgram();
			// System.out.println(irProgram);  // dump IR
			// IRCodeEmitter emitter = new IRCodeEmitter();
			// emitter.emit(irProgram);

			String irPath = destination.getPath().replace(".obj", "_ir.obj");
			File destinationIR = new File(irPath);
			if (destinationIR.exists()) destinationIR.delete();

			// Code.write(new FileOutputStream(destinationIR));
			// System.out.println("Path 2 (IR):     SUCCESSFUL COMPILATION -> " + destinationIR.getName());
			System.out.println("Path 2 (IR):     NOT YET IMPLEMENTED");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
