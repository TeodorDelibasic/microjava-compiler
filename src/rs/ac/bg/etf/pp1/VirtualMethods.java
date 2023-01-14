package rs.ac.bg.etf.pp1;

import java.util.LinkedHashMap;
import java.util.Map;

import rs.etf.pp1.symboltable.concepts.Obj;

public class VirtualMethods {
	
	private Map<String, Obj> parentMethods = new LinkedHashMap<>();
	private Map<String, Obj> childMethods = new LinkedHashMap<>();
	
	public void addParentMethod(String name, Obj method) {
		this.parentMethods.put(name, method);
	}
	
	public void addChilMethod(String name, Obj method) {
		this.childMethods.put(name, method);
	}
	
	public void removeMethod(String name) {
		this.parentMethods.remove(name);
		this.childMethods.remove(name);
	}
	
	public boolean isOverride(String name) {
		return this.parentMethods.containsKey(name);
	}

	public boolean isValidOverride(String name, ObjList params) {
		if (!this.parentMethods.containsKey(name)) {
			return true;
		}
		
		for (Obj arg : this.parentMethods.get(name).getLocalSymbols()) {
			if (!arg.getName().equals("this") && !params.equalTo(arg, arg.getFpPos())) {
				return false;
			}
		}
		
		return true;
	}

	public void resolveAdr() {
		for (String name : this.parentMethods.keySet()) {
			this.childMethods.get(name).setAdr(this.parentMethods.get(name).getAdr());
		}
	}
	
}
