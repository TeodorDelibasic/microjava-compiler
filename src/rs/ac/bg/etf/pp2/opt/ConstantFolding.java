package rs.ac.bg.etf.pp2.opt;

import java.util.HashMap;
import java.util.Map;

import rs.ac.bg.etf.pp2.ir.*;
import rs.ac.bg.etf.pp2.ir.IRInstructions.*;

public class ConstantFolding {

	public void optimize(IRProgram program) {
		for (IRMethod method : program.getMethods()) {
			for (IRBasicBlock block : method.getBlocks()) {
				optimizeBlock(block);
			}
		}
	}

	private void optimizeBlock(IRBasicBlock block) {
		// Track known constant values: temp -> value
		Map<Integer, Integer> constants = new HashMap<>();

		for (int i = 0; i < block.getInstructions().size(); i++) {
			IRInstruction instr = block.getInstructions().get(i);

			if (instr instanceof Const) {
				Const c = (Const) instr;
				constants.put(c.dst, c.value);
			}
			else if (instr instanceof Load) {
				Load l = (Load) instr;
				// Named constants (const int X = 5) are Obj.Con with value in adr
				if (l.symbol.getKind() == rs.etf.pp1.symboltable.concepts.Obj.Con) {
					int value = l.symbol.getAdr();
					constants.put(l.dst, value);
					// Replace LOAD with CONST for cleaner IR
					block.getInstructions().set(i, new Const(l.dst, value));
				}
			}
			else if (instr instanceof BinOp) {
				BinOp b = (BinOp) instr;
				Integer left = constants.get(b.src1);
				Integer right = constants.get(b.src2);

				if (left != null && right != null) {
					Integer result = evalBinOp(b.getOp(), left, right);
					if (result != null) {
						Const folded = new Const(b.dst, result);
						block.getInstructions().set(i, folded);
						constants.put(b.dst, result);
						continue;
					}
				}
				// Result is not a constant
				constants.remove(b.dst);
			}
			else if (instr instanceof Neg) {
				Neg n = (Neg) instr;
				Integer src = constants.get(n.src);
				if (src != null) {
					Const folded = new Const(n.dst, -src);
					block.getInstructions().set(i, folded);
					constants.put(n.dst, -src);
					continue;
				}
				constants.remove(n.dst);
			}
			else {
				// Any other instruction that defines a temp: remove from constants
				for (int t : getDefs(instr)) {
					constants.remove(t);
				}
			}
		}
	}

	private Integer evalBinOp(IRInstruction.Op op, int left, int right) {
		switch (op) {
			case ADD: return left + right;
			case SUB: return left - right;
			case MUL: return left * right;
			case DIV: return right != 0 ? left / right : null;
			case REM: return right != 0 ? left % right : null;
			default: return null;
		}
	}

	private int[] getDefs(IRInstruction instr) {
		if (instr instanceof Load)       return new int[]{((Load)instr).dst};
		if (instr instanceof GetField)   return new int[]{((GetField)instr).dst};
		if (instr instanceof ALoad)      return new int[]{((ALoad)instr).dst};
		if (instr instanceof ArrLen)     return new int[]{((ArrLen)instr).dst};
		if (instr instanceof NewArray)   return new int[]{((NewArray)instr).dst};
		if (instr instanceof NewObj)     return new int[]{((NewObj)instr).dst};
		if (instr instanceof Call)       { int d = ((Call)instr).dst; return d >= 0 ? new int[]{d} : new int[]{}; }
		if (instr instanceof InvokeVirtual) { int d = ((InvokeVirtual)instr).dst; return d >= 0 ? new int[]{d} : new int[]{}; }
		if (instr instanceof Read)       return new int[]{((Read)instr).dst};
		return new int[]{};
	}
}
