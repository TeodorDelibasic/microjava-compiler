package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;

public class ErrorVisitor extends VisitorAdaptor {
	
	@Override
	public void visit(VarDeclStmtErrorComma errorNode) {
		report_error("Variable declaration to ,", errorNode);
	}
	
	@Override
	public void visit(VarDeclStmtErrorSemi errorNode) {
		report_error("Variable declaration to ;", errorNode.getParent());
	}
	
	@Override
	public void visit(ClassParentError errorNode) {
		report_error("Parent class", errorNode.getParent());
	}
	
	@Override
	public void visit(FormParErrorComma errorNode) {
		report_error("Formal parameter to ,", errorNode.getParent());
	}
	
	@Override
	public void visit(FormParErrorParen errorNode) {
		report_error("Formal parameter to )", errorNode.getParent());
	}
	
	@Override
	public void visit(DesignatorAssignOpError errorNode) {
		report_error("Assignment statement to ;", errorNode.getParent());
	}
	
	@Override
	public void visit(ConditionError errorNode) {
		report_error("Condition to )", errorNode.getParent());
	}
	
	private void report_error(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder();

		if (info != null) {
			msg.append("Syntax error recovery on line ").append(info.getLine()).append(":");
		}

		msg.append(" ").append(message);

		System.err.println(msg.toString());
	}
	
}
