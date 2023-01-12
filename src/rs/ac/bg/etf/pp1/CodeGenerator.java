package rs.ac.bg.etf.pp1;

import java.util.List;

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
