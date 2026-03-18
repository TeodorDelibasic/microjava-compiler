package rs.ac.bg.etf.pp2.opt;

import java.util.HashMap;
import java.util.Map;

import rs.ac.bg.etf.pp2.ir.*;
import rs.ac.bg.etf.pp2.ir.IRInstructions.*;
import rs.etf.pp1.symboltable.concepts.Obj;

public class ConstantPropagation {

	public void optimize(IRProgram program) {
		for (IRMethod method : program.getMethods()) {
			for (IRBasicBlock block : method.getBlocks()) {
				propagateInBlock(block);
			}
		}
	}

	private void propagateInBlock(IRBasicBlock block) {
		// Maps variable identity (Obj name + kind + adr + level) -> known constant value
		Map<String, Integer> varConstants = new HashMap<>();
		// Maps temp -> known constant value
		Map<Integer, Integer> tempConstants = new HashMap<>();

		for (int i = 0; i < block.getInstructions().size(); i++) {
			IRInstruction instr = block.getInstructions().get(i);

			if (instr instanceof Const) {
				tempConstants.put(((Const) instr).dst, ((Const) instr).value);
			}
			else if (instr instanceof Store) {
				Store s = (Store) instr;
				// Track: if we're storing a known constant to a variable
				Integer val = tempConstants.get(s.src);
				String key = varKey(s.symbol);
				if (key != null && val != null) {
					varConstants.put(key, val);
				} else if (key != null) {
					varConstants.remove(key);
				}
			}
			else if (instr instanceof Load) {
				Load l = (Load) instr;
				// If loading a variable with a known constant value, replace with CONST
				String key = varKey(l.symbol);
				if (key != null && varConstants.containsKey(key)) {
					int val = varConstants.get(key);
					block.getInstructions().set(i, new Const(l.dst, val));
					tempConstants.put(l.dst, val);
					continue;
				}
				// Also track if loading a named constant (Obj.Con)
				if (l.symbol.getKind() == Obj.Con) {
					tempConstants.put(l.dst, l.symbol.getAdr());
				} else {
					tempConstants.remove(l.dst);
				}
			}
			else if (instr instanceof Read) {
				// Read invalidates the target variable
				Read r = (Read) instr;
				String key = varKey(r.symbol);
				if (key != null) varConstants.remove(key);
				tempConstants.remove(r.dst);
			}
			else if (instr instanceof AStore || instr instanceof PutField) {
				// Array/field stores could alias — conservatively invalidate all field/elem knowledge
				// Keep simple variable constants since they can't alias
			}
			else if (instr instanceof Call || instr instanceof InvokeVirtual) {
				// Method calls can modify any global variable or field
				// Invalidate all variable constants (conservative)
				varConstants.clear();
				// Track result temp
				int dst = instr instanceof Call ? ((Call) instr).dst : ((InvokeVirtual) instr).dst;
				if (dst >= 0) tempConstants.remove(dst);
			}
			else {
				// For any other instruction that defines a temp, update temp tracking
				for (int t : getDefs(instr)) {
					tempConstants.remove(t);
				}
			}
		}
	}

	/** Create a unique key for a variable symbol, or null if not a simple var/global */
	private String varKey(Obj symbol) {
		if (symbol.getKind() == Obj.Var) {
			return symbol.getName() + "@" + symbol.getLevel() + ":" + symbol.getAdr();
		}
		return null;
	}

	private int[] getDefs(IRInstruction instr) {
		if (instr instanceof BinOp)      return new int[]{((BinOp) instr).dst};
		if (instr instanceof Neg)        return new int[]{((Neg) instr).dst};
		if (instr instanceof GetField)   return new int[]{((GetField) instr).dst};
		if (instr instanceof ALoad)      return new int[]{((ALoad) instr).dst};
		if (instr instanceof ArrLen)     return new int[]{((ArrLen) instr).dst};
		if (instr instanceof NewArray)   return new int[]{((NewArray) instr).dst};
		if (instr instanceof NewObj)     return new int[]{((NewObj) instr).dst};
		return new int[]{};
	}
}
