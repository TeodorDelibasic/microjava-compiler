package rs.ac.bg.etf.pp2.opt;

import java.util.HashMap;
import java.util.Map;

import rs.ac.bg.etf.pp2.ir.*;
import rs.ac.bg.etf.pp2.ir.IRInstructions.*;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class RedundantLoadElimination {

	public void optimize(IRProgram program) {
		for (IRMethod method : program.getMethods()) {
			for (IRBasicBlock block : method.getBlocks()) {
				eliminateInBlock(block);
			}
		}
	}

	private void eliminateInBlock(IRBasicBlock block) {
		// Maps variable key -> temp that holds its current value
		Map<String, Integer> varToTemp = new HashMap<>();

		for (int i = 0; i < block.getInstructions().size(); i++) {
			IRInstruction instr = block.getInstructions().get(i);

			if (instr instanceof Load) {
				Load l = (Load) instr;
				String key = varKey(l.symbol);
				if (key != null && varToTemp.containsKey(key) && isSimpleType(l.symbol)) {
					// Redundant load of a simple variable — rewrite uses to the existing temp
					int existingTemp = varToTemp.get(key);
					rewriteUses(block, i + 1, l.dst, existingTemp);
					// Now l.dst is unused -> DCE will remove this LOAD
				} else if (key != null) {
					varToTemp.put(key, l.dst);
				}
			}
			else if (instr instanceof Store) {
				Store s = (Store) instr;
				String key = varKey(s.symbol);
				if (key != null) {
					// Store invalidates the cached load, but the stored temp becomes the new value
					varToTemp.put(key, s.src);
				}
			}
			else if (instr instanceof AStore || instr instanceof PutField) {
				// Could alias array/field loads — conservatively don't cache those
			}
			else if (instr instanceof Read) {
				Read r = (Read) instr;
				String key = varKey(r.symbol);
				if (key != null) varToTemp.remove(key);
			}
			else if (instr instanceof Call || instr instanceof InvokeVirtual) {
				// Method calls can modify globals — invalidate all
				varToTemp.clear();
			}
		}
	}

	/** Rewrite all uses of oldTemp to newTemp in instructions from startIdx onwards */
	private void rewriteUses(IRBasicBlock block, int startIdx, int oldTemp, int newTemp) {
		for (int i = startIdx; i < block.getInstructions().size(); i++) {
			IRInstruction instr = block.getInstructions().get(i);
			IRInstruction replaced = replaceTemp(instr, oldTemp, newTemp);
			if (replaced != null) {
				block.getInstructions().set(i, replaced);
			}
		}
	}

	/** Return a new instruction with oldTemp replaced by newTemp, or null if no change */
	private IRInstruction replaceTemp(IRInstruction instr, int old, int nw) {
		if (instr instanceof Store) {
			Store s = (Store) instr;
			if (s.src == old) return new Store(s.symbol, nw);
		}
		else if (instr instanceof BinOp) {
			BinOp b = (BinOp) instr;
			int s1 = b.src1 == old ? nw : b.src1;
			int s2 = b.src2 == old ? nw : b.src2;
			if (s1 != b.src1 || s2 != b.src2)
				return new BinOp(b.getOp(), b.dst, s1, s2);
		}
		else if (instr instanceof Neg) {
			Neg n = (Neg) instr;
			if (n.src == old) return new Neg(n.dst, nw);
		}
		else if (instr instanceof GetField) {
			GetField g = (GetField) instr;
			if (g.objTemp == old) return new GetField(g.dst, nw, g.offset);
		}
		else if (instr instanceof PutField) {
			PutField p = (PutField) instr;
			int obj = p.objTemp == old ? nw : p.objTemp;
			int src = p.src == old ? nw : p.src;
			if (obj != p.objTemp || src != p.src) return new PutField(obj, p.offset, src);
		}
		else if (instr instanceof ALoad) {
			ALoad a = (ALoad) instr;
			int arr = a.arrTemp == old ? nw : a.arrTemp;
			int idx = a.idxTemp == old ? nw : a.idxTemp;
			if (arr != a.arrTemp || idx != a.idxTemp)
				return new ALoad(a.getOp(), a.dst, arr, idx);
		}
		else if (instr instanceof AStore) {
			AStore a = (AStore) instr;
			int arr = a.arrTemp == old ? nw : a.arrTemp;
			int idx = a.idxTemp == old ? nw : a.idxTemp;
			int src = a.src == old ? nw : a.src;
			if (arr != a.arrTemp || idx != a.idxTemp || src != a.src)
				return new AStore(a.getOp(), arr, idx, src);
		}
		else if (instr instanceof ArrLen) {
			ArrLen a = (ArrLen) instr;
			if (a.arrTemp == old) return new ArrLen(a.dst, nw);
		}
		else if (instr instanceof NewArray) {
			NewArray n = (NewArray) instr;
			if (n.sizeTemp == old) return new NewArray(n.dst, n.elemType, nw);
		}
		else if (instr instanceof CJump) {
			CJump c = (CJump) instr;
			int s1 = c.src1 == old ? nw : c.src1;
			int s2 = c.src2 == old ? nw : c.src2;
			if (s1 != c.src1 || s2 != c.src2)
				return new CJump(c.relOp, s1, s2, c.trueTarget, c.falseTarget);
		}
		else if (instr instanceof ReturnVal) {
			ReturnVal r = (ReturnVal) instr;
			if (r.src == old) return new ReturnVal(nw);
		}
		else if (instr instanceof Print) {
			Print p = (Print) instr;
			if (p.src == old) return new Print(p.getOp(), nw, p.width);
		}
		return null;
	}

	private boolean isSimpleType(Obj symbol) {
		int kind = symbol.getType().getKind();
		return kind == rs.etf.pp1.symboltable.concepts.Struct.Int
			|| kind == rs.etf.pp1.symboltable.concepts.Struct.Char
			|| kind == Struct.Bool;
	}

	private String varKey(Obj symbol) {
		if (symbol.getKind() == Obj.Var) {
			return symbol.getName() + "@" + symbol.getLevel() + ":" + symbol.getAdr();
		}
		return null;
	}
}
