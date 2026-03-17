package rs.ac.bg.etf.pp2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.pp2.ir.*;
import rs.ac.bg.etf.pp2.ir.IRInstructions.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;

public class IRCodeEmitter {

	private Map<String, Integer> blockAddresses = new HashMap<>();
	private List<JumpFixup> pendingFixups = new ArrayList<>();
	private int tempBase; // first local slot for temps
	private int[] tempSlotMap; // maps IR temp number -> actual local slot offset

	private static class JumpFixup {
		int codeAddress;
		String targetLabel;
		JumpFixup(int addr, String label) {
			this.codeAddress = addr;
			this.targetLabel = label;
		}
	}

	public void emit(IRProgram program) {
		for (IRMethod method : program.getMethods()) {
			emitMethod(method);
		}
	}

	private void emitMethod(IRMethod method) {
		blockAddresses.clear();
		pendingFixups.clear();

		String name = method.getName();
		Obj sym = method.getSymbol();

		// Built-in methods get special bytecode
		if (name.equals("-default-ctor")) {
			Code.put(Code.enter); Code.put(1); Code.put(1);
			Code.put(Code.exit); Code.put(Code.return_);
			return;
		}
		if (name.equals("ord")) {
			if (sym != null) sym.setAdr(Code.pc);
			Code.put(Code.return_);
			return;
		}
		if (name.equals("chr")) {
			if (sym != null) sym.setAdr(Code.pc);
			Code.put(Code.return_);
			return;
		}
		if (name.equals("len")) {
			if (sym != null) sym.setAdr(Code.pc);
			Code.put(Code.arraylength);
			Code.put(Code.return_);
			return;
		}

		// Set method address in symbol table
		if (sym != null) sym.setAdr(Code.pc);
		if ("main".equals(name)) Code.mainPc = Code.pc;

		// Allocate temp slots — reuse slots to stay under 128 total locals
		int maxTemp = findMaxTemp(method);
		tempBase = method.getLocalCount();
		int numSlots = allocateTempSlots(method, maxTemp);
		int totalLocals = tempBase + numSlots;

		// Emit all basic blocks in order
		List<IRBasicBlock> blocks = method.getBlocks();
		for (int bi = 0; bi < blocks.size(); bi++) {
			IRBasicBlock block = blocks.get(bi);
			blockAddresses.put(block.getLabel(), Code.pc);
			resolveFixupsTo(block.getLabel());

			for (IRInstruction instr : block.getInstructions()) {
				emitInstruction(instr, totalLocals);
			}

			// After a CJump, the true target must be the next block.
			// If it's not, emit an explicit jump to the true target.
			IRInstruction last = block.getLastInstruction();
			if (last instanceof CJump) {
				CJump cj = (CJump) last;
				IRBasicBlock nextBlock = (bi + 1 < blocks.size()) ? blocks.get(bi + 1) : null;
				if (nextBlock == null || !nextBlock.getLabel().equals(cj.trueTarget.getLabel())) {
					String trueLabel = cj.trueTarget.getLabel();
					if (blockAddresses.containsKey(trueLabel)) {
						Code.putJump(blockAddresses.get(trueLabel));
					} else {
						Code.putJump(0);
						pendingFixups.add(new JumpFixup(Code.pc - 2, trueLabel));
					}
				}
			}
		}

		if (!pendingFixups.isEmpty()) {
			System.err.println("WARNING: " + pendingFixups.size()
					+ " unresolved fixups in method " + name);
		}
	}

	private void resolveFixupsTo(String label) {
		Iterator<JumpFixup> it = pendingFixups.iterator();
		while (it.hasNext()) {
			JumpFixup fixup = it.next();
			if (fixup.targetLabel.equals(label)) {
				Code.fixup(fixup.codeAddress);
				it.remove();
			}
		}
	}

