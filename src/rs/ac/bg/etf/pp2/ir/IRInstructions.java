package rs.ac.bg.etf.pp2.ir;

import java.util.List;
import rs.ac.bg.etf.pp1.VirtualMethods;
import rs.etf.pp1.symboltable.concepts.Obj;

public class IRInstructions {

    // =========== Constants & Load/Store ===========

    /** CONST dst, value — load an integer constant into a temp */
    public static class Const extends IRInstruction {
        public final int dst;
        public final int value;

        public Const(int dst, int value) {
            super(Op.CONST);
            this.dst = dst;
            this.value = value;
        }

        public String toString() {
            return "    CONST t" + dst + ", " + value;
        }
    }

    /** LOAD dst, symbol — load a variable/field/constant from symbol table */
    public static class Load extends IRInstruction {
        public final int dst;
        public final Obj symbol;

        public Load(int dst, Obj symbol) {
            super(Op.LOAD);
            this.dst = dst;
            this.symbol = symbol;
        }

        public String toString() {
            return "    LOAD t" + dst + ", " + symbol.getName();
        }
    }

    /** STORE symbol, src — store temp into a variable */
    public static class Store extends IRInstruction {
        public final Obj symbol;
        public final int src;

        public Store(Obj symbol, int src) {
            super(Op.STORE);
            this.symbol = symbol;
            this.src = src;
        }

        public String toString() {
            return "    STORE " + symbol.getName() + ", t" + src;
        }
    }

    // =========== Arithmetic ===========

    /** Binary arithmetic: dst = src1 op src2 */
    public static class BinOp extends IRInstruction {
        public final int dst;
        public final int src1;
        public final int src2;

        public BinOp(Op op, int dst, int src1, int src2) {
            super(op);
            this.dst = dst;
            this.src1 = src1;
            this.src2 = src2;
        }

        public String toString() {
            return "    " + op + " t" + dst + ", t" + src1 + ", t" + src2;
        }
    }

    /** NEG dst, src — unary negation */
    public static class Neg extends IRInstruction {
        public final int dst;
        public final int src;

        public Neg(int dst, int src) {
            super(Op.NEG);
            this.dst = dst;
            this.src = src;
        }

        public String toString() {
            return "    NEG t" + dst + ", t" + src;
        }
    }

    /** INC symbol, value — increment variable by constant */
    public static class Inc extends IRInstruction {
        public final Obj symbol;
        public final int value;

        public Inc(Obj symbol, int value) {
            super(Op.INC);
            this.symbol = symbol;
            this.value = value;
        }

        public String toString() {
            return "    INC " + symbol.getName() + ", " + value;
        }
    }

    // =========== Fields ===========

    /** GETFIELD dst, objTemp, offset — load object field */
    public static class GetField extends IRInstruction {
        public final int dst;
        public final int objTemp;
        public final int offset;

        public GetField(int dst, int objTemp, int offset) {
            super(Op.GETFIELD);
            this.dst = dst;
            this.objTemp = objTemp;
            this.offset = offset;
        }

        public String toString() {
            return "    GETFIELD t" + dst + ", t" + objTemp + ", " + offset;
        }
    }

    /** PUTFIELD objTemp, offset, src — store into object field */
    public static class PutField extends IRInstruction {
        public final int objTemp;
        public final int offset;
        public final int src;

        public PutField(int objTemp, int offset, int src) {
            super(Op.PUTFIELD);
            this.objTemp = objTemp;
            this.offset = offset;
            this.src = src;
        }

        public String toString() {
            return "    PUTFIELD t" + objTemp + ", " + offset + ", t" + src;
        }
    }

    // =========== Arrays ===========

    /** ALOAD/BALOAD dst, arrTemp, idxTemp — load from array */
    public static class ALoad extends IRInstruction {
        public final int dst;
        public final int arrTemp;
        public final int idxTemp;

        public ALoad(Op op, int dst, int arrTemp, int idxTemp) {
            super(op); // ALOAD or BALOAD
            this.dst = dst;
            this.arrTemp = arrTemp;
            this.idxTemp = idxTemp;
        }

