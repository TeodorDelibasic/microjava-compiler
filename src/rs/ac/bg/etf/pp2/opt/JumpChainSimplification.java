package rs.ac.bg.etf.pp2.opt;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.pp2.ir.*;
import rs.ac.bg.etf.pp2.ir.IRInstructions.*;

public class JumpChainSimplification {

	public void optimize(IRProgram program) {
		for (IRMethod method : program.getMethods()) {
			boolean changed = true;
			while (changed) {
				changed = simplifyChains(method);
			}
			removeUnreachableBlocks(method);
		}
	}

	private boolean simplifyChains(IRMethod method) {
		// Build map: block label -> its sole jump target (if block is a pure jump)
		Map<String, IRBasicBlock> jumpOnly = new HashMap<>();
		for (IRBasicBlock block : method.getBlocks()) {
			IRBasicBlock target = getSoleJumpTarget(block);
			if (target != null) {
				jumpOnly.put(block.getLabel(), target);
			}
		}

		if (jumpOnly.isEmpty()) return false;

		// Resolve chains: follow jumpOnly links to final target
		Map<String, IRBasicBlock> resolved = new HashMap<>();
		for (String label : jumpOnly.keySet()) {
			resolved.put(label, resolveChain(label, jumpOnly));
		}

		// Rewrite all jump/cjump targets
		boolean changed = false;
		for (IRBasicBlock block : method.getBlocks()) {
			for (int i = 0; i < block.getInstructions().size(); i++) {
				IRInstruction instr = block.getInstructions().get(i);

				if (instr instanceof Jump) {
					Jump j = (Jump) instr;
					IRBasicBlock finalTarget = resolved.get(j.target.getLabel());
					if (finalTarget != null && finalTarget != j.target) {
						block.getInstructions().set(i, new Jump(finalTarget));
						changed = true;
					}
				}
				else if (instr instanceof CJump) {
					CJump c = (CJump) instr;
					IRBasicBlock newTrue = resolved.getOrDefault(c.trueTarget.getLabel(), c.trueTarget);
					IRBasicBlock newFalse = resolved.getOrDefault(c.falseTarget.getLabel(), c.falseTarget);
					if (newTrue != c.trueTarget || newFalse != c.falseTarget) {
						block.getInstructions().set(i, new CJump(c.relOp, c.src1, c.src2, newTrue, newFalse));
						changed = true;
					}
				}
			}
		}

		return changed;
	}

	/** If block contains only a single JUMP instruction, return its target */
	private IRBasicBlock getSoleJumpTarget(IRBasicBlock block) {
		List<IRInstruction> instrs = block.getInstructions();
		if (instrs.size() == 1 && instrs.get(0) instanceof Jump) {
			return ((Jump) instrs.get(0)).target;
		}
		return null;
	}

	/** Follow jump chain to final non-jump-only target */
	private IRBasicBlock resolveChain(String label, Map<String, IRBasicBlock> jumpOnly) {
		IRBasicBlock target = jumpOnly.get(label);
		int limit = 100; // prevent infinite loops
		while (target != null && jumpOnly.containsKey(target.getLabel()) && limit-- > 0) {
			target = jumpOnly.get(target.getLabel());
		}
		return target;
	}

	/** Remove blocks that are no longer referenced by any jump/cjump */
	private void removeUnreachableBlocks(IRMethod method) {
		// Collect all referenced block labels
		java.util.Set<String> referenced = new java.util.HashSet<>();
		// Entry block is always referenced
		if (!method.getBlocks().isEmpty()) {
			referenced.add(method.getBlocks().get(0).getLabel());
		}

		for (IRBasicBlock block : method.getBlocks()) {
			for (IRInstruction instr : block.getInstructions()) {
				if (instr instanceof Jump) {
					referenced.add(((Jump) instr).target.getLabel());
				} else if (instr instanceof CJump) {
					CJump c = (CJump) instr;
					referenced.add(c.trueTarget.getLabel());
					referenced.add(c.falseTarget.getLabel());
				}
			}
		}

		// Also keep blocks that are fall-through targets of CJumps
		// (the block after a CJump block is implicitly the true target)
		List<IRBasicBlock> blocks = method.getBlocks();
		for (int i = 0; i < blocks.size() - 1; i++) {
			IRInstruction last = blocks.get(i).getLastInstruction();
			if (last instanceof CJump) {
				referenced.add(blocks.get(i + 1).getLabel());
			}
		}

		// Remove unreferenced blocks (except the entry block)
		Iterator<IRBasicBlock> it = method.getBlocks().iterator();
		boolean first = true;
		while (it.hasNext()) {
			IRBasicBlock block = it.next();
			if (first) { first = false; continue; }
			if (!referenced.contains(block.getLabel())) {
				it.remove();
			}
		}
	}
}
