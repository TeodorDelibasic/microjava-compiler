package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.List;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class ObjList {
	
	private List<Obj> list = new ArrayList<>();
	
	public void add(Obj obj) {
		this.list.add(obj);
	}
	
	public void addAtIndex(Obj obj, int index) {
		this.list.add(index, obj);
	}
	
	public int size() {
		return this.list.size();
	}
	
	public List<Obj> getList() {
		return this.list;
	}
	
	public boolean assignableFrom(Struct srcType, int ind) {
		return this.list.get(ind) == SymbolTable.noObj ||
				SymbolTable.assignable(srcType, this.list.get(ind).getType());
	}
	
	public boolean assignableTo(Struct destType, int ind) {
		return this.list.get(ind) == SymbolTable.noObj ||
				SymbolTable.assignable(this.list.get(ind).getType(), destType);
	}
	
	public boolean equalTo(Struct objType, int ind) {
		return this.list.get(ind) == SymbolTable.noObj || 
				SymbolTable.equals(objType, this.list.get(ind).getType());
	}
	
	public void clear() {
		this.list.clear();
	}
}
