package rs.ac.bg.etf.pp2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import rs.ac.bg.etf.pp1.SymbolTable;
import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp2.ir.*;
import rs.ac.bg.etf.pp2.ir.IRInstructions.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class IRGenerator extends VisitorAdaptor {

	private IRProgram program;
	private IRMethod currentMethod;
	private IRBasicBlock currentBlock;
	private TempGenerator temps;

	private Stack<Integer> valueStack = new Stack<>();

	private Map<String, Struct> classes = new HashMap<>();
	private Map<String, Integer> tvfAdr = new HashMap<>();
	private Map<String, rs.ac.bg.etf.pp1.VirtualMethods> classVirtualMethods = new HashMap<>();

	private Stack<Stack<IRBasicBlock>> falseJumpTargets = new Stack<>();
	private Stack<Stack<IRBasicBlock>> trueJumpTargets = new Stack<>();
	private Stack<Stack<IRBasicBlock>> breakTargets = new Stack<>();
	private Stack<IRBasicBlock> loopHeaders = new Stack<>();
	private Stack<IRBasicBlock> foreachEndBlocks = new Stack<>();
	private Stack<IRBasicBlock> skipElseBlocks = new Stack<>();

	// Foreach: [arrayTemp, indexTemp]
	private Stack<int[]> foreachState = new Stack<>();

	private Stack<Obj> methodCallStack = new Stack<>();
	private Stack<List<Integer>> methodArgStack = new Stack<>();

	public IRProgram getProgram() {
		return program;
	}

	private int newTemp() { return temps.newTemp(); }

	private void emit(IRInstruction instr) { currentBlock.add(instr); }

	private void switchToBlock(IRBasicBlock block) { currentBlock = block; }

	@Override
	public void visit(ProgramHeader programHeader) {
		program = new IRProgram(programHeader.getProgName());
		generateBuiltins();
	}

	@Override
	public void visit(Program prog) { }

	@Override
	public void visit(ClassHeader classHeader) {
		
		
		classVirtualMethods.put(classHeader.getClassName(), classHeader.virtualmethods);
	}

	@Override
	public void visit(ClassDecl classDecl) {
		this.classes.put(classDecl.getClassHeader().getClassName(), classDecl.struct);
		this.tvfAdr.put(classDecl.getClassHeader().getClassName(), Code.dataSize);
		for (Obj method : classDecl.struct.getMembers()) {
			if (method.getKind() != Obj.Meth || method.getName().charAt(0) == '-')
				continue;
			Code.dataSize += method.getName().length() + 2;
		}
		Code.dataSize += 1;
	}

	@Override
	public void visit(MethodSignature methodSignature) {
		Obj methodObj = methodSignature.obj;
		String name = methodSignature.getMethodName();

		temps = new TempGenerator(name);
		currentMethod = new IRMethod(name, methodObj);
		currentMethod.setParamCount(methodObj.getLevel());
		currentMethod.setLocalCount(methodObj.getLocalSymbols().size());

		IRBasicBlock entryBlock = temps.newBlock("entry");
		currentMethod.addBlock(entryBlock);
		switchToBlock(entryBlock);

		emit(new Enter(methodObj.getLevel(), methodObj.getLocalSymbols().size()));

		if (name.equals("main"))
			generateTvf();

		program.addMethod(currentMethod);
	}

	@Override
	public void visit(MethodDecl methodDecl) {
		Obj methodObj = methodDecl.obj;
		if (methodObj.getType().equals(SymbolTable.noType)) {
			emit(new Exit());
			emit(new Return());
		} else {
			emit(new Trap(1));
		}
	}

	@Override
	public void visit(ConstructorSignature constructorSignature) {
		Obj methodObj = constructorSignature.obj;
		String name = "-ctor-" + methodObj.getName();

		temps = new TempGenerator(name);
		currentMethod = new IRMethod(name, methodObj);
		currentMethod.setParamCount(methodObj.getLevel());
		currentMethod.setLocalCount(methodObj.getLocalSymbols().size());

		IRBasicBlock entryBlock = temps.newBlock("entry");
		currentMethod.addBlock(entryBlock);
		switchToBlock(entryBlock);

		emit(new Enter(methodObj.getLevel(), methodObj.getLocalSymbols().size()));
		program.addMethod(currentMethod);
	}

	@Override
	public void visit(ConstructorDecl constructorDecl) {
		emit(new Exit());
		emit(new Return());
	}

	@Override
	public void visit(MethodDesignator methodDesignator) {
		this.methodCallStack.push(methodDesignator.obj);
		this.methodArgStack.push(new ArrayList<>());
	}

	@Override
	public void visit(ExprSingle exprSingle) { collectArg(); }

	@Override
	public void visit(ExprMultiple exprMultiple) { collectArg(); }

	private void collectArg() {
		if (!this.methodCallStack.empty()) {
			int argTemp = valueStack.pop();
			this.methodArgStack.peek().add(argTemp);
		}
	}

	@Override
	public void visit(MethodCall methodCall) {
		Obj methodObj = methodCall.getMethodDesignator().obj;
		List<Integer> args = this.methodArgStack.pop();
		boolean isVoid = methodObj.getType() == SymbolTable.noType;
		int dst = isVoid ? -1 : newTemp();

		if (methodObj.getFpPos() != -1) {
			emit(new Call(dst, methodObj, args));
		} else {
			int objTemp = valueStack.pop();
			emit(new InvokeVirtual(dst, objTemp, methodObj.getName(), args));
		}

		if (!isVoid)
			valueStack.push(dst);
		this.methodCallStack.pop();
	}

	@Override
	public void visit(StatementReturnVoid statementReturnVoid) {
		emit(new Exit());
		emit(new Return());
	}

	@Override
	public void visit(StatementReturnExpr statementReturnExpr) {
		int val = valueStack.pop();
		emit(new ReturnVal(val));
	}

	@Override
	public void visit(StatementRead statementRead) {
		Obj designatorObj = statementRead.getDesignator().obj;
		int dst = newTemp();

		if (designatorObj.getType().getKind() == Struct.Char)
			emit(new Read(IRInstruction.Op.BREAD, dst, designatorObj));
		else
			emit(new Read(IRInstruction.Op.READ, dst, designatorObj));

		// Store to the right target
		if (designatorObj.getKind() == Obj.Elem) {
			int idx = valueStack.pop();
			int arr = valueStack.pop();
			IRInstruction.Op storeOp = (designatorObj.getType().getKind() == Struct.Char)
					? IRInstruction.Op.BASTORE : IRInstruction.Op.ASTORE;
			emit(new AStore(storeOp, arr, idx, dst));
		} else if (designatorObj.getKind() == Obj.Fld) {
			int objTemp = valueStack.pop();
			emit(new PutField(objTemp, designatorObj.getAdr(), dst));
		} else {
			emit(new Store(designatorObj, dst));
		}
	}

	@Override
	public void visit(StatementPrint statementPrint) {
		int val = valueStack.pop();
		Struct type = statementPrint.getExpr().struct;

		int width;
		IRInstruction.Op printOp;

		if (type.getKind() == Struct.Char) {
			width = 1;
			printOp = IRInstruction.Op.BPRINT;
		} else {
			width = 5;
			printOp = IRInstruction.Op.PRINT;
		}

		if (statementPrint.getWidthOptional() instanceof WidthYes)
			width = ((WidthYes) statementPrint.getWidthOptional()).getWidth();

		emit(new Print(printOp, val, width));
	}

	@Override
	public void visit(CondStart condStart) {
		this.falseJumpTargets.push(new Stack<>());
		this.trueJumpTargets.push(new Stack<>());
	}

	@Override
	public void visit(CondExpr condExpr) {
		int val = valueStack.pop();
		int one = newTemp();
		emit(new Const(one, 1));

		IRBasicBlock trueBlock = temps.newBlock("cond_true");
		IRBasicBlock falseBlock = temps.newBlock("cond_false");
		currentMethod.addBlock(trueBlock);
		currentMethod.addBlock(falseBlock);

		emit(new CJump(Code.eq, val, one, trueBlock, falseBlock));
		this.falseJumpTargets.peek().push(falseBlock);
		switchToBlock(trueBlock);
	}

	@Override
	public void visit(CondRelOp condRelOp) {
		int right = valueStack.pop();
		int left = valueStack.pop();

		IRBasicBlock trueBlock = temps.newBlock("cond_true");
		IRBasicBlock falseBlock = temps.newBlock("cond_false");
		currentMethod.addBlock(trueBlock);
		currentMethod.addBlock(falseBlock);

		int relOp = condRelOp.getRelOp().opcode.getOpCode();
		emit(new CJump(relOp, left, right, trueBlock, falseBlock));
		this.falseJumpTargets.peek().push(falseBlock);
		switchToBlock(trueBlock);
	}

	@Override
	public void visit(CondFactSingle condFactSingle) {
		IRBasicBlock trueTarget = temps.newBlock("and_true");
		currentMethod.addBlock(trueTarget);
		emit(new Jump(trueTarget));
		this.trueJumpTargets.peek().push(trueTarget);
	}

	@Override
	public void visit(CondTermSingle condTermSingle) {
		Stack<IRBasicBlock> trueTargets = this.trueJumpTargets.pop();
		if (!trueTargets.empty()) {
			IRBasicBlock continueBlock = temps.newBlock("cond_done");
			currentMethod.addBlock(continueBlock);
			emit(new Jump(continueBlock));
			for (IRBasicBlock tb : trueTargets) {
				tb.add(new Jump(continueBlock));
			}
			switchToBlock(continueBlock);
		}
	}

	@Override
	public void visit(Or or) {
		Stack<IRBasicBlock> falseTargets = this.falseJumpTargets.pop();
		IRBasicBlock nextOrBlock = temps.newBlock("or_next");
		currentMethod.addBlock(nextOrBlock);
		for (IRBasicBlock fb : falseTargets) {
			fb.add(new Jump(nextOrBlock));
		}
		switchToBlock(nextOrBlock);
		this.falseJumpTargets.push(new Stack<>());
	}

	@Override
	public void visit(StatementIf statementIf) {
		IRBasicBlock afterIf = temps.newBlock("after_if");
		currentMethod.addBlock(afterIf);
		emit(new Jump(afterIf));
		Stack<IRBasicBlock> falseTargets = this.falseJumpTargets.pop();
		for (IRBasicBlock fb : falseTargets) {
			fb.add(new Jump(afterIf));
		}
		switchToBlock(afterIf);
	}

	@Override
	public void visit(ElseHeader elseHeader) {
		IRBasicBlock afterElse = temps.newBlock("after_else");
		currentMethod.addBlock(afterElse);
		this.skipElseBlocks.push(afterElse);
		emit(new Jump(afterElse));

		IRBasicBlock elseBlock = temps.newBlock("else");
		currentMethod.addBlock(elseBlock);
		Stack<IRBasicBlock> falseTargets = this.falseJumpTargets.pop();
		for (IRBasicBlock fb : falseTargets) {
			fb.add(new Jump(elseBlock));
		}
		switchToBlock(elseBlock);
	}

	@Override
	public void visit(StatementIfElse statementIfElse) {
		IRBasicBlock afterElse = this.skipElseBlocks.pop();
		emit(new Jump(afterElse));
		switchToBlock(afterElse);
	}

	@Override
	public void visit(WhileHeader whileHeader) {
		IRBasicBlock loopHeader = temps.newBlock("while_header");
		currentMethod.addBlock(loopHeader);
		emit(new Jump(loopHeader));
		switchToBlock(loopHeader);
		this.loopHeaders.push(loopHeader);
		this.breakTargets.push(new Stack<>());
	}

	@Override
	public void visit(StatementWhile statementWhile) {
		emit(new Jump(this.loopHeaders.pop()));
		IRBasicBlock afterWhile = temps.newBlock("after_while");
		currentMethod.addBlock(afterWhile);
		Stack<IRBasicBlock> falseTargets = this.falseJumpTargets.pop();
		for (IRBasicBlock fb : falseTargets) fb.add(new Jump(afterWhile));
		Stack<IRBasicBlock> breaks = this.breakTargets.pop();
		for (IRBasicBlock bb : breaks) bb.add(new Jump(afterWhile));
		switchToBlock(afterWhile);
	}

	@Override
	public void visit(ForeachHeader foreachHeader) {
		Obj arrayObj = foreachHeader.getDesignator().obj;
		int elemLoadOp = (arrayObj.getType().getElemType() == SymbolTable.charType)
				? Code.baload : Code.aload;

		this.breakTargets.push(new Stack<>());

		int arrayTemp = newTemp();
		if (arrayObj.getKind() == Obj.Fld) {
			int objTemp = valueStack.pop();
			emit(new GetField(arrayTemp, objTemp, arrayObj.getAdr()));
		} else if (arrayObj.getKind() == Obj.Elem) {
			int idx = valueStack.pop();
			int arr = valueStack.pop();
			IRInstruction.Op loadOp = (arrayObj.getType().getKind() == Struct.Char)
					? IRInstruction.Op.BALOAD : IRInstruction.Op.ALOAD;
			emit(new ALoad(loadOp, arrayTemp, arr, idx));
		} else {
			emit(new Load(arrayTemp, arrayObj));
		}
		int indexTemp = newTemp();
		emit(new Const(indexTemp, -1));

		IRBasicBlock loopHeader = temps.newBlock("foreach_header");
		currentMethod.addBlock(loopHeader);
		emit(new Jump(loopHeader));
		switchToBlock(loopHeader);
		this.loopHeaders.push(loopHeader);

		
		int one = newTemp();
		emit(new Const(one, 1));
		emit(new BinOp(IRInstruction.Op.ADD, indexTemp, indexTemp, one));

		// Check bounds
		int len = newTemp();
		emit(new ArrLen(len, arrayTemp));

		IRBasicBlock bodyBlock = temps.newBlock("foreach_body");
		IRBasicBlock endBlock = temps.newBlock("foreach_end");
		currentMethod.addBlock(bodyBlock);
		currentMethod.addBlock(endBlock);
		emit(new CJump(Code.ne, indexTemp, len, bodyBlock, endBlock));
		this.foreachEndBlocks.push(endBlock);

		switchToBlock(bodyBlock);

		int elemTemp = newTemp();
		IRInstruction.Op loadOp = (elemLoadOp == Code.baload)
				? IRInstruction.Op.BALOAD : IRInstruction.Op.ALOAD;
		emit(new ALoad(loadOp, elemTemp, arrayTemp, indexTemp));
		emit(new Store(foreachHeader.obj, elemTemp));

		this.foreachState.push(new int[]{arrayTemp, indexTemp});
	}

	@Override
	public void visit(StatementForeach statementForeach) {
		this.foreachState.pop();
		emit(new Jump(this.loopHeaders.pop()));
		IRBasicBlock endBlock = this.foreachEndBlocks.pop();
		Stack<IRBasicBlock> breaks = this.breakTargets.pop();
		for (IRBasicBlock bb : breaks) bb.add(new Jump(endBlock));
		switchToBlock(endBlock);
	}

	@Override
	public void visit(StatementContinue statementContinue) {
		emit(new Jump(this.loopHeaders.peek()));
		IRBasicBlock afterContinue = temps.newBlock("after_continue");
		currentMethod.addBlock(afterContinue);
		switchToBlock(afterContinue);
	}

	@Override
	public void visit(StatementBreak statementBreak) {
		IRBasicBlock breakBlock = temps.newBlock("break");
		currentMethod.addBlock(breakBlock);
		emit(new Jump(breakBlock));
		this.breakTargets.peek().push(breakBlock);
		IRBasicBlock afterBreak = temps.newBlock("after_break");
		currentMethod.addBlock(afterBreak);
		switchToBlock(afterBreak);
	}

	@Override
	public void visit(DesignatorAssignOp designatorAssignOp) {
		int val = valueStack.pop();
		Obj designatorObj = designatorAssignOp.getDesignator().obj;

		if (designatorObj.getKind() == Obj.Elem) {
			int idx = valueStack.pop();
			int arr = valueStack.pop();
			IRInstruction.Op storeOp = (designatorObj.getType().getKind() == Struct.Char)
					? IRInstruction.Op.BASTORE : IRInstruction.Op.ASTORE;
			emit(new AStore(storeOp, arr, idx, val));
		} else if (designatorObj.getKind() == Obj.Fld) {
			int objTemp = valueStack.pop();
			emit(new PutField(objTemp, designatorObj.getAdr(), val));
		} else {
			emit(new Store(designatorObj, val));
		}
	}

	@Override
	public void visit(DesignatorMethod designatorMethod) {
		Obj methodObj = designatorMethod.getMethodCall().getMethodDesignator().obj;
		if (methodObj.getType() != SymbolTable.noType)
			valueStack.pop();
	}

	@Override
	public void visit(DesignatorPostfixOp designatorPostfixOp) {
		Obj designatorObj = designatorPostfixOp.getDesignator().obj;

		int one = newTemp();
		emit(new Const(one, 1));

		if (designatorObj.getKind() == Obj.Elem) {
			int idx = valueStack.pop();
			int arr = valueStack.pop();
			IRInstruction.Op loadOp = (designatorObj.getType().getKind() == Struct.Char)
					? IRInstruction.Op.BALOAD : IRInstruction.Op.ALOAD;
			IRInstruction.Op storeOp = (designatorObj.getType().getKind() == Struct.Char)
					? IRInstruction.Op.BASTORE : IRInstruction.Op.ASTORE;
			int current = newTemp();
			emit(new ALoad(loadOp, current, arr, idx));
			int result = newTemp();
			if (designatorPostfixOp.getPostfixOp() instanceof PostfixInc)
				emit(new BinOp(IRInstruction.Op.ADD, result, current, one));
			else
				emit(new BinOp(IRInstruction.Op.SUB, result, current, one));
			emit(new AStore(storeOp, arr, idx, result));
		} else if (designatorObj.getKind() == Obj.Fld) {
			int objTemp = valueStack.pop();
			int current = newTemp();
			emit(new GetField(current, objTemp, designatorObj.getAdr()));
			int result = newTemp();
			if (designatorPostfixOp.getPostfixOp() instanceof PostfixInc)
				emit(new BinOp(IRInstruction.Op.ADD, result, current, one));
			else
				emit(new BinOp(IRInstruction.Op.SUB, result, current, one));
			emit(new PutField(objTemp, designatorObj.getAdr(), result));
		} else {
			int current = newTemp();
			emit(new Load(current, designatorObj));
			int result = newTemp();
			if (designatorPostfixOp.getPostfixOp() instanceof PostfixInc)
				emit(new BinOp(IRInstruction.Op.ADD, result, current, one));
			else
				emit(new BinOp(IRInstruction.Op.SUB, result, current, one));
			emit(new Store(designatorObj, result));
		}
	}

	@Override
	public void visit(DesignatorMultiple designatorMultiple) {
		Obj arrayObj = designatorMultiple.getDesignator().obj;
		List<Obj> designatorObjList = designatorMultiple.getDesignatorList().objlist.getList();
		IRInstruction.Op loadOp = (arrayObj.getType().getKind() == Struct.Char)
				? IRInstruction.Op.BALOAD : IRInstruction.Op.ALOAD;

		// Collect valueStack data for Elem/Fld targets (pushed by child designator visits).
		// Non-skipped Elem targets have [arrRef, idx] on stack; Fld targets have [objRef].
		// Collect in forward order, then iterate in reverse for the stores.
		int[][] targetData = new int[designatorObjList.size()][];
		for (int i = designatorObjList.size() - 1; i >= 0; i--) {
			Obj target = designatorObjList.get(i);
			if (target != SymbolTable.noObj && target.getKind() == Obj.Elem) {
				int idx = valueStack.pop();
				int arr = valueStack.pop();
				targetData[i] = new int[]{arr, idx};
			} else if (target != SymbolTable.noObj && target.getKind() == Obj.Fld) {
				int objTemp = valueStack.pop();
				targetData[i] = new int[]{objTemp};
			}
		}

		for (int i = designatorObjList.size() - 1; i >= 0; i--) {
			Obj target = designatorObjList.get(i);
			if (target != SymbolTable.noObj) {
				int arrTemp = newTemp();
				emit(new Load(arrTemp, arrayObj));
				int idxTemp = newTemp();
				emit(new Const(idxTemp, i));
				int elemTemp = newTemp();
				emit(new ALoad(loadOp, elemTemp, arrTemp, idxTemp));

				if (target.getKind() == Obj.Elem) {
					IRInstruction.Op storeOp = (target.getType().getKind() == Struct.Char)
							? IRInstruction.Op.BASTORE : IRInstruction.Op.ASTORE;
					emit(new AStore(storeOp, targetData[i][0], targetData[i][1], elemTemp));
				} else if (target.getKind() == Obj.Fld) {
					emit(new PutField(targetData[i][0], target.getAdr(), elemTemp));
				} else {
					emit(new Store(target, elemTemp));
				}
			}
		}
	}

	@Override
	public void visit(DesignatorIdent designatorIdent) {
		Obj obj = designatorIdent.obj;
		if (obj.getKind() == Obj.Fld ||
				(obj.getKind() == Obj.Meth && obj.getFpPos() == -1)) {
			
			int thisTemp = newTemp();
			emit(new Load(thisTemp, new Obj(Obj.Var, "this", obj.getType(), 0, 1)));
			valueStack.push(thisTemp);
		}
	}

	@Override
	public void visit(DesignatorClass designatorClass) {
		Obj parentObj = designatorClass.getDesignator().obj;
		int objTemp = newTemp();

		if (parentObj.getKind() == Obj.Fld) {
			
			int thisTemp = valueStack.pop();
			emit(new GetField(objTemp, thisTemp, parentObj.getAdr()));
		} else if (parentObj.getKind() == Obj.Elem) {
			
			int idx = valueStack.pop();
			int arr = valueStack.pop();
			IRInstruction.Op loadOp = (parentObj.getType().getKind() == Struct.Char)
					? IRInstruction.Op.BALOAD : IRInstruction.Op.ALOAD;
			emit(new ALoad(loadOp, objTemp, arr, idx));
		} else {
			emit(new Load(objTemp, parentObj));
		}

		valueStack.push(objTemp);
	}

	@Override
	public void visit(LoadDesignatorArray loadDesignatorArray) {
		Obj obj = loadDesignatorArray.obj;
		int arrTemp = newTemp();

		if (obj.getKind() == Obj.Fld) {
			
			int objTemp = valueStack.pop();
			emit(new GetField(arrTemp, objTemp, obj.getAdr()));
		} else if (obj.getKind() == Obj.Elem) {
			
			int idx = valueStack.pop();
			int arr = valueStack.pop();
			IRInstruction.Op loadOp = (obj.getType().getKind() == Struct.Char)
					? IRInstruction.Op.BALOAD : IRInstruction.Op.ALOAD;
			emit(new ALoad(loadOp, arrTemp, arr, idx));
		} else {
			emit(new Load(arrTemp, obj));
		}

		valueStack.push(arrTemp);
	}

	@Override
	public void visit(FactorDesignator factorDesignator) {
		Obj obj = factorDesignator.obj;
		int dst = newTemp();

		if (obj.getKind() == Obj.Elem) {
			int idx = valueStack.pop();
			int arr = valueStack.pop();
			IRInstruction.Op loadOp = (obj.getType().getKind() == Struct.Char)
					? IRInstruction.Op.BALOAD : IRInstruction.Op.ALOAD;
			emit(new ALoad(loadOp, dst, arr, idx));
		} else if (obj.getKind() == Obj.Fld) {
			int objTemp = valueStack.pop();
			emit(new GetField(dst, objTemp, obj.getAdr()));
		} else {
			emit(new Load(dst, obj));
		}

		valueStack.push(dst);
	}

	@Override
	public void visit(FactorNum factorNum) {
		int dst = newTemp();
		emit(new Const(dst, factorNum.obj.getAdr()));
		valueStack.push(dst);
	}

	@Override
	public void visit(FactorChar factorChar) {
		int dst = newTemp();
		emit(new Const(dst, factorChar.obj.getAdr()));
		valueStack.push(dst);
	}

	@Override
	public void visit(FactorBool factorBool) {
		int dst = newTemp();
		emit(new Const(dst, factorBool.obj.getAdr()));
		valueStack.push(dst);
	}

	@Override
	public void visit(FactorNewArray factorNewArray) {
		int size = valueStack.pop();
		int dst = newTemp();
		int elemType = (factorNewArray.getType().struct.getKind() == Struct.Char) ? 0 : 1;
		emit(new NewArray(dst, elemType, size));
		valueStack.push(dst);
	}

	@Override
	public void visit(NewClass newClass) {
		int dst = newTemp();
		int fieldCount = newClass.getType().struct.getNumberOfFields();
		emit(new NewObj(dst, fieldCount * 4));
		int tvfAddr = newTemp();
		emit(new Const(tvfAddr, this.tvfAdr.get(newClass.getType().getTypeName())));
		emit(new PutField(dst, 0, tvfAddr));
		valueStack.push(dst);
	}

	@Override
	public void visit(FactorNewClass factorNewClass) {
		Obj ctorObj = factorNewClass.getNewClass().obj;
		
		int explicitArgs = ctorObj.getLevel() - 1;
		List<Integer> argTemps = new ArrayList<>();
		for (int i = 0; i < explicitArgs; i++) {
			argTemps.add(0, valueStack.pop()); // insert at front to preserve order
		}
		int objTemp = valueStack.peek(); // object stays on stack
		List<Integer> ctorArgs = new ArrayList<>();
		ctorArgs.add(objTemp); // 'this' is first arg
		ctorArgs.addAll(argTemps);
		emit(new Call(-1, ctorObj, ctorArgs));
	}

	@Override
	public void visit(FactorMultiple factorMultiple) {
		int right = valueStack.pop();
		int left = valueStack.pop();
		int dst = newTemp();

		int opCode = factorMultiple.getMulOp().opcode.getOpCode();
		IRInstruction.Op irOp;
		if (opCode == Code.mul) irOp = IRInstruction.Op.MUL;
		else if (opCode == Code.div) irOp = IRInstruction.Op.DIV;
		else irOp = IRInstruction.Op.REM;

		emit(new BinOp(irOp, dst, left, right));
		valueStack.push(dst);
	}

	@Override
	public void visit(TermNegative termNegative) {
		int src = valueStack.pop();
		int dst = newTemp();
		emit(new Neg(dst, src));
		valueStack.push(dst);
	}

	@Override
	public void visit(TermMultiple termMultiple) {
		int right = valueStack.pop();
		int left = valueStack.pop();
		int dst = newTemp();

		int opCode = termMultiple.getAddOp().opcode.getOpCode();
		IRInstruction.Op irOp = (opCode == Code.add)
				? IRInstruction.Op.ADD : IRInstruction.Op.SUB;

		emit(new BinOp(irOp, dst, left, right));
		valueStack.push(dst);
	}

	private void generateBuiltins() {
		IRMethod defaultCtor = new IRMethod("-default-ctor", null);
		TempGenerator ctorTemps = new TempGenerator("default_ctor");
		IRBasicBlock ctorBlock = ctorTemps.newBlock("entry");
		defaultCtor.addBlock(ctorBlock);
		ctorBlock.add(new Enter(1, 1));
		ctorBlock.add(new Exit());
		ctorBlock.add(new Return());
		defaultCtor.setParamCount(1);
		defaultCtor.setLocalCount(1);
		program.addMethod(defaultCtor);

		Obj ordObj = SymbolTable.find("ord");
		IRMethod ordMethod = new IRMethod("ord", ordObj);
		IRBasicBlock ordBlock = new TempGenerator("ord").newBlock("entry");
		ordMethod.addBlock(ordBlock);
		ordBlock.add(new Return());
		program.addMethod(ordMethod);

		Obj chrObj = SymbolTable.find("chr");
		IRMethod chrMethod = new IRMethod("chr", chrObj);
		IRBasicBlock chrBlock = new TempGenerator("chr").newBlock("entry");
		chrMethod.addBlock(chrBlock);
		chrBlock.add(new Return());
		program.addMethod(chrMethod);

		Obj lenObj = SymbolTable.find("len");
		IRMethod lenMethod = new IRMethod("len", lenObj);
		IRBasicBlock lenBlock = new TempGenerator("len").newBlock("entry");
		lenMethod.addBlock(lenBlock);
		lenMethod.setParamCount(1);
		lenMethod.setLocalCount(1);
		program.addMethod(lenMethod);
	}

	private void generateTvf() {
		for (String className : this.classes.keySet()) {
			List<String> nameList = new ArrayList<>();
			List<Obj> methodList = new ArrayList<>();
			for (Obj method : this.classes.get(className).getMembers()) {
				if (method.getKind() != Obj.Meth || method.getName().charAt(0) == '-')
					continue;
				nameList.add(method.getName());
				methodList.add(method);
			}
			TvfInit tvfInit = new TvfInit(className,
					this.tvfAdr.get(className),
					nameList.toArray(new String[0]),
					methodList.toArray(new Obj[0]),
					classVirtualMethods.get(className));
			program.addTvfEntry(tvfInit);
			emit(tvfInit);
		}
	}
}