        public String toString() {
            return "    " + op + " t" + dst + ", t" + arrTemp + "[t" + idxTemp + "]";
        }
    }

    /** ASTORE/BASTORE arrTemp, idxTemp, src — store into array */
    public static class AStore extends IRInstruction {
        public final int arrTemp;
        public final int idxTemp;
        public final int src;

        public AStore(Op op, int arrTemp, int idxTemp, int src) {
            super(op); // ASTORE or BASTORE
            this.arrTemp = arrTemp;
            this.idxTemp = idxTemp;
            this.src = src;
        }

        public String toString() {
            return "    " + op + " t" + arrTemp + "[t" + idxTemp + "], t" + src;
        }
    }

    /** ARRLEN dst, arrTemp — get array length */
    public static class ArrLen extends IRInstruction {
        public final int dst;
        public final int arrTemp;

        public ArrLen(int dst, int arrTemp) {
            super(Op.ARRLEN);
            this.dst = dst;
            this.arrTemp = arrTemp;
        }

        public String toString() {
            return "    ARRLEN t" + dst + ", t" + arrTemp;
        }
    }

    /** NEWARRAY dst, elemType, sizeTemp — allocate new array */
    public static class NewArray extends IRInstruction {
        public final int dst;
        public final int elemType; // 0 = char/byte, 1 = int/reference
        public final int sizeTemp;

        public NewArray(int dst, int elemType, int sizeTemp) {
            super(Op.NEWARRAY);
            this.dst = dst;
            this.elemType = elemType;
            this.sizeTemp = sizeTemp;
        }

        public String toString() {
            return "    NEWARRAY t" + dst + ", " + (elemType == 0 ? "byte" : "word") + ", t" + sizeTemp;
        }
    }

    // =========== Objects ===========

    /** NEW_OBJ dst, size — allocate new object */
    public static class NewObj extends IRInstruction {
        public final int dst;
        public final int size;

        public NewObj(int dst, int size) {
            super(Op.NEW_OBJ);
            this.dst = dst;
            this.size = size;
        }

        public String toString() {
            return "    NEW t" + dst + ", " + size;
        }
    }

    // =========== Control Flow ===========

    /** JUMP label — unconditional jump */
    public static class Jump extends IRInstruction {
        public final IRBasicBlock target;

        public Jump(IRBasicBlock target) {
            super(Op.JUMP);
            this.target = target;
        }

        public String toString() {
            return "    JUMP " + target.getLabel();
        }
    }

    /** CJUMP relop, src1, src2, trueTarget, falseTarget — conditional jump */
    public static class CJump extends IRInstruction {
        public final int relOp; // Code.eq, Code.ne, Code.lt, etc.
        public final int src1;
        public final int src2;
        public final IRBasicBlock trueTarget;
        public final IRBasicBlock falseTarget;

        public CJump(int relOp, int src1, int src2, IRBasicBlock trueTarget, IRBasicBlock falseTarget) {
            super(Op.CJUMP);
            this.relOp = relOp;
            this.src1 = src1;
            this.src2 = src2;
            this.trueTarget = trueTarget;
            this.falseTarget = falseTarget;
        }

        public String toString() {
            String[] ops = {"==", "!=", "<", "<=", ">", ">="};
            String opStr = (relOp >= 0 && relOp < ops.length) ? ops[relOp] : "?";
            return "    CJUMP t" + src1 + " " + opStr + " t" + src2
                    + " ? " + trueTarget.getLabel() + " : " + falseTarget.getLabel();
        }
    }

    // =========== Methods ===========

    /** ENTER params, locals — method entry */
    public static class Enter extends IRInstruction {
        public final int paramCount;
        public final int localCount;

        public Enter(int paramCount, int localCount) {
            super(Op.ENTER);
            this.paramCount = paramCount;
            this.localCount = localCount;
        }

        public String toString() {
            return "    ENTER " + paramCount + ", " + localCount;
        }
    }

    /** EXIT — method exit (restore stack frame) */
    public static class Exit extends IRInstruction {
        public Exit() {
            super(Op.EXIT);
        }

        public String toString() {
            return "    EXIT";
        }
    }

