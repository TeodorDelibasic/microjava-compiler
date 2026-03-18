package rs.ac.bg.etf.pp2.ir;

import java.util.ArrayList;
import java.util.List;

public class IRBasicBlock {
	private final String label;
	private final List<IRInstruction> instructions = new ArrayList<>();

	public IRBasicBlock(String label) {
		this.label = label;
	}

	public String getLabel() { return label; }

	public void add(IRInstruction instruction) { instructions.add(instruction); }

	public List<IRInstruction> getInstructions() { return instructions; }

	public boolean isEmpty() { return instructions.isEmpty(); }

	public IRInstruction getLastInstruction() {
		if (instructions.isEmpty()) return null;
		return instructions.get(instructions.size() - 1);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(label).append(":\n");
		for (IRInstruction instr : instructions) {
			sb.append(instr.toString()).append("\n");
		}
		return sb.toString();
	}
}
