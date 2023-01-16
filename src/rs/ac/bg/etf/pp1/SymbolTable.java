package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class SymbolTable extends Tab {

	public static final Struct boolType = new Struct(Struct.Bool);
	
	public static void init() {
		Tab.init();
		Tab.currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));
	}
	
	public static boolean equals(Struct s1, Struct s2) {
		if (s1.getKind() == Struct.Array && s2.getKind() == Struct.Array)
			return SymbolTable.equals(s1.getElemType(), s2.getElemType());
		
		return s1 == s2;
	}
	
	public static boolean compatible(Struct s1, Struct s2) {
		if (s1.isRefType() && s2 == SymbolTable.nullType
				|| s2.isRefType() && s1 == SymbolTable.nullType)
			return true;
		
		return SymbolTable.equals(s1, s2);
	}
	
	public static boolean assignable(Struct src, Struct dst) {
		if (dst.isRefType() && src == SymbolTable.nullType)
			return true;
		
		if (src.getKind() == Struct.Class && dst.getKind() == Struct.Class) {
			for (Struct parentClass = src; parentClass != null; parentClass = parentClass.getElemType()) {
				if (parentClass == dst)
					return true;
			}
			return false;
		}
		
		return SymbolTable.equals(src, dst);
	}
	
}