	private void emitInstruction(IRInstruction instr, int totalLocals) {
		switch (instr.getOp()) {
			case CONST:       emitConst((Const) instr); break;
			case LOAD:        emitLoad((Load) instr); break;
			case STORE:       emitStore((Store) instr); break;
			case ADD: case SUB: case MUL: case DIV: case REM:
			                  emitBinOp((BinOp) instr); break;
			case NEG:         emitNeg((Neg) instr); break;
			case GETFIELD:    emitGetField((GetField) instr); break;
			case PUTFIELD:    emitPutField((PutField) instr); break;
			case ALOAD: case BALOAD:
			                  emitALoad((ALoad) instr); break;
			case ASTORE: case BASTORE:
			                  emitAStore((AStore) instr); break;
			case ARRLEN:      emitArrLen((ArrLen) instr); break;
			case NEWARRAY:    emitNewArray((NewArray) instr); break;
			case NEW_OBJ:     emitNewObj((NewObj) instr); break;
			case JUMP:        emitJump((Jump) instr); break;
			case CJUMP:       emitCJump((CJump) instr); break;
			case ENTER:       emitEnter((Enter) instr, totalLocals); break;
			case EXIT:        Code.put(Code.exit); break;
			case RETURN:      Code.put(Code.return_); break;
			case RETURN_VAL:  emitReturnVal((ReturnVal) instr); break;
			case CALL:        emitCall((Call) instr); break;
			case INVOKE_VIRTUAL: emitInvokeVirtual((InvokeVirtual) instr); break;
			case READ: case BREAD:
			                  emitRead((Read) instr); break;
			case PRINT: case BPRINT:
			                  emitPrint((Print) instr); break;
			case TRAP:        emitTrap((Trap) instr); break;
			case TVF_INIT:    emitTvfInit((TvfInit) instr); break;
			case LABEL:       break; // no-op
			case PARAM:       break; // should not appear (args embedded in Call/InvokeVirtual)
			default:
				System.err.println("Unknown IR instruction: " + instr.getOp());
		}
	}

	private void loadTemp(int temp) {
		int slot = tempBase + tempSlotMap[temp];
		if (slot <= 3)
			Code.put(Code.load_n + slot);
		else {
			Code.put(Code.load);
			Code.put(slot);
		}
	}

	private void storeTemp(int temp) {
		int slot = tempBase + tempSlotMap[temp];
		if (slot <= 3)
			Code.put(Code.store_n + slot);
		else {
			Code.put(Code.store);
			Code.put(slot);
		}
	}

	private void emitConst(Const c) {
		Code.loadConst(c.value);
		storeTemp(c.dst);
	}

	private void emitLoad(Load l) {
		Code.load(l.symbol);
		storeTemp(l.dst);
	}

	private void emitStore(Store s) {
		loadTemp(s.src);
		Code.store(s.symbol);
	}

	private void emitBinOp(BinOp b) {
		loadTemp(b.src1);
		loadTemp(b.src2);
		switch (b.getOp()) {
			case ADD: Code.put(Code.add); break;
			case SUB: Code.put(Code.sub); break;
			case MUL: Code.put(Code.mul); break;
			case DIV: Code.put(Code.div); break;
			case REM: Code.put(Code.rem); break;
			default: break;
		}
		storeTemp(b.dst);
	}

	private void emitNeg(Neg n) {
		loadTemp(n.src);
		Code.put(Code.neg);
		storeTemp(n.dst);
	}

	private void emitGetField(GetField g) {
		loadTemp(g.objTemp);
		Code.put(Code.getfield);
		Code.put2(g.offset);
		storeTemp(g.dst);
	}

	private void emitPutField(PutField p) {
		loadTemp(p.objTemp);
		loadTemp(p.src);
		Code.put(Code.putfield);
		Code.put2(p.offset);
	}

	private void emitALoad(ALoad a) {
		loadTemp(a.arrTemp);
		loadTemp(a.idxTemp);
		Code.put(a.getOp() == IRInstruction.Op.BALOAD ? Code.baload : Code.aload);
		storeTemp(a.dst);
	}

	private void emitAStore(AStore a) {
		loadTemp(a.arrTemp);
		loadTemp(a.idxTemp);
		loadTemp(a.src);
		Code.put(a.getOp() == IRInstruction.Op.BASTORE ? Code.bastore : Code.astore);
	}

	private void emitArrLen(ArrLen a) {
		loadTemp(a.arrTemp);
		Code.put(Code.arraylength);
		storeTemp(a.dst);
	}

