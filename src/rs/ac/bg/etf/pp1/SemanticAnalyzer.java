package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	private boolean errorDetected = false;

	public boolean hasErrors() {
		return this.errorDetected;
	}
	
	private void report_error(String message, SyntaxNode info) {
		errorDetected = true;

		StringBuilder msg = new StringBuilder();

		if (info != null) {
			msg.append("Error on line ").append(info.getLine()).append("!");
		}

		msg.append(" ").append(message);

		System.err.println(msg.toString());
	}

	private void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);

		if (info != null) {
			msg.append(" na liniji ").append(info.getLine());
		}

		System.out.println(msg.toString());
	}
}
