package rs.ac.bg.etf.pp1;

import java.util.Stack;

import rs.etf.pp1.symboltable.concepts.*;
import rs.etf.pp1.symboltable.visitors.DumpSymbolTableVisitor;

public class DumpVisitor extends DumpSymbolTableVisitor {
	
	protected StringBuilder output = new StringBuilder();
	protected final String indent = "   ";
	protected StringBuilder currentIndent = new StringBuilder();
	protected Stack<Struct> classStack = new Stack<>();
	
	protected void nextIndentationLevel() {
		currentIndent.append(indent);
	}
	
	protected void previousIndentationLevel() {
		if (currentIndent.length() > 0)
			currentIndent.setLength(currentIndent.length()-indent.length());
	}
	
	@Override
	public void visitObjNode(Obj objToVisit) {
		switch (objToVisit.getKind()) {
			case Obj.Con: 
				output.append("Con ");
				break;
			case Obj.Var: 
				output.append("Var ");
				break;
			case Obj.Type:
				output.append("Type ");
				break;
			case Obj.Meth:
				output.append("Meth ");
				break;
			case Obj.Fld: 
				output.append("Fld ");
				break;
			case Obj.Prog:
				output.append("Prog ");
				break;
		}
		
		output.append(objToVisit.getName());
		output.append(": ");
		
		if (Obj.Var == objToVisit.getKind() && "this".equalsIgnoreCase(objToVisit.getName()) || !classStack.empty() && classStack.peek() == objToVisit.getType())
			output.append("");
		else
			objToVisit.getType().accept(this);
		
		output.append(", ");
		output.append(objToVisit.getAdr());
		output.append(", ");
		output.append(objToVisit.getLevel() + " ");
				
		if (objToVisit.getKind() == Obj.Prog || objToVisit.getKind() == Obj.Meth) {
			output.append("\n");
			nextIndentationLevel();
		}
		

		for (Obj o : objToVisit.getLocalSymbols()) {
			output.append(currentIndent.toString());
			o.accept(this);
			output.append("\n");
		}
		
		if (objToVisit.getKind() == Obj.Prog || objToVisit.getKind() == Obj.Meth) 
			previousIndentationLevel();
	}

	@Override
	public void visitScopeNode(Scope scope) {
		for (Obj o : scope.values()) {
			o.accept(this);
			output.append("\n");
		}
	}

	@Override
	public void visitStructNode(Struct structToVisit) {
		switch (structToVisit.getKind()) {
			case Struct.None:
				output.append("notype");
				break;
			case Struct.Int:
				output.append("int");
				break;
			case Struct.Char:
				output.append("char");
				break;
			case Struct.Bool:
				output.append("bool");
				break;
			case Struct.Array:
				output.append("Arr of ");
				switch (structToVisit.getElemType().getKind()) {
					case Struct.None:
						output.append("notype");
						break;
					case Struct.Int:
						output.append("int");
						break;
					case Struct.Char:
						output.append("char");
						break;
					case Struct.Bool:
						output.append("bool");
						break;
					case Struct.Class:
						output.append("Class");
						break;
				}
				break;
			case Struct.Class:
				if (!classStack.empty() && classStack.peek() == structToVisit)
					break;
				
				classStack.push(structToVisit);
				output.append("Class [");
				for (Obj obj : structToVisit.getMembers()) {
					obj.accept(this);
				}
				output.append("]");
				classStack.pop();
				break;
		}
	}

	public String getOutput() {
		return output.toString();
	}
}

