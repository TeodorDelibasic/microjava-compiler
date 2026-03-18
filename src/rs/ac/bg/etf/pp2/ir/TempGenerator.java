package rs.ac.bg.etf.pp2.ir;

public class TempGenerator {
    private int nextTemp = 0;
    private int nextLabel = 0;
    private final String methodPrefix;

    public TempGenerator(String methodPrefix) {
        this.methodPrefix = methodPrefix;
    }

    /** Allocate a new temporary register: t0, t1, t2, ... */
    public int newTemp() {
        return nextTemp++;
    }

    /** Create a new labeled basic block */
    public IRBasicBlock newBlock(String suffix) {
        String label = methodPrefix + "_" + suffix + "_" + nextLabel++;
        return new IRBasicBlock(label);
    }

    /** Create a new block with just a number */
    public IRBasicBlock newBlock() {
        String label = methodPrefix + "_L" + nextLabel++;
        return new IRBasicBlock(label);
    }
}
