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
	
	public static boolean assignable(Struct src, Struct dst) {
		if (src.getKind() == Struct.Class && dst.getKind() == Struct.Class) {
			for (Struct parentClass = src; parentClass != null; parentClass = src.getElemType()) {
				if (parentClass == dst)
					return true;
			}
			
			return false;
		}
		
		return src.assignableTo(dst);
	}
	
}
