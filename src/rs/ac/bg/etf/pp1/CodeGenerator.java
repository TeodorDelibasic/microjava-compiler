package rs.ac.bg.etf.pp1;

import java.util.List;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	
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
	public void visit(StatementReturnVoid StatementReturnVoid) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(StatementPrint statementPrint) {
		Struct type = statementPrint.getExpr().struct;
		
		if (type.getKind() == Struct.Char) {
			Code.loadConst(1);
			Code.put(Code.bprint);
		} else {
			Code.loadConst(5);
			Code.put(Code.print);
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(DesignatorAssignOp designatorAssignop) {
		Code.store(designatorAssignop.getDesignator().obj);
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
		List<Obj> designatorObjList = designatorMultiple.getDesignatorList().objlist.getList();
		
		for (int i = 0; i < designatorObjList.size(); i++) {
			if (designatorObjList.get(i) != SymbolTable.noObj) {
				Code.load(designatorMultiple.getDesignator().obj);
				Code.loadConst(i);
				
				if (designatorMultiple.getDesignator().obj.getType().getKind() == Struct.Char) {
					Code.put(Code.baload);
				} else {
					Code.put(Code.aload);
				}
				
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
	
}