    /** RETURN — return void */
    public static class Return extends IRInstruction {
        public Return() {
            super(Op.RETURN);
        }

        public String toString() {
            return "    RETURN";
        }
    }

    /** RETURN_VAL src — return with value */
    public static class ReturnVal extends IRInstruction {
        public final int src;

        public ReturnVal(int src) {
            super(Op.RETURN_VAL);
            this.src = src;
        }

        public String toString() {
            return "    RETURN t" + src;
        }
    }

    /** PARAM src — push argument for upcoming call */
    public static class Param extends IRInstruction {
        public final int src;

        public Param(int src) {
            super(Op.PARAM);
            this.src = src;
        }

        public String toString() {
            return "    PARAM t" + src;
        }
    }

    /** CALL dst, method, argTemps — static method call */
    public static class Call extends IRInstruction {
        public final int dst;       // -1 if void
        public final Obj method;
        public final List<Integer> argTemps;

        public Call(int dst, Obj method, List<Integer> argTemps) {
            super(Op.CALL);
            this.dst = dst;
            this.method = method;
            this.argTemps = argTemps;
        }

        public String toString() {
            String result = dst >= 0 ? "t" + dst + " = " : "";
            return "    " + result + "CALL " + method.getName() + " (" + argTemps.size() + " args)";
        }
    }

    /** INVOKE_VIRTUAL dst, objTemp, methodName, argTemps — virtual method call */
    public static class InvokeVirtual extends IRInstruction {
        public final int dst;       // -1 if void
        public final int objTemp;
        public final String methodName;
        public final List<Integer> argTemps; // regular args (not including 'this')

        public InvokeVirtual(int dst, int objTemp, String methodName, List<Integer> argTemps) {
            super(Op.INVOKE_VIRTUAL);
            this.dst = dst;
            this.objTemp = objTemp;
            this.methodName = methodName;
            this.argTemps = argTemps;
        }

        public String toString() {
            String result = dst >= 0 ? "t" + dst + " = " : "";
            return "    " + result + "INVOKE_VIRTUAL t" + objTemp + "." + methodName + " (" + (argTemps.size() + 1) + " args)";
        }
    }

    // =========== I/O ===========

    /** READ/BREAD dst — read input */
    public static class Read extends IRInstruction {
        public final int dst;
        public final Obj symbol; // needed for store target

        public Read(Op op, int dst, Obj symbol) {
            super(op); // READ or BREAD
            this.dst = dst;
            this.symbol = symbol;
        }

        public String toString() {
            return "    " + op + " t" + dst + " -> " + symbol.getName();
        }
    }

    /** PRINT/BPRINT src, width — print output */
    public static class Print extends IRInstruction {
        public final int src;
        public final int width;

        public Print(Op op, int src, int width) {
            super(op); // PRINT or BPRINT
            this.src = src;
            this.width = width;
        }

        public String toString() {
            return "    " + op + " t" + src + ", " + width;
        }
    }

    // =========== Special ===========

    /** TRAP code — runtime error */
    public static class Trap extends IRInstruction {
        public final int code;

        public Trap(int code) {
            super(Op.TRAP);
            this.code = code;
        }

        public String toString() {
            return "    TRAP " + code;
        }
    }

    /** Label pseudo-instruction — marks the start of a basic block */
    public static class Label extends IRInstruction {
        public final String name;

        public Label(String name) {
            super(Op.LABEL);
            this.name = name;
        }

        public String toString() {
            return name + ":";
        }
    }

    /** TVF_INIT — initialize virtual method table for a class */
    public static class TvfInit extends IRInstruction {
        public final String className;
        public final int startAddress;
        public final String[] methodNames;
        public final Obj[] methods;
        public final VirtualMethods virtualMethods; // for resolving inherited addresses

        public TvfInit(String className, int startAddress, String[] methodNames, Obj[] methods, VirtualMethods virtualMethods) {
            super(Op.TVF_INIT);
            this.className = className;
            this.startAddress = startAddress;
            this.methodNames = methodNames;
            this.methods = methods;
            this.virtualMethods = virtualMethods;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("    TVF_INIT ").append(className).append(" [");
            for (int i = 0; i < methodNames.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(methodNames[i]);
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