	private void emitNewArray(NewArray n) {
		loadTemp(n.sizeTemp);
		Code.put(Code.newarray);
		Code.put(n.elemType);
		storeTemp(n.dst);
	}

	private void emitNewObj(NewObj n) {
		Code.put(Code.new_);
		Code.put2(n.size);
		storeTemp(n.dst);
	}

	private void emitJump(Jump j) {
		String targetLabel = j.target.getLabel();
		if (blockAddresses.containsKey(targetLabel)) {
			Code.putJump(blockAddresses.get(targetLabel));
		} else {
			Code.putJump(0);
			pendingFixups.add(new JumpFixup(Code.pc - 2, targetLabel));
		}
	}

	private void emitCJump(CJump c) {
		loadTemp(c.src1);
		loadTemp(c.src2);
		String falseLabel = c.falseTarget.getLabel();
		if (blockAddresses.containsKey(falseLabel)) {
			Code.putFalseJump(c.relOp, blockAddresses.get(falseLabel));
		} else {
			Code.putFalseJump(c.relOp, 0);
			pendingFixups.add(new JumpFixup(Code.pc - 2, falseLabel));
		}
		// True target should be the next block (fall through)
	}

	private void emitEnter(Enter e, int totalLocals) {
		Code.put(Code.enter);
		Code.put(e.paramCount);
		Code.put(totalLocals);
	}

