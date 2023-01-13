package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	public int nVars = 0;
	
	public Struct currentType = SymbolTable.noType;
	public Struct methodType = null;
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(ProgramHeader programHeader) {
		programHeader.obj = SymbolTable.insert(Obj.Prog, programHeader.getProgName(), SymbolTable.noType);
		SymbolTable.openScope();
	}
	
	@Override
	public void visit(Program program) {
		Obj mainMethod = SymbolTable.currentScope.findSymbol("main");
		
		if (mainMethod == null) {
			report_error("Main method must exist", null);
		} else if (!mainMethod.getType().equals(SymbolTable.noType)) {
			report_error("Main method must be void", null);
		} else if (mainMethod.getLevel() != 0) {
			report_error("Main method can't have parameters", null);
		}
		
		SymbolTable.chainLocalSymbols(program.getProgramHeader().obj);
		SymbolTable.closeScope();
	}
	
	//------------------------------------------------------------------------	
	
	@Override
	public void visit(Type type) {
		type.struct = SymbolTable.noType;
		
		Obj typeObj = SymbolTable.find(type.getTypeName());

		if (typeObj.equals(SymbolTable.noObj)) {
			this.report_error("Type " + type.getTypeName() + " not found", type);
		} else if (typeObj.getKind() != Obj.Type) {
			this.report_error(type.getTypeName() + " is not a type", type);
		} else {
			type.struct = typeObj.getType();
		}

		this.currentType = type.struct;
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(ConstDeclNum constDeclNum) {
		this.declareConstant(constDeclNum.getConstName(), constDeclNum.getConstVal(), SymbolTable.intType, constDeclNum);
	}
	
	@Override
	public void visit(ConstDeclChar constDeclChar) {
		this.declareConstant(constDeclChar.getConstName(), constDeclChar.getConstVal(), SymbolTable.charType, constDeclChar);
	}
	
	@Override
	public void visit(ConstDeclBool constDeclBool) {
		this.declareConstant(constDeclBool.getConstName(), constDeclBool.getConstVal(), SymbolTable.boolType, constDeclBool);
	}
	
	private void declareConstant(String constName, int constValue, Struct constType, SyntaxNode syntaxNode) {
		if (this.symbolExists(constName, syntaxNode)) {
			return;
		}
		
		if (!this.currentType.equals(constType)) {
			report_error("Type doesn't match for constant " + constName, syntaxNode);
			return;
		}
		
		SymbolTable.insert(Obj.Con, constName, constType).setAdr(constValue);
	}
	
	@Override
	public void visit(ConstDecl constDecl) {
		if (!this.currentType.equals(SymbolTable.intType) &&
				!this.currentType.equals(SymbolTable.charType) &&
				!this.currentType.equals(SymbolTable.boolType)) {
			report_error("Constants must have builtin type", constDecl);
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(VarDecl varDecl) {
		String varDeclName = varDecl.getVarName();
		
		if (this.symbolExists(varDeclName, varDecl)) {
			return;
		}
		
		Struct varType;
		
		if (varDecl.getVarArray() instanceof VarArrayYes) {
			varType = new Struct(Struct.Array, this.currentType);
		} else {
			varType = this.currentType;
		}
		
		this.nVars++;
		
		SymbolTable.insert(Obj.Var, varDeclName, varType);
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(MethodHeader methodHeader) {
		methodHeader.obj = SymbolTable.noObj;
		
		String methodName = methodHeader.getMethodName();
		
		if (this.symbolExists(methodName, methodHeader)) {
			return;
		}
		
		if (methodHeader.getMethodType() instanceof VoidYes) {
			this.methodType = SymbolTable.noType;
		} else {
			this.methodType = this.currentType;
		}
		
		methodHeader.obj = SymbolTable.insert(Obj.Meth, methodName, this.methodType);
		
		SymbolTable.openScope();
	}
	
	@Override
	public void visit(MethodSignature methodSignature) {
		methodSignature.obj = methodSignature.getMethodHeader().obj;
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		methodDecl.obj = methodDecl.getMethodSignature().obj;
		
		if (methodDecl.obj == SymbolTable.noObj) {
			return;
		}
		
		methodDecl.obj.setLevel(methodDecl.getMethodSignature().getFormPars().objlist.size());
		
		SymbolTable.chainLocalSymbols(methodDecl.obj);
		SymbolTable.closeScope();
		
		this.methodType = null;
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(FormPar formPar) {
		String formParName = formPar.getFormParName();
		
		if (this.symbolExists(formParName, formPar)) {
			return;
		}
		
		Struct formParType;
		
		if (formPar.getFormParArray() instanceof FormParArrayYes) {
			formParType = new Struct(Struct.Array, this.currentType);
		} else {
			formParType = currentType;
		}
		
		formPar.obj = SymbolTable.insert(Obj.Var, formParName, formParType);
	}
	
	@Override
	public void visit(FormParSingle formParSingle) {
		formParSingle.getFormPar().obj.setFpPos(0);
		
		formParSingle.objlist = new ObjList();
		formParSingle.objlist.add(formParSingle.getFormPar().obj);
	}
	
	@Override
	public void visit(FormParMultiple formParMultiple) {
		formParMultiple.getFormPar().obj.setFpPos(formParMultiple.getFormParList().objlist.size());
		
		formParMultiple.objlist = formParMultiple.getFormParList().objlist;
		formParMultiple.objlist.add(formParMultiple.getFormPar().obj);
	}
	
	@Override
	public void visit(FormParsYes formParsYes) {
		formParsYes.objlist = formParsYes.getFormParList().objlist;
	}
	
	@Override
	public void visit(FormParsNo formParsNo) {
		formParsNo.objlist = new ObjList();
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(MethodCall methodCall) {
		Obj methodObj = methodCall.getDesignator().obj;
		
		if (methodObj.equals(SymbolTable.noObj)) {
			return;
		}
		
		if (methodObj.getKind() != Obj.Meth) {
			report_error("Symbol " + methodObj.getName() + " is not a method or a function", methodCall);
			return;
		}
		
		ObjList paramList = methodCall.getActPars().objlist;
		
		if (paramList.size() != methodObj.getLevel()) {
			report_error("Invalid number of arguments for method" + methodObj.getName(), methodCall);
			return;
		}
		
		for (Obj arg : methodObj.getLocalSymbols()) {
			if (!paramList.assignableTo(arg, arg.getFpPos())) {
				report_error("Type mismatch for " + arg.getFpPos() + ". argument", methodCall);
				return;
			}
		}
	}
	
	@Override
	public void visit(ActParsYes actParsYes) {
		actParsYes.objlist = actParsYes.getExprList().objlist;
	}
	
	@Override
	public void visit(ActParsNo actParsNo) {
		actParsNo.objlist = new ObjList();
	}
	
	@Override
	public void visit(ExprMultiple exprMultiple) {
		exprMultiple.objlist = exprMultiple.getExprList().objlist;
		exprMultiple.objlist.add(new Obj(Obj.NO_VALUE, "", exprMultiple.getExpr().struct));
	}
	
	@Override
	public void visit(ExprSingle exprSingle) {
		exprSingle.objlist = new ObjList();
		exprSingle.objlist.add(new Obj(Obj.NO_VALUE, "", exprSingle.getExpr().struct));
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(StatementReturnVoid statementReturnVoid) {
		if (!this.methodType.equals(SymbolTable.noType)) {
			report_error("Non-void method must return an expression", statementReturnVoid);
		}
	}
	
	@Override
	public void visit(StatementReturnExpr statementReturnExpr) {
		if (this.methodType == null) {
			return;
		}
		
		Struct exprType = statementReturnExpr.getExpr().struct;
		
		if (exprType == SymbolTable.noType) {
			return;
		}
		
		if (!this.methodType.equals(exprType)) {
			report_error("Type of returned expression must be equal to method type", statementReturnExpr);
			return;
		}
	}
	
	@Override
	public void visit(StatementRead statementRead) {
		Obj designatorObj = statementRead.getDesignator().obj;
		
		if (!this.isAssignable(designatorObj)) {
			report_error("Can't assign to " + designatorObj.getName(), statementRead);
			return;
		}
		
		if (!this.isBuiltin(designatorObj.getType())) {
			report_error("Only builtin types can be read", statementRead);
			return;
		}
	}
	
	@Override
	public void visit(StatementPrint statementPrint) {
		Struct type = statementPrint.getExpr().struct;
		
		if (type.getKind() == Struct.None) {
			return;
		}
		
		if (!this.isBuiltin(statementPrint.getExpr().struct)) {
			report_error("Only builtin types can be printed", statementPrint);
			return;
		}
	}
	
	private int inLoop = 0;
	
	@Override
	public void visit(StatementContinue statementContinue) {
		if (this.inLoop == 0) {
			report_error("Continue can only be used within a loop", statementContinue);
			return;
		}
	}
	
	@Override
	public void visit(StatementBreak statementBreak) {
		if (this.inLoop == 0) {
			report_error("Break can only be used within a loop", statementBreak);
			return;
		}
	}
	
	@Override
	public void visit(ForeachHeader foreachHeader) {
		this.inLoop++;
		
		Obj arrayObj = foreachHeader.getDesignator().obj;
		
		if (arrayObj == SymbolTable.noObj) {
			return;
		}
		
		if (arrayObj.getType().getKind() != Struct.Array) {
			report_error("Foreach can be called for arrays only", foreachHeader);
			return;
		}
		
		String elemName = foreachHeader.getElement();
		
		foreachHeader.obj = SymbolTable.find(elemName);
		
		if (foreachHeader.obj.getKind() != Obj.Var || foreachHeader.obj == SymbolTable.noObj) {
			report_error(elemName + " must be a local or global variable", foreachHeader);
			return;
		}
		
		if (!foreachHeader.obj.getType().equals(arrayObj.getType().getElemType())) {
			report_error("Type mismatch", foreachHeader);
			return;
		}
	}
	
	@Override
	public void visit(StatementForeach statementForeach) {
		this.inLoop--;
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(DesignatorAssignOp designatorAssignOp) {
		Obj designatorObj = designatorAssignOp.getDesignator().obj;
		
		if (designatorObj.equals(SymbolTable.noObj)) {
			return;
		}
		
		if (!this.isAssignable(designatorObj)) {
			report_error("Can't assign to " + designatorObj.getName(), designatorAssignOp);
			return;
		}
		
		if (!designatorAssignOp.getExpr().struct.assignableTo(designatorObj.getType())) {
			report_error("Type mismatch", designatorAssignOp);
			return;
		}
	}
	
	@Override
	public void visit(DesignatorPostfixOp designatorPostfixOp) {
		Obj designatorObj = designatorPostfixOp.getDesignator().obj;
		
		if (designatorObj.equals(SymbolTable.noObj)) {
			return;
		}
		
		if (!this.isAssignable(designatorObj)) {
			report_error("Can't assign to " + designatorObj.getName(), designatorPostfixOp);
			return;
		}
		
		if (designatorObj.getType().getKind() != Struct.Int) {
			report_error(designatorObj.getName() + " must be integer", designatorPostfixOp);
			return;
		}
	}
	
	@Override
	public void visit(DesignatorMultiple designatorMultiple) {
		ObjList designatorObjList = designatorMultiple.getDesignatorList().objlist;
		
		if (designatorMultiple.getDesignatorList() instanceof DesignatorNone) {
			designatorObjList = new ObjList();
		}
		
		designatorObjList.getList().forEach(designatorObj -> {
			if (designatorObj != SymbolTable.noObj && !this.isAssignable(designatorObj)) {
				report_error("Can't assign to " + designatorObj.getName(), designatorMultiple);
			}
		});
		
		Obj arrayObj = designatorMultiple.getDesignator().obj;
		
		if (arrayObj.getType().getKind() != Struct.Array) {
			report_error("Symbol on right side of a multiple assignment must be an array", designatorMultiple);
			return;
		}
		
		for (int ind = 0; ind < designatorObjList.size(); ind++) {
			if (!designatorObjList.assignableFrom(arrayObj, ind)) {
				report_error("Type mismatch for " + designatorObjList.getList().get(ind).getName(), designatorMultiple);
			}
		}
	}
	
	@Override
	public void visit(DesignatorPresent designatorPresent) {
		designatorPresent.objlist = designatorPresent.getDesignatorList().objlist;
		designatorPresent.objlist.add(designatorPresent.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorSkip designatorSkip) {
		designatorSkip.objlist = designatorSkip.getDesignatorList().objlist;
		designatorSkip.objlist.add(SymbolTable.noObj);
	}
	
	@Override
	public void visit(DesignatorDone designatorDone) {
		designatorDone.objlist = new ObjList();
		designatorDone.objlist.add(designatorDone.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorNone designatorNone) {
		designatorNone.objlist = new ObjList();
		designatorNone.objlist.add(SymbolTable.noObj);
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(DesignatorIdent designatorIdent) {
		String designatorName = designatorIdent.getDesignatorName();
		
		if ((designatorIdent.obj = SymbolTable.find(designatorName)).equals(SymbolTable.noObj)) {
			report_error("Symbol " + designatorName + " doesn't exist", designatorIdent);
		}
	}
	
	@Override
	public void visit(DesignatorClass designatorClass) {
		designatorClass.obj = SymbolTable.noObj;
		
		Obj classObj = designatorClass.getDesignator().obj;
		
		if (classObj.equals(SymbolTable.noObj)) {
			return;
		}
		
		Struct classType = classObj.getType();
		
		if (classType.getKind() != Struct.Class) {
			report_error("Symbol " + classObj.getName() + " is not a class", designatorClass);
			return;
		}
		
		String fieldName = designatorClass.getFieldName();
		Obj fieldObj = classType.getMembersTable().searchKey(fieldName);
		
		if (fieldObj == null) {
			report_error("Symbol " + classObj.getName() + " doesn't have field " + fieldName, designatorClass);
			return;
		}
		
		designatorClass.obj = fieldObj;
	}
	
	@Override
	public void visit(LoadDesignatorArray loadDesignatorArray) {
		loadDesignatorArray.obj = loadDesignatorArray.getDesignator().obj;
	}
	
	@Override
	public void visit(DesignatorArray designatorArray) {
		designatorArray.obj = SymbolTable.noObj;
		
		Obj arrayObj = designatorArray.getLoadDesignatorArray().obj;
		
		if (arrayObj.equals(SymbolTable.noObj)) {
			return;
		}
		
		Struct arrayType = arrayObj.getType();
		
		if (arrayType.getKind() != Struct.Array) {
			report_error("Symbol " + arrayObj.getName() + " is not an array", designatorArray);
			return;
		}
		
		Struct exprType = designatorArray.getExpr().struct;
		
		if (exprType.getKind() != Struct.Int) {
			report_error("Array must be indexed with an integer", designatorArray);
			return;
		}
		
		designatorArray.obj = new Obj(Obj.Elem, arrayObj.getName() + "[]", arrayType.getElemType());
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(FactorDesignator factorDesignator) {
		factorDesignator.obj = factorDesignator.getDesignator().obj;
	}
	
	@Override
	public void visit(FactorDesignatorMethod factorDesignatorMethod) {
		factorDesignatorMethod.obj = factorDesignatorMethod.getMethodCall().getDesignator().obj;
	}
	
	@Override
	public void visit(FactorNum factorNum) {
		int adr = factorNum.getConstVal();
		String name = Integer.toString(adr);
		
		factorNum.obj = new Obj(Obj.Con, name, SymbolTable.intType, adr, 1);
	}
	
	@Override
	public void visit(FactorChar factorChar) {
		int adr = factorChar.getConstVal();
		String name = Integer.toString(adr);
		
		factorChar.obj = new Obj(Obj.Con, name, SymbolTable.charType, adr, 1);
	}
	
	@Override
	public void visit(FactorBool factorBool) {
		int adr = factorBool.getConstVal();
		String name = Integer.toString(adr);
		
		factorBool.obj = new Obj(Obj.Con, name, SymbolTable.boolType, adr, 1);
	}
	
	@Override
	public void visit(FactorParen factorParen) {
		factorParen.obj = new Obj(Obj.NO_VALUE, "", factorParen.getExpr().struct);
	}
	
	@Override
	public void visit(FactorNewArray factorNewArray) {
		factorNewArray.obj = SymbolTable.noObj;
		
		if (factorNewArray.getType().struct == SymbolTable.noType) {
			return;
		}
		
		if (factorNewArray.getExpr().struct == SymbolTable.noType) {
			return;
		}
		
		if (factorNewArray.getExpr().struct.getKind() != Struct.Int) {
			report_error("Array size must be specified with an integer", factorNewArray);
			return;
		}
		
		factorNewArray.obj = new Obj(Obj.NO_VALUE, "", new Struct(Struct.Array, factorNewArray.getType().struct));
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(FactorSingle factorSingle) {
		factorSingle.struct = factorSingle.getFactor().obj.getType();
	}
	
	@Override
	public void visit(FactorMultiple factorMultiple) {
		factorMultiple.struct = SymbolTable.noType;
		
		int leftType = factorMultiple.getTerm().struct.getKind();
		int rightType = factorMultiple.getFactor().obj.getType().getKind();
		
		if (leftType == Struct.None || rightType == Struct.None) {
			return;
		}
		
		if (!(leftType == Struct.Int && rightType == Struct.Int)) {
			report_error("Both operands must be of type int for arithmetic operations", factorMultiple);
			return;
		}
		
		factorMultiple.struct = SymbolTable.intType;
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(TermPositive termPositive) {
		termPositive.struct = termPositive.getTerm().struct;
	}
	
	@Override
	public void visit(TermNegative termNegative) {
		termNegative.struct = SymbolTable.noType;
		
		if (termNegative.getTerm().struct.getKind() == Struct.None) {
			return;
		}
		
		if (termNegative.getTerm().struct.getKind() != Struct.Int) {
			report_error("Operand must be of type int for negation", termNegative);
			return;
		}
		
		termNegative.struct = SymbolTable.intType;
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(TermSingle termSingle) {
		termSingle.struct = termSingle.getTermFirst().struct;
	}
	
	@Override
	public void visit(TermMultiple termMultiple) {
		termMultiple.struct = SymbolTable.noType;
		
		int leftType = termMultiple.getExpr().struct.getKind();
		int rightType = termMultiple.getTerm().struct.getKind();
		
		if (leftType == Struct.None || rightType == Struct.None) {
			return;
		}
		
		if (!(leftType == Struct.Int && rightType == Struct.Int)) {
			report_error("Both operands must be of type int for arithmetic operations", termMultiple);
			return;
		}
		
		termMultiple.struct = SymbolTable.intType;
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(CondExpr condExpr) {
		if (condExpr.getExpr().struct.getKind() == Struct.None) {
			return;
		}
		
		if (condExpr.getExpr().struct != SymbolTable.boolType) {
			report_error("Expression in condition must be boolean", condExpr);
			return;
		}
	}
	
	@Override
	public void visit(CondRelOp condRelOp) {
		Struct leftType = condRelOp.getExpr().struct;
		Struct rightType = condRelOp.getExpr1().struct;
		
		if (leftType.getKind() == Struct.None || rightType.getKind() == Struct.None) {
			return;
		}
		
		if (!leftType.compatibleWith(rightType)) {
			report_error("Types must be compatible to be compared to each other", condRelOp);
		}
		
		if ((leftType.isRefType() || rightType.isRefType()) && condRelOp.getRelOp().opcode.getOpCode() > Code.ne) {
			report_error("Only == and != are valid for reference types", condRelOp);
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(RelOpEq relOpEq) {
		relOpEq.opcode = new OpCode(Code.eq);
	}
	
	@Override
	public void visit(RelOpNe relOpNe) {
		relOpNe.opcode = new OpCode(Code.ne);
	}
	
	@Override
	public void visit(RelOpGt relOpGt) {
		relOpGt.opcode = new OpCode(Code.gt);
	}
	
	@Override
	public void visit(RelOpGe relOpGe) {
		relOpGe.opcode = new OpCode(Code.ge);
	}
	
	@Override
	public void visit(RelOpLt relOpLt) {
		relOpLt.opcode = new OpCode(Code.lt);
	}
	
	@Override
	public void visit(RelOpLe relOpLe) {
		relOpLe.opcode = new OpCode(Code.le);
	}
	
	//------------------------------------------------------------------------
	
	public void visit(AddOpAdd addOpAdd) {
		addOpAdd.opcode = new OpCode(Code.add);
	}
	
	public void visit(AddOpSub addOpSub) {
		addOpSub.opcode = new OpCode(Code.sub);
	}
	
	public void visit(MulOpMul mulOpMul) {
		mulOpMul.opcode = new OpCode(Code.mul);
	}
	
	public void visit(MulOpDiv mulOpDiv) {
		mulOpDiv.opcode = new OpCode(Code.div);
	}
	
	public void visit(MulOpRem mulOpRem) {
		mulOpRem.opcode = new OpCode(Code.rem);
	}
	
	//------------------------------------------------------------------------
	
	private boolean symbolExists(String symbolName, SyntaxNode syntaxNode) {
		if (SymbolTable.currentScope.findSymbol(symbolName) != null) {
			report_error("Symbol " + symbolName + " already exists", syntaxNode);
			return true;
		}
		return false;
	}
	
	private boolean isAssignable(Obj obj) {
		return obj.getKind() == Obj.Var || obj.getKind() == Obj.Elem || obj.getKind() == Obj.Fld;
	}
	
	private boolean isBuiltin(Struct type) {
		return type.getKind() == Struct.Int || type.getKind() == Struct.Char || type.getKind() == Struct.Bool;
	}
	
	//------------------------------------------------------------------------
	
	public boolean errorDetected = false;
	
	private void report_error(String message, SyntaxNode info) {
		errorDetected = true;

		StringBuilder msg = new StringBuilder();

		if (info != null) {
			msg.append("Error on line ").append(info.getLine()).append("!");
		}

		msg.append(" ").append(message);

		System.err.println(msg.toString());
	}

	private void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);

		if (info != null) {
			msg.append(" na liniji ").append(info.getLine());
		}

		System.out.println(msg.toString());
	}
}
