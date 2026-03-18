package rs.ac.bg.etf.pp2.ir;

import java.util.ArrayList;
import java.util.List;

public class IRProgram {
    private final String name;
    private final List<IRMethod> methods = new ArrayList<>();
    private int dataSize;   // size of global/static data area

    // TVF (Type Virtual Function) table entries
    private final List<IRInstructions.TvfInit> tvfEntries = new ArrayList<>();

    public IRProgram(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addMethod(IRMethod method) {
        methods.add(method);
    }

    public List<IRMethod> getMethods() {
        return methods;
    }

    public IRMethod getMethod(String name) {
        for (IRMethod m : methods) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    public void setDataSize(int size) {
        this.dataSize = size;
    }

    public int getDataSize() {
        return dataSize;
    }

    public void addTvfEntry(IRInstructions.TvfInit tvf) {
        tvfEntries.add(tvf);
    }

    public List<IRInstructions.TvfInit> getTvfEntries() {
        return tvfEntries;
    }

    public int countInstructions() {
        int count = 0;
        for (IRMethod m : methods) {
            for (IRBasicBlock b : m.getBlocks()) {
                count += b.getInstructions().size();
            }
        }
        return count;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== IR Program: ").append(name).append(" ===\n");
        sb.append("Data size: ").append(dataSize).append("\n\n");

        if (!tvfEntries.isEmpty()) {
            sb.append("--- TVF Entries ---\n");
            for (IRInstructions.TvfInit tvf : tvfEntries) {
                sb.append(tvf.toString()).append("\n");
            }
            sb.append("\n");
        }

        for (IRMethod method : methods) {
            sb.append(method.toString()).append("\n");
        }
        return sb.toString();
    }
}
