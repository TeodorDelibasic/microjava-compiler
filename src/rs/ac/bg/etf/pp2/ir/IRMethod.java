package rs.ac.bg.etf.pp2.ir;

import java.util.ArrayList;
import java.util.List;
import rs.etf.pp1.symboltable.concepts.Obj;

public class IRMethod {
    private final String name;
    private final Obj symbol;       // symbol table entry for this method
    private final List<IRBasicBlock> blocks = new ArrayList<>();
    private IRBasicBlock entryBlock;

    private int paramCount;
    private int localCount;

    public IRMethod(String name, Obj symbol) {
        this.name = name;
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public Obj getSymbol() {
        return symbol;
    }

    public void addBlock(IRBasicBlock block) {
        blocks.add(block);
        if (entryBlock == null) {
            entryBlock = block;
        }
    }

    public List<IRBasicBlock> getBlocks() {
        return blocks;
    }

    public IRBasicBlock getEntryBlock() {
        return entryBlock;
    }

    public void setParamCount(int count) {
        this.paramCount = count;
    }

    public void setLocalCount(int count) {
        this.localCount = count;
    }

    public int getParamCount() {
        return paramCount;
    }

    public int getLocalCount() {
        return localCount;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("method ").append(name);
        sb.append(" (params=").append(paramCount);
        sb.append(", locals=").append(localCount).append("):\n");
        for (IRBasicBlock block : blocks) {
            sb.append(block.toString());
        }
        return sb.toString();
    }
}
