package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.concepts.*;
import rs.etf.pp1.mj.runtime.Code;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	private int dataSize = 0;
	
	public int getDataSize() {
		return this.dataSize;
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(ProgramHeader programHeader) {
		if (this.symbolExists(programHeader.getProgName(), programHeader))
			programHeader.obj = SymbolTable.insert(Obj.Prog, "+", SymbolTable.noType);
		else
			programHeader.obj = SymbolTable.insert(Obj.Prog, programHeader.getProgName(), SymbolTable.noType);
		
		SymbolTable.openScope();
	}
	
	@Override
	public void visit(Program program) {
		Obj mainMethod = SymbolTable.currentScope.findSymbol("main");
		
		if (mainMethod == null)
			report_error("Global main method must exist!", null);
		else if (mainMethod.getType() != SymbolTable.noType)
			report_error("Main method must be void!", null);
		else if (mainMethod.getLevel() != 0)
			report_error("Main method can't have parameters!", null);
		
		SymbolTable.chainLocalSymbols(program.getProgramHeader().obj);
		SymbolTable.closeScope();
	}
	
	//------------------------------------------------------------------------	
	
	private Struct currentType = SymbolTable.noType;
	
	@Override
	public void visit(Type type) {
		type.struct = SymbolTable.noType;
		
		Obj typeObj = SymbolTable.find(type.getTypeName());

		if (typeObj == SymbolTable.noObj)
			this.report_error("Type " + type.getTypeName() + " not found!", type);
		else if (typeObj.getKind() != Obj.Type)
			this.report_error(type.getTypeName() + " is not a type!", type);
		else
			type.struct = typeObj.getType();

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
		if (this.currentType == SymbolTable.noType || this.symbolExists(constName, syntaxNode))
			return;
		
		if (!SymbolTable.equals(this.currentType, constType)) {
			report_error("Type doesn't match for constant " + constName, syntaxNode);
			return;
		}
		
		SymbolTable.insert(Obj.Con, constName, constType).setAdr(constValue);
	}
	
	@Override
	public void visit(ConstDecl constDecl) {
		if (this.currentType != SymbolTable.intType 
				&& this.currentType != SymbolTable.charType 
				&& this.currentType != SymbolTable.boolType) {
			report_error("Constants must have builtin type", constDecl);
		}
	}
	
	//------------------------------------------------------------------------
	
	private String className = null;
	private Struct classType = null;
	private Struct classParent = null;
	
	private VirtualMethods virtualMethods = new VirtualMethods();
	
	@Override
	public void visit(ClassHeader classHeader) {
		this.className = classHeader.getClassName();
		this.classType = SymbolTable.noType;
		
		if (this.symbolExists(this.className, classHeader))
			return;
		
		this.classType = new Struct(Struct.Class);
		
		SymbolTable.insert(Obj.Type, this.className, this.classType);
		SymbolTable.openScope();
		
		SymbolTable.insert(Obj.Fld, "-TVF", SymbolTable.intType);
		
		this.generateDefaultConstructor();
		
		if (this.classParent != null)
			this.insertParentMembers();
	}
	
	@Override
	public void visit(ClassParentYes classParent) {
		this.classParent = SymbolTable.noType;
		
		if (classParent.getType().struct.getKind() != Struct.Class) {
			report_error("Classes can only extend other classes!", classParent);
			return;
		}
		
		this.classParent = classParent.getType().struct;
	}
	
	private void insertParentMembers() {
		if (this.classParent == SymbolTable.noType)
			return;
		
		for (Obj member : this.classParent.getMembers()) {
			if (member.getKind() == Obj.Fld) {
				if (member.getName().equals("-TVF"))
					continue;
				
				SymbolTable.insert(Obj.Fld, 
						member.getName(),
						member.getType()).setAdr(member.getAdr());
			} else {
				if (member.getName().charAt(0) == '-')
					continue;
				
				Obj childMethod = SymbolTable.insert(Obj.Meth, 
						member.getName(),
						member.getType());
				
				SymbolTable.openScope();
				SymbolTable.insert(Obj.Var, "this", this.classType);
				
				for (Obj parentParam : member.getLocalSymbols()) {
					if (parentParam.getName().equals("this"))
						continue;
					
					Obj childParam = SymbolTable.insert(Obj.Var, 
							parentParam.getName(),
							parentParam.getType());
					
					childParam.setAdr(parentParam.getAdr());
					childParam.setFpPos(parentParam.getFpPos());
				}
				
				childMethod.setLevel(member.getLevel());
				childMethod.setFpPos(member.getFpPos());
				
				SymbolTable.chainLocalSymbols(childMethod);
				SymbolTable.closeScope();
				
				this.virtualMethods.addParentMethod(member.getName(), member);
				this.virtualMethods.addChilMethod(member.getName(), childMethod);
			}
		}
		
		this.classType.setElementType(this.classParent);
	}
	
	@Override
	public void visit(ClassDecl classDecl) {
		if (this.classType == SymbolTable.noType) {
			return;
		} else {
			classDecl.struct = this.classType;
			classDecl.getClassHeader().virtualmethods = this.virtualMethods;
			
			SymbolTable.chainLocalSymbols(classDecl.struct);
			SymbolTable.closeScope();
		}
		
		this.virtualMethods = new VirtualMethods();
		
		this.constructorCnt = 0;
		
		this.classType = null;
		this.className = null;
		this.classParent = null;
	}
	
	//------------------------------------------------------------------------
	
	private int constructorCnt = 0;
	
	@Override
	public void visit(ConstructorSignature constructorSignature) {
		constructorSignature.obj = SymbolTable.noObj;
	
		if (this.classType == SymbolTable.noType)
			return;

		SymbolTable.currentScope.getLocals().deleteKey("-");
		
		if (!constructorSignature.getClassName().equals(this.className)) {
			report_error("Constructor must have the same name as class!", constructorSignature);
			return;
		}
		
		for (Obj constructor : SymbolTable.currentScope.values()) {
			if (constructor.getKind() != Obj.Meth
					|| constructor.getName().charAt(0) != '-'
					|| constructor.getLevel() - 1 != this.formParList.size())
				continue;
			
			boolean check = true;
			
			for (Obj param : constructor.getLocalSymbols()) {
				if (param.getName().equals("this") || param.getFpPos() < 0)
					continue;
				
				if (!this.formParList.equalTo(param.getType(), param.getFpPos() - 1)) {
					check = false;
					break;
				}
			}
			
			if (check) {
				report_error("Constructors can't have the same formal parameters!", constructorSignature);
				return;
			}
		}
		
		this.currentMethod = SymbolTable.noType;
		
		constructorSignature.obj = SymbolTable.insert(Obj.Meth, "-" + this.constructorCnt++, this.currentMethod);
		
		SymbolTable.openScope();
		
		Obj thisObj = SymbolTable.insert(Obj.Var, "this", this.classType);
		thisObj.setFpPos(0);
		thisObj.setLevel(1);
		
		for (Obj paramObj : this.formParList.getList()) {
			SymbolTable.currentScope.addToLocals(paramObj);
		}
	}
	
	@Override
	public void visit(ConstructorDecl constructorDecl) {
		constructorDecl.obj = constructorDecl.getConstructorSignature().obj;
		constructorDecl.obj.setLevel(this.formParList.size() + 1);
		
		this.formParList.clear();
		
		if (constructorDecl.obj == SymbolTable.noObj)
			return;
		
		SymbolTable.chainLocalSymbols(constructorDecl.obj);
		SymbolTable.closeScope();
		
		this.currentMethod = null;
	}
	
	@Override
	public void visit(ClassMethodsNo classMethodsNo) {
		this.generateDefaultConstructor();
	}
	
	private void generateDefaultConstructor() {
		Obj defaultObj = SymbolTable.insert(Obj.Meth, "-", SymbolTable.noType);
		defaultObj.setLevel(1);
		
		SymbolTable.openScope();
		SymbolTable.insert(Obj.Var, "this", this.classType);
		SymbolTable.chainLocalSymbols(defaultObj);
		SymbolTable.closeScope();
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(VarDecl varDecl) {
		if (this.classType == SymbolTable.noType || this.currentMethod == SymbolTable.nullType)
			return;
		
		String varDeclName = varDecl.getVarName();
		
		if (this.symbolExists(varDeclName, varDecl))
			return;
		
		Struct varType;
		
		if (varDecl.getVarArray() instanceof VarArrayYes)
			varType = new Struct(Struct.Array, this.currentType);
		else
			varType = this.currentType;
		
		int varKind = Obj.Var;
		
		if (this.currentMethod == null) {
			if (this.classType != null)
				varKind = Obj.Fld;
			else
				this.dataSize++;
		}
		
		SymbolTable.insert(varKind, varDeclName, varType).setFpPos(-1);
	}
	
	//------------------------------------------------------------------------
	
	private Struct currentMethod = null;
	
	@Override
	public void visit(MethodSignature methodSignature) {
		methodSignature.obj = SymbolTable.noObj;
		
		if (this.classType == SymbolTable.noType)
			return;
		
		if (methodSignature.getMethodType() instanceof VoidYes)
			this.currentMethod = SymbolTable.noType;
		else
			this.currentMethod = this.currentType;
		
		int isClassMethod = 0;
		
		String methodName = methodSignature.getMethodName();
		
		if (this.classType != null) {
			Obj thisObj = new Obj(Obj.Var, "this", this.classType, 0, 1);
			
			thisObj.setFpPos(0);
			
			this.formParList.addAtIndex(thisObj, 0);
			
			if (this.virtualMethods.isOverride(methodName)) {
				if (this.virtualMethods.validParams(methodName, formParList)
						&& this.virtualMethods.validReturnType(methodName, this.currentMethod)) {
					this.virtualMethods.overrideMethod(methodName);
					SymbolTable.currentScope.getLocals().deleteKey(methodName);
				} else {
					report_error("Invalid override!", methodSignature);
					return;
				}
			}
			
			isClassMethod = -1;
		}
		
		if (this.symbolExists(methodName, methodSignature)) {
			this.currentMethod = SymbolTable.nullType;
			return;
		}
		
		methodSignature.obj = SymbolTable.insert(Obj.Meth, methodName, this.currentMethod);
		methodSignature.obj.setFpPos(isClassMethod);
		
		SymbolTable.openScope();
		
		for (Obj paramObj : this.formParList.getList()) {
			SymbolTable.currentScope.addToLocals(paramObj);
		}
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		methodDecl.obj = methodDecl.getMethodSignature().obj;
		methodDecl.obj.setLevel(this.formParList.size());
		
		this.formParList.clear();
		
		if (methodDecl.obj == SymbolTable.noObj)
			return;
		
		SymbolTable.chainLocalSymbols(methodDecl.obj);
		SymbolTable.closeScope();
		
		this.currentMethod = null;
	}
	
	//------------------------------------------------------------------------
	
	private ObjList formParList = new ObjList();
	
	@Override
	public void visit(FormPar formPar) {
		Struct formParType;
		
		if (formPar.getFormParArray() instanceof FormParArrayYes)
			formParType = new Struct(Struct.Array, this.currentType);
		else
			formParType = currentType;
		
		Obj paramObj = new Obj(Obj.Var, formPar.getFormParName(), formParType, 0, 1);
		
		paramObj.setFpPos(this.formParList.size() + (this.classType != null ? 1 : 0));
		
		this.formParList.add(paramObj);
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(MethodDesignator methodDesignator) {
		methodDesignator.obj = methodDesignator.getDesignator().obj;
	}
	
	@Override
	public void visit(MethodCall methodCall) {
		Obj methodObj = methodCall.getMethodDesignator().obj;
		
		if (methodObj == SymbolTable.noObj)
			return;
		
		if (methodObj.getKind() != Obj.Meth) {
			report_error("Symbol " + methodObj.getName() + " is not a method or a function!", methodCall);
			return;
		}
		
		ObjList paramList = methodCall.getActPars().objlist;
		
		if (paramList.size() != methodObj.getLevel() + methodObj.getFpPos()) {
			report_error("Invalid number of arguments for method " + methodObj.getName() + "!", methodCall);
			return;
		}
		
		for (Obj arg : methodObj.getLocalSymbols()) {
			if (methodObj.getFpPos() == -1 && arg.getName().equals("this"))
				continue;
			
			if (arg.getFpPos() >= 0 && !paramList.assignableTo(arg.getType(), arg.getFpPos() + methodObj.getFpPos())) {
				report_error("Type mismatch for " + arg.getFpPos() + ". argument!", methodCall);
				return;
			}
		}
		
		if (methodObj.getFpPos() == 0)
			report_info("Global function " + methodObj.getName(), methodCall);
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
		if (this.currentMethod == null)
			return;
		
		if (this.currentMethod != SymbolTable.noType)
			report_error("Non-void method must return an expression!", statementReturnVoid.getParent());
	}
	
	@Override
	public void visit(StatementReturnExpr statementReturnExpr) {
		if (this.currentMethod == null)
			return;
		
		Struct exprType = statementReturnExpr.getExpr().struct;
		
		if (exprType == SymbolTable.noType)
			return;
		
		if (!SymbolTable.assignable(exprType, this.currentMethod))
			report_error("Type of returned expression must be assignable to method type!", statementReturnExpr);
	}
	
	@Override
	public void visit(StatementRead statementRead) {
		Obj designatorObj = statementRead.getDesignator().obj;
		
		if (!this.isAssignable(designatorObj)) {
			report_error("Can't assign to " + designatorObj.getName() + "!", statementRead);
			return;
		}
		
		if (!this.isBuiltin(designatorObj.getType()))
			report_error("Only builtin types can be read!", statementRead);
	}
	
	@Override
	public void visit(StatementPrint statementPrint) {
		Struct type = statementPrint.getExpr().struct;
		
		if (type.getKind() == Struct.None)
			return;
		
		if (!this.isBuiltin(statementPrint.getExpr().struct)) {
			report_error("Only builtin types can be printed!", statementPrint);
			return;
		}
	}
	
	//------------------------------------------------------------------------
	
	private int inLoop = 0;
	
	@Override
	public void visit(StatementContinue statementContinue) {
		if (this.inLoop == 0) {
			report_error("Continue can only be used within a loop", statementContinue.getParent());
			return;
		}
	}
	
	@Override
	public void visit(StatementBreak statementBreak) {
		if (this.inLoop == 0) {
			report_error("Break can only be used within a loop", statementBreak.getParent());
			return;
		}
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(WhileHeader whileHeader) {
		this.inLoop++;
	}
	
	@Override
	public void visit(StatementWhile statementWhile) {
		this.inLoop--;
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(ForeachHeader foreachHeader) {
		this.inLoop++;
		
		Obj arrayObj = foreachHeader.getDesignator().obj;
		
		if (arrayObj == SymbolTable.noObj) {
			return;
		}
		
		if (arrayObj.getType().getKind() != Struct.Array) {
			report_error("Foreach can be called for arrays only!", foreachHeader);
			return;
		}
		
		String elemName = foreachHeader.getElement();
		
		foreachHeader.obj = SymbolTable.find(elemName);
		
		if (foreachHeader.obj.getKind() != Obj.Var || foreachHeader.obj == SymbolTable.noObj) {
			report_error(elemName + " must be a local or global variable!", foreachHeader);
			return;
		}
		
		if (!SymbolTable.equals(foreachHeader.obj.getType(), arrayObj.getType().getElemType())) {
			report_error("Type mismatch!", foreachHeader);
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
		
		if (designatorObj == SymbolTable.noObj)
			return;
		
		if (!this.isAssignable(designatorObj)) {
			report_error("Can't assign to " + designatorObj.getName() + "!", designatorAssignOp);
			return;
		}
		
		if (!SymbolTable.assignable(designatorAssignOp.getExpr().struct, designatorObj.getType())) {
			report_error("Type mismatch!", designatorAssignOp);
			return;
		}
	}
	
	@Override
	public void visit(DesignatorPostfixOp designatorPostfixOp) {
		Obj designatorObj = designatorPostfixOp.getDesignator().obj;
		
		if (designatorObj == SymbolTable.noObj)
			return;
		
		if (!this.isAssignable(designatorObj)) {
			report_error("Can't assign to " + designatorObj.getName() + "!", designatorPostfixOp);
			return;
		}
		
		if (designatorObj.getType().getKind() != Struct.Int) {
			report_error(designatorObj.getName() + " must be integer!", designatorPostfixOp);
			return;
		}
	}
	
	@Override
	public void visit(DesignatorMultiple designatorMultiple) {
		ObjList designatorObjList = designatorMultiple.getDesignatorList().objlist;
		
		if (designatorMultiple.getDesignatorList() instanceof DesignatorNone)
			designatorObjList = new ObjList();
		
		designatorObjList.getList().forEach(designatorObj -> {
			if (designatorObj != SymbolTable.noObj && !this.isAssignable(designatorObj))
				report_error("Can't assign to " + designatorObj.getName() + "!", designatorMultiple);
		});
		
		Obj arrayObj = designatorMultiple.getDesignator().obj;
		
		if (arrayObj.getType().getKind() != Struct.Array) {
			report_error("Symbol on right side of a multiple assignment must be an array!", designatorMultiple);
			return;
		}
		
		for (int ind = 0; ind < designatorObjList.size(); ind++) {
			if (!designatorObjList.assignableFrom(arrayObj.getType().getElemType(), ind))
				report_error("Type mismatch for " + designatorObjList.getList().get(ind).getName() + "!", designatorMultiple);
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
		
		designatorIdent.obj = SymbolTable.find(designatorName);
		
		if (designatorIdent.obj.getKind() == Obj.Type) {
			report_error("Designator can't be a type!", designatorIdent);
			return;
		}
		
		if (designatorIdent.obj == SymbolTable.noObj) {
			report_error("Symbol " + designatorName + " doesn't exist!", designatorIdent);
			return;
		}
		
		if (designatorIdent.obj.getKind() == Obj.Con) {
			report_info("Constant " + designatorName, designatorIdent);
		} else if (designatorIdent.obj.getKind() == Obj.Var) {
			if (designatorIdent.obj.getLevel() == 0) {
				report_info("Global variable " + designatorName, designatorIdent);
			}
			else {
				if (designatorIdent.obj.getFpPos() < 0)
					report_info("Local variable " + designatorName, designatorIdent);
				else
					report_info("Formal parameter " + designatorName, designatorIdent);
			}
		} else if (designatorIdent.obj.getKind() == Obj.Fld) {
			report_info("Class field " + designatorName, designatorIdent);
		} else if (designatorIdent.obj.getKind() == Obj.Meth && designatorIdent.obj.getFpPos() < 0) {
			report_info("Class method " + designatorName, designatorIdent);
		}
	}
	
	@Override
	public void visit(DesignatorClass designatorClass) {
		designatorClass.obj = SymbolTable.noObj;
		
		Obj classObj = designatorClass.getDesignator().obj;
		
		if (classObj == SymbolTable.noObj)
			return;
		
		Struct classType = classObj.getType();
		
		if (classType.getKind() != Struct.Class) {
			report_error("Symbol " + classObj.getName() + " is not a class!", designatorClass);
			return;
		}
		
		Obj memberObj = null;
		String memberName = designatorClass.getFieldName();
		
		if (classType == this.classType)
			memberObj = SymbolTable.currentScope.getOuter().findSymbol(memberName);
		else
			memberObj = classType.getMembersTable().searchKey(memberName);
		
		if (memberObj == null || memberObj == SymbolTable.noObj) {
			report_error("Symbol " + classObj.getName() + " doesn't have field " + memberName + "!", designatorClass);
			return;
		}
		
		if (memberObj.getKind() == Obj.Fld)
			report_info("Class field " + memberName, designatorClass);
		else if (memberObj.getKind() == Obj.Meth)
			report_info("Class method " + memberName, designatorClass);
		
		designatorClass.obj = memberObj;
	}
	
	@Override
	public void visit(LoadDesignatorArray loadDesignatorArray) {
		loadDesignatorArray.obj = loadDesignatorArray.getDesignator().obj;
	}
	
	@Override
	public void visit(DesignatorArray designatorArray) {
		designatorArray.obj = SymbolTable.noObj;
		
		Obj arrayObj = designatorArray.getLoadDesignatorArray().obj;
		
		if (arrayObj == SymbolTable.noObj) {
			return;
		}
		
		Struct arrayType = arrayObj.getType();
		
		if (arrayType.getKind() != Struct.Array) {
			report_error("Symbol " + arrayObj.getName() + " is not an array!", designatorArray);
			return;
		}
		
		Struct exprType = designatorArray.getExpr().struct;
		
		if (exprType.getKind() != Struct.Int) {
			report_error("Array must be indexed with an integer!", designatorArray);
			return;
		}
		
		report_info("Array element " + arrayObj.getName(), designatorArray);
		
		designatorArray.obj = new Obj(Obj.Elem, arrayObj.getName() + "[]", arrayType.getElemType());
	}
	
	//------------------------------------------------------------------------
	
	@Override
	public void visit(FactorDesignator factorDesignator) {
		factorDesignator.obj = factorDesignator.getDesignator().obj;
		
		if (factorDesignator.getDesignator().obj.getKind() == Obj.Meth)
			report_error("Method must be called!", factorDesignator);
	}
	
	@Override
	public void visit(FactorDesignatorMethod factorDesignatorMethod) {
		factorDesignatorMethod.obj = factorDesignatorMethod.getMethodCall().getMethodDesignator().obj;
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
		
		if (factorNewArray.getType().struct == SymbolTable.noType ||
				factorNewArray.getExpr().struct == SymbolTable.noType)
			return;
		
		if (factorNewArray.getExpr().struct.getKind() != Struct.Int) {
			report_error("Array size must be specified with an integer!", factorNewArray);
			return;
		}
		
		factorNewArray.obj = new Obj(Obj.NO_VALUE, "", new Struct(Struct.Array, factorNewArray.getType().struct));
	}
	
	@Override
	public void visit(FactorNewClass factorNewClass) {
		factorNewClass.obj = SymbolTable.noObj;
		factorNewClass.getNewClass().obj = SymbolTable.noObj;
		
		Struct classType = factorNewClass.getNewClass().getType().struct;
		
		if (classType.getKind() != Struct.Class) {
			report_error("Type must be a class!", factorNewClass);
			return;
		}
		
		boolean checkConstructor = false;
		
		for (Obj constructor : classType.getMembers()) {
			if (constructor.getKind() != Obj.Meth
					|| constructor.getName().charAt(0) != '-'
					|| constructor.getLevel() - 1 != factorNewClass.getActPars().objlist.size())
				continue;
			
			boolean checkParams = true;
			
			for (Obj param : constructor.getLocalSymbols()) {
				if (param.getName().equals("this") || param.getFpPos() < 0)
					continue;
				
				if (!factorNewClass.getActPars().objlist.assignableTo(param.getType(), param.getFpPos() - 1)) {
					checkParams = false;
					break;
				}
			}
			
			if (checkParams) {
				factorNewClass.getNewClass().obj = constructor;
				checkConstructor = true;
				break;
			}
		}
		
		if (!checkConstructor) {
			report_error("No callable constructor with given types found!", factorNewClass);
			return;
		}
		
		report_info("Creating new instance of class " + factorNewClass.getNewClass().getType().getTypeName(), factorNewClass);
		
		factorNewClass.obj = new Obj(Obj.Var, "", classType);
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
			report_error("Both operands must be of type int for arithmetic operations!", factorMultiple);
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
			report_error("Operand must be of type int for negation!", termNegative);
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
			report_error("Both operands must be of type int for arithmetic operations!", termMultiple);
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
			report_error("Expression in condition must be boolean!", condExpr);
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
		
		if (!SymbolTable.compatible(leftType, rightType))
			report_error("Types must be compatible to be compared to each other!", condRelOp);
		
		if ((leftType.isRefType() || rightType.isRefType()) && condRelOp.getRelOp().opcode.getOpCode() > Code.ne)
			report_error("Only == and != are valid for reference types!", condRelOp);
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
			report_error("Symbol " + symbolName + " already exists!", syntaxNode);
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
	
	private boolean errorDetected = false;
	
	public boolean hasErrors() {
		return this.errorDetected;
	}
	
	private void report_error(String message, SyntaxNode info) {
		errorDetected = true;

		StringBuilder msg = new StringBuilder("Semantic error");

		if (info != null) {
			msg.append(" on line ").append(info.getLine());
		}

		msg.append(":").append(" ").append(message);

		System.err.println(msg.toString());
	}

	private void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);

		if (info != null) {
			msg.append(" detected on line ").append(info.getLine());
		}

		System.out.println(msg.toString());
	}
}
