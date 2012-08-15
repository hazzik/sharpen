package sharpen.core.csharp.ast;

public class CSDeclarationExpressionFragment {
	private String _name;
	private CSExpression _initializer;

	public CSDeclarationExpressionFragment(String name, CSExpression initializer) {
		_name = name;
		_initializer = initializer;
	}
	
	public String name() {
		return _name;
	}
	
	public CSExpression initializer() {
		return _initializer;
	}
}
