package rs.ac.bg.etf.pp2.ir;

public abstract class IRInstruction {

    public enum Op {
        // Constants & Load/Store
        CONST,          // CONST dst, value
        LOAD,           // LOAD dst, symbol
        STORE,          // STORE symbol, src

        // Arithmetic
        ADD, SUB, MUL, DIV, REM,  // dst = src1 op src2
        NEG,            // dst = -src1
        INC,            // INC symbol, value

        // Fields
        GETFIELD,       // dst = obj.field[offset]
        PUTFIELD,       // obj.field[offset] = src

        // Arrays
        ALOAD,          // dst = arr[idx]   (int array)
        BALOAD,         // dst = arr[idx]   (char/byte array)
        ASTORE,         // arr[idx] = src   (int array)
        BASTORE,        // arr[idx] = src   (char/byte array)
        ARRLEN,         // dst = arr.length
        NEWARRAY,       // dst = new type[size]

        // Objects
        NEW_OBJ,        // dst = new Object(size)

        // Control flow
        JUMP,           // goto label
        CJUMP,          // if src1 relop src2 goto trueLabel else falseLabel

        // Methods
        ENTER,          // enter method (params, locals)
        EXIT,           // exit method
        RETURN,         // return void
        RETURN_VAL,     // return src
        CALL,           // dst = call method(args)
        INVOKE_VIRTUAL, // dst = obj.method(args) via vtable
        PARAM,          // push argument for next call

        // I/O
        READ,           // dst = read()
        BREAD,          // dst = bread()  (char)
        PRINT,          // print src, width
        BPRINT,         // bprint src, width (char)

        // Special
        TRAP,           // runtime error
        LABEL,          // pseudo-instruction: marks a label

        // TVF (virtual table)
        TVF_INIT,       // initialize virtual method table
    }

    protected final Op op;

    protected IRInstruction(Op op) {
        this.op = op;
    }

    public Op getOp() {
        return op;
    }

    public abstract String toString();
}
