package rs.ac.bg.etf.pp1;

import java.util.LinkedHashMap;
import java.util.Map;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class VirtualMethods {
	
	private Map<String, Obj> parentMethods = new LinkedHashMap<>();
	private Map<String, Obj> childMethods = new LinkedHashMap<>();
	
	public void addParentMethod(String name, Obj method) {
		this.parentMethods.put(name, method);
	}
	
	public void addChilMethod(String name, Obj method) {
		this.childMethods.put(name, method);
	}
	
	public void overrideMethod(String name) {
		this.parentMethods.remove(name);
	}
	
	public boolean isOverride(String name) {
		return this.parentMethods.containsKey(name);
	}
	
	public boolean hasMethod(String methodName) {
		return this.childMethods.containsKey(methodName);
	}

	public boolean validParams(String name, ObjList params) {
		if (!this.parentMethods.containsKey(name)) {
			return true;
		}
		
		if (params.size() != this.parentMethods.get(name).getLevel()) {
			return false;
		}
		
		for (Obj arg : this.parentMethods.get(name).getLocalSymbols()) {
			if (arg.getFpPos() == -1) {
				continue;
			}
			
			if (!arg.getName().equals("this") && !params.equalTo(arg.getType(), arg.getFpPos())) {
				return false;
			}
		}
		
		return true;
	}

	public boolean validReturnType(String methodName, Struct currentMethod) {
		return SymbolTable.assignable(currentMethod, this.parentMethods.get(methodName).getType());
	}

	public void resolveAdr() {
		for (String name : this.parentMethods.keySet()) {
			this.childMethods.get(name).setAdr(this.parentMethods.get(name).getAdr());
		}
	}
	
}
