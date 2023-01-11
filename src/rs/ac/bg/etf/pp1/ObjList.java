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
	
}
