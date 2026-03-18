package rs.ac.bg.etf.pp2.opt;

import java.util.HashMap;
import java.util.Map;

import rs.ac.bg.etf.pp2.ir.*;
import rs.ac.bg.etf.pp2.ir.IRInstructions.*;

public class StrengthReduction {

	public void optimize(IRProgram program) {
		for (IRMethod method : program.getMethods()) {
			for (IRBasicBlock block : method.getBlocks()) {
				reduceBlock(block);
			}
		}
	}

	private void reduceBlock(IRBasicBlock block) {
		Map<Integer, Integer> constants = new HashMap<>();

		for (int i = 0; i < block.getInstructions().size(); i++) {
			IRInstruction instr = block.getInstructions().get(i);

			if (instr instanceof Const) {
				constants.put(((Const) instr).dst, ((Const) instr).value);
				continue;
			}

			if (!(instr instanceof BinOp)) continue;

			BinOp b = (BinOp) instr;
			Integer leftVal = constants.get(b.src1);
			Integer rightVal = constants.get(b.src2);

			IRInstruction replacement = tryReduce(b, leftVal, rightVal);
			if (replacement != null) {
				block.getInstructions().set(i, replacement);
				if (replacement instanceof Const) {
					constants.put(((Const) replacement).dst, ((Const) replacement).value);
				}
			}
		}
	}

	private IRInstruction tryReduce(BinOp b, Integer leftVal, Integer rightVal) {
		switch (b.getOp()) {
			case MUL: return reduceMul(b, leftVal, rightVal);
			case DIV: return reduceDiv(b, leftVal, rightVal);
			case REM: return reduceRem(b, leftVal, rightVal);
			case ADD: return reduceAdd(b, leftVal, rightVal);
			case SUB: return reduceSub(b, leftVal, rightVal);
			default:  return null;
		}
	}

	private IRInstruction reduceMul(BinOp b, Integer leftVal, Integer rightVal) {
		// MUL x, a, 0  or  MUL x, 0, a  →  CONST x, 0
		if ((rightVal != null && rightVal == 0) || (leftVal != null && leftVal == 0))
			return new Const(b.dst, 0);

		// MUL x, a, 1  →  ADD x, a, zero (identity)
		// MUL x, 1, a  →  ADD x, a, zero (identity)
		// These become no-ops after DCE if the zero const is dead

		// MUL x, a, 2  →  ADD x, a, a
		if (rightVal != null && rightVal == 2)
			return new BinOp(IRInstruction.Op.ADD, b.dst, b.src1, b.src1);
		if (leftVal != null && leftVal == 2)
			return new BinOp(IRInstruction.Op.ADD, b.dst, b.src2, b.src2);

		return null;
	}

	private IRInstruction reduceDiv(BinOp b, Integer leftVal, Integer rightVal) {
		// DIV x, 0, a  →  CONST x, 0
		if (leftVal != null && leftVal == 0)
			return new Const(b.dst, 0);

		// DIV x, a, 1  →  identity (keep a, DCE will clean up)
		// Not easily expressible without a copy instruction, skip

		return null;
	}

	private IRInstruction reduceRem(BinOp b, Integer leftVal, Integer rightVal) {
		// REM x, a, 1  →  CONST x, 0
		if (rightVal != null && rightVal == 1)
			return new Const(b.dst, 0);

		// REM x, 0, a  →  CONST x, 0
		if (leftVal != null && leftVal == 0)
			return new Const(b.dst, 0);

		return null;
	}

	private IRInstruction reduceAdd(BinOp b, Integer leftVal, Integer rightVal) {
		// ADD x, a, 0  or  ADD x, 0, a  →  no-op (identity)
		// Not easily expressible as a single instruction without copy, skip
		return null;
	}

	private IRInstruction reduceSub(BinOp b, Integer leftVal, Integer rightVal) {
		// SUB x, a, 0  →  identity, skip
		// SUB x, a, a  →  CONST x, 0
		if (b.src1 == b.src2)
			return new Const(b.dst, 0);
		return null;
	}
}