	private void emitReturnVal(ReturnVal r) {
		loadTemp(r.src);
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	private void emitCall(Call c) {
		// Load args onto stack
		for (int argTemp : c.argTemps) {
			loadTemp(argTemp);
		}
		int dest = c.method.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(dest);
		if (c.dst >= 0) {
			storeTemp(c.dst);
		}
	}

	private void emitInvokeVirtual(InvokeVirtual iv) {
		// Load 'this' as first arg
		loadTemp(iv.objTemp);
		// Load regular args
		for (int argTemp : iv.argTemps) {
			loadTemp(argTemp);
		}
		// Load 'this' again for vtable lookup
		loadTemp(iv.objTemp);
		Code.put(Code.getfield);
		Code.put2(0);
		// Dispatch
		Code.put(Code.invokevirtual);
		for (int i = 0; i < iv.methodName.length(); i++) {
			Code.put4(iv.methodName.charAt(i));
		}
		// Sentinel: -1 as 4 bytes
		Code.put(0xFF);
		Code.put(0xFF);
		Code.put(0xFF);
		Code.put(0xFF);
		if (iv.dst >= 0) {
			storeTemp(iv.dst);
		}
	}

	private void emitRead(Read r) {
		Code.put(r.getOp() == IRInstruction.Op.BREAD ? Code.bread : Code.read);
		storeTemp(r.dst);
	}

	private void emitPrint(Print p) {
		loadTemp(p.src);
		Code.loadConst(p.width);
		Code.put(p.getOp() == IRInstruction.Op.BPRINT ? Code.bprint : Code.print);
	}

	private void emitTrap(Trap t) {
		Code.put(Code.trap);
		Code.put(t.code);
	}

	private void emitTvfInit(TvfInit tvf) {
		// Resolve inherited method addresses now that all methods are emitted
		if (tvf.virtualMethods != null)
			tvf.virtualMethods.resolveAdr();
		int adr = tvf.startAddress;
		for (int i = 0; i < tvf.methodNames.length; i++) {
			String name = tvf.methodNames[i];
			for (int j = 0; j < name.length(); j++) {
				Code.loadConst(name.charAt(j));
				Code.put(Code.putstatic);
				Code.put2(adr++);
			}
			Code.loadConst(-1);
			Code.put(Code.putstatic);
			Code.put2(adr++);
			Code.loadConst(tvf.methods[i].getAdr());
			Code.put(Code.putstatic);
			Code.put2(adr++);
		}
		Code.loadConst(-2);
		Code.put(Code.putstatic);
		Code.put2(adr++);
	}

	/**
	 * Allocate physical local variable slots for IR temps.
	 * Cross-block temps get unique slots (safe). Single-block temps can reuse
	 * slots from other single-block temps in different blocks.
	 * This keeps total locals under 128 (MJ VM signed byte operand limit).
	 */
	private int allocateTempSlots(IRMethod method, int maxTemp) {
		if (maxTemp < 0) {
			tempSlotMap = new int[0];
			return 0;
		}

		int numTemps = maxTemp + 1;
		tempSlotMap = new int[numTemps];

		// Track which block each temp is defined and used in
		int[] defBlock = new int[numTemps];
		int[] lastUseBlock = new int[numTemps];
		java.util.Arrays.fill(defBlock, -1);
		java.util.Arrays.fill(lastUseBlock, -1);

		int blockIdx = 0;
		for (IRBasicBlock block : method.getBlocks()) {
			for (IRInstruction instr : block.getInstructions()) {
				for (int t : getDefsIn(instr)) {
					if (t >= 0 && t < numTemps && defBlock[t] == -1)
						defBlock[t] = blockIdx;
				}
				for (int t : getUsesIn(instr)) {
					if (t >= 0 && t < numTemps)
						lastUseBlock[t] = blockIdx;
				}
			}
			blockIdx++;
		}

		// Phase 1: assign unique slots to cross-block temps
		int slotsUsed = 0;
		boolean[] isCrossBlock = new boolean[numTemps];
		for (int t = 0; t < numTemps; t++) {
			if (defBlock[t] == -1) {
				tempSlotMap[t] = 0; // unused
				continue;
			}
			if (defBlock[t] != lastUseBlock[t]) {
				isCrossBlock[t] = true;
				tempSlotMap[t] = slotsUsed++;
			}
		}

		// Phase 2: for single-block temps, reuse slots within each block
		// Scan each block: find last use of each temp, then allocate/free slots
		int localBlockSlots = slotsUsed; // high-water mark
		blockIdx = 0;
		for (IRBasicBlock block : method.getBlocks()) {
			// Find last use index of each single-block temp in this block
			Map<Integer, Integer> lastUseIdx = new HashMap<>();
			int idx = 0;
			for (IRInstruction instr : block.getInstructions()) {
				for (int t : getUsesIn(instr)) {
					if (t >= 0 && t < numTemps && !isCrossBlock[t] && defBlock[t] == blockIdx)
						lastUseIdx.put(t, idx);
				}
				idx++;
			}

			// Allocate slots, freeing after last use
			List<Integer> freePool = new ArrayList<>();
			// Seed pool with slots from slotsUsed onwards
			Map<Integer, Integer> tempToSlot = new HashMap<>();
			idx = 0;
			for (IRInstruction instr : block.getInstructions()) {
				// Allocate slots for temps defined here
				for (int t : getDefsIn(instr)) {
					if (t >= 0 && t < numTemps && !isCrossBlock[t] && defBlock[t] == blockIdx) {
						int slot;
						if (!freePool.isEmpty()) {
							slot = freePool.remove(freePool.size() - 1);
						} else {
							slot = localBlockSlots++;
						}
						tempSlotMap[t] = slot;
						tempToSlot.put(t, slot);
					}
				}
				// Free slots for temps whose last use is this instruction
				for (int t : getUsesIn(instr)) {
					if (t >= 0 && t < numTemps && !isCrossBlock[t] && defBlock[t] == blockIdx) {
						Integer lu = lastUseIdx.get(t);
						if (lu != null && lu == idx && tempToSlot.containsKey(t)) {
							freePool.add(tempToSlot.get(t));
						}
					}
				}
				idx++;
			}
			blockIdx++;
		}

		return localBlockSlots;
	}

	private int[] getDefsIn(IRInstruction instr) {
		if (instr instanceof Const)      return new int[]{((Const)instr).dst};
		if (instr instanceof Load)       return new int[]{((Load)instr).dst};
		if (instr instanceof BinOp)      return new int[]{((BinOp)instr).dst};
		if (instr instanceof Neg)        return new int[]{((Neg)instr).dst};
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

	private int[] getUsesIn(IRInstruction instr) {
		if (instr instanceof Store)      return new int[]{((Store)instr).src};
		if (instr instanceof BinOp)      { BinOp b = (BinOp) instr; return new int[]{b.src1, b.src2}; }
		if (instr instanceof Neg)        return new int[]{((Neg)instr).src};
		if (instr instanceof GetField)   return new int[]{((GetField)instr).objTemp};
		if (instr instanceof PutField)   { PutField p = (PutField) instr; return new int[]{p.objTemp, p.src}; }
		if (instr instanceof ALoad)      { ALoad a = (ALoad) instr; return new int[]{a.arrTemp, a.idxTemp}; }
		if (instr instanceof AStore)     { AStore a = (AStore) instr; return new int[]{a.arrTemp, a.idxTemp, a.src}; }
		if (instr instanceof ArrLen)     return new int[]{((ArrLen)instr).arrTemp};
		if (instr instanceof NewArray)   return new int[]{((NewArray)instr).sizeTemp};
		if (instr instanceof CJump)      { CJump c = (CJump) instr; return new int[]{c.src1, c.src2}; }
		if (instr instanceof ReturnVal)  return new int[]{((ReturnVal)instr).src};
		if (instr instanceof Call)       { Call c = (Call) instr; int[] r = new int[c.argTemps.size()]; for (int i = 0; i < r.length; i++) r[i] = c.argTemps.get(i); return r; }
		if (instr instanceof InvokeVirtual) { InvokeVirtual iv = (InvokeVirtual) instr; int[] r = new int[iv.argTemps.size() + 1]; r[0] = iv.objTemp; for (int i = 0; i < iv.argTemps.size(); i++) r[i+1] = iv.argTemps.get(i); return r; }
		if (instr instanceof Print)      return new int[]{((Print)instr).src};
		return new int[]{};
	}

	private int findMaxTemp(IRMethod method) {
		int max = -1;
		for (IRBasicBlock block : method.getBlocks()) {
			for (IRInstruction instr : block.getInstructions()) {
				max = Math.max(max, maxTempIn(instr));
			}
		}
		return max;
	}

	private int maxTempIn(IRInstruction instr) {
		int m = -1;
		if (instr instanceof Const)       { m = ((Const)instr).dst; }
		else if (instr instanceof Load)   { m = ((Load)instr).dst; }
		else if (instr instanceof Store)  { m = ((Store)instr).src; }
		else if (instr instanceof BinOp)  {
			BinOp b = (BinOp) instr;
			m = Math.max(b.dst, Math.max(b.src1, b.src2));
		}
		else if (instr instanceof Neg)    {
			m = Math.max(((Neg)instr).dst, ((Neg)instr).src);
		}
		else if (instr instanceof GetField) {
			GetField g = (GetField) instr;
			m = Math.max(g.dst, g.objTemp);
		}
		else if (instr instanceof PutField) {
			PutField p = (PutField) instr;
			m = Math.max(p.objTemp, p.src);
		}
		else if (instr instanceof ALoad)  {
			ALoad a = (ALoad) instr;
			m = Math.max(a.dst, Math.max(a.arrTemp, a.idxTemp));
		}
		else if (instr instanceof AStore) {
			AStore a = (AStore) instr;
			m = Math.max(a.arrTemp, Math.max(a.idxTemp, a.src));
		}
		else if (instr instanceof ArrLen) {
			m = Math.max(((ArrLen)instr).dst, ((ArrLen)instr).arrTemp);
		}
		else if (instr instanceof NewArray) {
			m = Math.max(((NewArray)instr).dst, ((NewArray)instr).sizeTemp);
		}
		else if (instr instanceof NewObj)  { m = ((NewObj)instr).dst; }
		else if (instr instanceof CJump)  {
			m = Math.max(((CJump)instr).src1, ((CJump)instr).src2);
		}
		else if (instr instanceof ReturnVal) { m = ((ReturnVal)instr).src; }
		else if (instr instanceof Call)   {
			Call c = (Call) instr;
			m = c.dst;
			for (int t : c.argTemps) m = Math.max(m, t);
		}
		else if (instr instanceof InvokeVirtual) {
			InvokeVirtual iv = (InvokeVirtual) instr;
			m = Math.max(iv.dst, iv.objTemp);
			for (int t : iv.argTemps) m = Math.max(m, t);
		}
		else if (instr instanceof Read)   { m = ((Read)instr).dst; }
		else if (instr instanceof Print)  { m = ((Print)instr).src; }
		return m;
	}
}
