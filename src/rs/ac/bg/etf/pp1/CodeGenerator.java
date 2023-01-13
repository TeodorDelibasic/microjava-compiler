package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	
	@Override
	public void visit(ProgramHeader programHeader) {
		this.generateOrd();
		this.generateChr();
		this.generateLen();
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(MethodHeader methodHeader) {
		Obj methodObj = methodHeader.obj;
		
		if (methodHeader.getMethodName().equals("main")) {
			Code.mainPc = Code.pc;
		}
		
		methodObj.setAdr(Code.pc);
		
		Code.put(Code.enter);
		Code.put(methodObj.getLevel());
		Code.put(methodObj.getLocalSymbols().size());
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		if (methodDecl.obj.getType().equals(SymbolTable.noType)) {
			Code.put(Code.exit);
			Code.put(Code.return_);
		} else {
			Code.put(Code.trap);
			Code.put(1);
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(MethodCall methodCall) {
		int dest = methodCall.getDesignator().obj.getAdr() - Code.pc;		
		
		Code.put(Code.call);
		Code.put2(dest);
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(StatementReturnVoid StatementReturnVoid) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(StatementReturnExpr statementReturnExpr) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(StatementRead statementRead) {
		Obj designatorObj = statementRead.getDesignator().obj;
		
		if (designatorObj.getType().getKind() == Struct.Char) {
			Code.put(Code.bread);
		} else {
			Code.put(Code.read);
		}
		
		Code.store(designatorObj);
	}
	
	@Override
	public void visit(StatementPrint statementPrint) {
		Struct type = statementPrint.getExpr().struct;
		
		int width, printIns;
		
		if (type.getKind() == Struct.Char) {
			width = 1;
			printIns = Code.bprint;
		} else {
			width = 5;
			printIns = Code.print;
		}
		
		if (statementPrint.getWidthOptional() instanceof WidthYes) {
			width = ((WidthYes) statementPrint.getWidthOptional()).getWidth();
		}
		
		Code.loadConst(width);
		Code.put(printIns);
	}
	
	//------------------------------------------------------------------------
	
	private Stack<Integer> loopJmpStart = new Stack<>();
	private Stack<Integer> jmpEndFixup = new Stack<>(); 
	
	private Stack<Stack<Integer>> falseCondFactFixup = new Stack<>();
	private Stack<Stack<Integer>> trueCondTermFixup = new Stack<>();
	
	private Stack<Stack<Integer>> breakFixupEnd = new Stack<>();
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(CondStart condStart) {
		this.falseCondFactFixup.push(new Stack<>());
		this.trueCondTermFixup.push(new Stack<>());
	}
	
	@Override
	public void visit(CondExpr condExpr) {
		Code.putFalseJump(Code.eq, 0);
		this.falseCondFactFixup.peek().push(Code.pc - 2);
	}
	
	@Override
	public void visit(CondRelOp condRelOp) {
		Code.putFalseJump(condRelOp.getRelOp().opcode.getOpCode(), 0);
		this.falseCondFactFixup.peek().push(Code.pc - 2);
	}
	
	@Override
	public void visit(CondFactSingle condFactSingle) {
		Code.putJump(0);
		this.trueCondTermFixup.peek().push(Code.pc - 2);
	}
	
	@Override
	public void visit(CondTermSingle condTermSingle) {
		Stack<Integer> currentConditionFixup = this.trueCondTermFixup.pop();
		
		for (int conditionFixup : currentConditionFixup) {
			Code.fixup(conditionFixup);
		}
	}
	
	@Override
	public void visit(Or or) {
		Stack<Integer> currentCondTermFixup = this.falseCondFactFixup.pop();
		
		for (int termFixup : currentCondTermFixup) {
			Code.fixup(termFixup);
		}
		
		this.falseCondFactFixup.push(new Stack<>());
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(ElseHeader elseHeader) {
		Stack<Integer> currentCondTermFixup = this.falseCondFactFixup.pop();
		
		for (int termFixup : currentCondTermFixup) {
			Code.fixup(termFixup);
		}
	}
	
	@Override
	public void visit(StatementIf statementIf) {
		Stack<Integer> currentCondTermFixup = this.falseCondFactFixup.pop();
		
		for (int termFixup : currentCondTermFixup) {
			Code.fixup(termFixup);
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(WhileHeader whileHeader) {
		this.loopJmpStart.push(Code.pc);
	}
	
	@Override
	public void visit(StatementWhile statementWhile) {
		Code.putJump(this.loopJmpStart.pop());
		
		Stack<Integer> currentCondTermFixup = this.falseCondFactFixup.pop();
		
		for (int breakFixup : currentCondTermFixup) {
			Code.fixup(breakFixup);
		}
		
		Stack<Integer> currentLoopBreakFixup = this.breakFixupEnd.pop();
		
		for (int breakFixup : currentLoopBreakFixup) {
			Code.fixup(breakFixup);
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(ForeachHeader foreachHeader) {
		int arrayLoadIns;
		
		if (foreachHeader.getDesignator().obj.getType().getElemType() == SymbolTable.charType) {
			arrayLoadIns = Code.baload;
		} else {
			arrayLoadIns = Code.aload;
		}

		Code.load(foreachHeader.getDesignator().obj);
		Code.put(Code.const_m1);
		
		this.loopJmpStart.push(Code.pc);
		
		Code.put(Code.const_1);
		Code.put(Code.add);
		Code.put(Code.dup2);
		Code.put(Code.dup2);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.arraylength);
		Code.putFalseJump(Code.ne, 0);
		
		this.jmpEndFixup.push(Code.pc - 2);
		
		Code.put(arrayLoadIns);
		Code.store(foreachHeader.obj);
		
		this.breakFixupEnd.push(new Stack<>());
	}
	
	@Override
	public void visit(StatementForeach statementForeach) {
		Code.putJump(this.loopJmpStart.pop());
		Code.fixup(this.jmpEndFixup.pop());
		
		Code.put(Code.pop);
		Code.put(Code.pop);
		
		Stack<Integer> currentLoopBreakFixup = this.breakFixupEnd.pop();
		
		for (int breakFixup : currentLoopBreakFixup) {
			Code.fixup(breakFixup);
		}
		
		Code.put(Code.pop);
		Code.put(Code.pop);
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(StatementContinue statementContinue) {
		Code.putJump(this.loopJmpStart.peek());
	}
	
	@Override
	public void visit(StatementBreak statementBreak) {
		Code.putJump(0);
		
		this.breakFixupEnd.peek().push(Code.pc - 2);
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(DesignatorAssignOp designatorAssignop) {
		Code.store(designatorAssignop.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorMethod designatorMethod) {
		if (designatorMethod.getMethodCall().getDesignator().obj.getType() != SymbolTable.noType) {
			Code.put(Code.pop);
		}
	}

	@Override
	public void visit(DesignatorPostfixOp designatorPostfixOp) {
		Obj designatorObj = designatorPostfixOp.getDesignator().obj;
		
		if (designatorObj.getKind() == Obj.Fld) {
			Code.put(Code.dup);
		} else if (designatorPostfixOp.getDesignator().obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
		}

		Code.load(designatorObj);
		Code.put(Code.const_1);
		
		if (designatorPostfixOp.getPostfixOp() instanceof PostfixInc) {
			Code.put(Code.add);
		} else {
			Code.put(Code.sub);
		}
		
		Code.store(designatorObj);
	}
	
	@Override
	public void visit(DesignatorMultiple designatorMultiple) {
		Obj arrayObj = designatorMultiple.getDesignator().obj;
		List<Obj> designatorObjList = designatorMultiple.getDesignatorList().objlist.getList();
		
		int loadArrayElem = (arrayObj.getType().getKind() == Struct.Char ? Code.baload : Code.aload);
		
		for (int i = designatorObjList.size() - 1; i >= 0; i--) {
			if (designatorObjList.get(i) != SymbolTable.noObj) {
				Code.load(arrayObj);
				Code.loadConst(i);
				Code.put(loadArrayElem);
				Code.store(designatorObjList.get(i));
			}
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(DesignatorClass designatorClass) {
		Code.load(designatorClass.getDesignator().obj);
	}
	
	@Override
	public void visit(LoadDesignatorArray loadDesignatorArray) {
		Code.load(loadDesignatorArray.obj);
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(FactorDesignator factorDesignator) {
		Code.load(factorDesignator.obj);
	}
	
	@Override
	public void visit(FactorNum factorNum) {
		Code.load(factorNum.obj);
	}
	
	@Override
	public void visit(FactorChar factorChar) {
		Code.load(factorChar.obj);
	}
	
	@Override
	public void visit(FactorBool factorBool) {
		Code.load(factorBool.obj);
	}
	
	@Override
	public void visit(FactorNewArray factorNewArray) {
		Code.put(Code.newarray);
		
		if (factorNewArray.getType().struct.getKind() == Struct.Char) {
			Code.put(0);
		} else {
			Code.put(1);
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(FactorMultiple factorMultiple) {
		Code.put(factorMultiple.getMulOp().opcode.getOpCode());
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(TermNegative termNegative) {
		Code.put(Code.neg);
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(TermMultiple termMultiple) {
		Code.put(termMultiple.getAddOp().opcode.getOpCode());
	}
	
	//------------------------------------------------------------------------
	
	private void generateOrd() {
		SymbolTable.find("ord").setAdr(Code.pc);
		
		Code.put(Code.return_);
	}
	
	private void generateChr() {
		SymbolTable.find("chr").setAdr(Code.pc);
		
		Code.put(Code.return_);
	}
	
	private void generateLen() {
		SymbolTable.find("len").setAdr(Code.pc);
		
		Code.put(Code.arraylength);
		
		Code.put(Code.return_);
	}	
}
