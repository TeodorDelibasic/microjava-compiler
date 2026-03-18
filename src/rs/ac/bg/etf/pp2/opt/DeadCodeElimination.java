package rs.ac.bg.etf.pp2.opt;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import rs.ac.bg.etf.pp2.ir.*;
import rs.ac.bg.etf.pp2.ir.IRInstructions.*;

public class DeadCodeElimination {

	public void optimize(IRProgram program) {
		for (IRMethod method : program.getMethods()) {
			boolean changed = true;
			while (changed) {
				changed = eliminateDeadCode(method);
			}
		}
	}

	private boolean eliminateDeadCode(IRMethod method) {
		// Collect all temps that are used as source operands
		Set<Integer> usedTemps = new HashSet<>();
		for (IRBasicBlock block : method.getBlocks()) {
			for (IRInstruction instr : block.getInstructions()) {
				for (int t : getUses(instr)) {
					usedTemps.add(t);
				}
			}
		}

		// Remove instructions that define a temp not in usedTemps
		boolean changed = false;
		for (IRBasicBlock block : method.getBlocks()) {
			Iterator<IRInstruction> it = block.getInstructions().iterator();
			while (it.hasNext()) {
				IRInstruction instr = it.next();
				int def = getSingleDef(instr);
				if (def >= 0 && !usedTemps.contains(def) && isSideEffectFree(instr)) {
					it.remove();
					changed = true;
				}
			}
		}
		return changed;
	}

	/** Returns the single temp defined by this instruction, or -1 */
	private int getSingleDef(IRInstruction instr) {
		if (instr instanceof Const)      return ((Const) instr).dst;
		if (instr instanceof Load)       return ((Load) instr).dst;
		if (instr instanceof BinOp)      return ((BinOp) instr).dst;
		if (instr instanceof Neg)        return ((Neg) instr).dst;
		if (instr instanceof GetField)   return ((GetField) instr).dst;
		if (instr instanceof ALoad)      return ((ALoad) instr).dst;
		if (instr instanceof ArrLen)     return ((ArrLen) instr).dst;
		if (instr instanceof NewArray)   return ((NewArray) instr).dst;
		if (instr instanceof NewObj)     return ((NewObj) instr).dst;
		return -1;
	}

	/** Returns true if the instruction has no side effects beyond defining its dest */
	private boolean isSideEffectFree(IRInstruction instr) {
		return (instr instanceof Const)
			|| (instr instanceof Load)
			|| (instr instanceof BinOp)
			|| (instr instanceof Neg)
			|| (instr instanceof GetField)
			|| (instr instanceof ALoad)
			|| (instr instanceof ArrLen);
		// NOT: NewArray, NewObj (allocation), Call, InvokeVirtual, Store, Print, Read, etc.
	}

	private int[] getUses(IRInstruction instr) {
		if (instr instanceof Store)      return new int[]{((Store) instr).src};
		if (instr instanceof BinOp)      { BinOp b = (BinOp) instr; return new int[]{b.src1, b.src2}; }
		if (instr instanceof Neg)        return new int[]{((Neg) instr).src};
		if (instr instanceof GetField)   return new int[]{((GetField) instr).objTemp};
		if (instr instanceof PutField)   { PutField p = (PutField) instr; return new int[]{p.objTemp, p.src}; }
		if (instr instanceof ALoad)      { ALoad a = (ALoad) instr; return new int[]{a.arrTemp, a.idxTemp}; }
		if (instr instanceof AStore)     { AStore a = (AStore) instr; return new int[]{a.arrTemp, a.idxTemp, a.src}; }
		if (instr instanceof ArrLen)     return new int[]{((ArrLen) instr).arrTemp};
		if (instr instanceof NewArray)   return new int[]{((NewArray) instr).sizeTemp};
		if (instr instanceof CJump)      { CJump c = (CJump) instr; return new int[]{c.src1, c.src2}; }
		if (instr instanceof ReturnVal)  return new int[]{((ReturnVal) instr).src};
		if (instr instanceof Call) {
			Call c = (Call) instr;
			int[] r = new int[c.argTemps.size()];
			for (int i = 0; i < r.length; i++) r[i] = c.argTemps.get(i);
			return r;
		}
		if (instr instanceof InvokeVirtual) {
			InvokeVirtual iv = (InvokeVirtual) instr;
			int[] r = new int[iv.argTemps.size() + 1];
			r[0] = iv.objTemp;
			for (int i = 0; i < iv.argTemps.size(); i++) r[i + 1] = iv.argTemps.get(i);
			return r;
		}
		if (instr instanceof Print)      return new int[]{((Print) instr).src};
		return new int[]{};
	}
}
