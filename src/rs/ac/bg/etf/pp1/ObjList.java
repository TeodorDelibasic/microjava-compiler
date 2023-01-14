package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.List;

import rs.etf.pp1.symboltable.concepts.Obj;

public class ObjList {
	
	private List<Obj> objList = new ArrayList<>();
	
	public void add(Obj obj) {
		this.objList.add(obj);
	}
	
	public int size() {
		return this.objList.size();
	}
	
	public List<Obj> getList() {
		return this.objList;
	}
	
	public boolean assignableFrom(Obj src, int ind) {
		return this.objList.get(ind) == SymbolTable.noObj || src.getType().getElemType().assignableTo(this.objList.get(ind).getType());
	}
	
	public boolean assignableTo(Obj dest, int ind) {
		return this.objList.get(ind) == SymbolTable.noObj || this.objList.get(ind).getType().assignableTo(dest.getType());
	}
	
	public boolean equalTo(Obj param, int ind) {
		return this.objList.get(ind).getType().equals(param.getType());
	}
}
