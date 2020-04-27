package sharpen.core;

import org.eclipse.jdt.core.dom.*;
import sharpen.core.csharp.ast.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class CSLambdaAnonymousClassBuilder extends AbstractNestedClassBuilder {

	private LambdaExpression _node;

	private CSClass _type;

	private CSConstructor _constructor;

	private Set<IVariableBinding> _capturedVariables = new LinkedHashSet<IVariableBinding>();

	public CSLambdaAnonymousClassBuilder(CSharpBuilder builder, LambdaExpression node) {
		super(builder);
		_node = node;
		run();
	}

	public CSClass type() {
		return _type;
	}

	public Set<IVariableBinding> capturedVariables() {
		return _capturedVariables;
	}

	public CSExpression createConstructorInvocation() {
		CSConstructorInvocationExpression invocation = new CSConstructorInvocationExpression(new CSReferenceExpression(_type.name()));
		if (isEnclosingReferenceRequired()) {
			invocation.addArgument(new CSThisExpression());
		}
		addCapturedVariables(invocation);
		return invocation;
	}

	private void addCapturedVariables(CSConstructorInvocationExpression invocation) {
		for (IVariableBinding variable : _capturedVariables) {
			invocation.addArgument(new CSReferenceExpression(identifier(variable.getName())));
		}
	}

	public void run() {
		captureExternalLocalVariables();
		setUpAnonymousType();
		setUpConstructor();
		setUpMethod();
		flushCapturedVariables();
		if (_constructor.body().statements().size() > 0) {
			//add constructor if it is not empty
			_type.addMember(_constructor);
		}
	}

	public boolean visit(AnonymousClassDeclaration node) {
		CSAnonymousClassBuilder builder = new CSAnonymousClassBuilder(this, node);
		if (builder.isEnclosingReferenceRequired()) {
			requireEnclosingReference();
		}
		captureNeededVariables(builder);
		pushExpression(builder.createConstructorInvocation());
		_currentType.addMember(builder.type());
		return false;
	}

	private void captureNeededVariables(CSAnonymousClassBuilder builder) {

		IMethodBinding currentMethod = currentMethodDeclarationBinding();
		for (IVariableBinding variable : builder.capturedVariables()) {
			IMethodBinding method = variable.getDeclaringMethod();
			if (method != currentMethod) {
				_capturedVariables.add(variable);
			}
		}
	}

	private IMethodBinding currentMethodDeclarationBinding() {
		return _currentBodyDeclaration instanceof MethodDeclaration
			? ((MethodDeclaration)_currentBodyDeclaration).resolveBinding()
			: null;
	}

	private void addFieldParameter(String name, CSTypeReferenceExpression type) {
		addFieldParameter(CSharpCode.newPrivateReadonlyField(name, type));
	}

	private void addFieldParameter(CSField field) {
		_type.addMember(field);

		String parameterName = field.name();
		_constructor.addParameter(parameterName, field.type());
		addToConstructor(createFieldAssignment(field.name(), parameterName));
	}

	private void addToConstructor(final CSExpression expression) {
		_constructor.body().addStatement(expression);
	}

	private String anonymousBaseTypeName() {
		return mappedTypeName(anonymousBaseType());
	}

	public ITypeBinding anonymousBaseType() {
		return nestedTypeBinding();
	}

	@Override
	protected ITypeBinding nestedTypeBinding() {
		return _node.resolveTypeBinding();
	}

	@Override
	protected CSExpression createEnclosingThisReference(ITypeBinding enclosingClassBinding, boolean ignoreSuperclass) {
		//We do not have superclass
		return super.createEnclosingThisReference(enclosingClassBinding, true);
	}

	private String anonymousInnerClassName() {
		return "_" + simpleName(anonymousBaseTypeName()) + "_" + lineNumber(_node);
	}

	private String simpleName(String typeName) {
		final int index = typeName.lastIndexOf('.');
		if (index < 0) return typeName;
		return typeName.substring(index + 1);
	}

	private void setUpAnonymousType() {
		_type = classForAnonymousType();
	}

	private CSClass classForAnonymousType() {
		CSClass type = new CSClass(anonymousInnerClassName(), CSClassModifier.Sealed);
		type.visibility(CSVisibility.Private);
		ITypeBinding bt = anonymousBaseType();
		CSTypeReference tref = new CSTypeReference(anonymousBaseTypeName());
		type.addBaseType(tref);
		for (ITypeBinding arg : bt.getTypeArguments()) {
			tref.addTypeArgument(mappedTypeReference(arg));
		}
		ITypeBinding tt = anonymousBaseType();
		for (ITypeBinding tp : tt.getTypeParameters())
			type.addTypeParameter(new CSTypeParameter(identifier(tp.getName())));
		return type;
	}

	private void setUpConstructor() {
		_constructor = new CSConstructor();
		_constructor.visibility(CSVisibility.Public);
	}

	private void setUpMethod() {
		final IMethodBinding methodBinding = _node.resolveMethodBinding();
		CSMethod method = new CSMethod(mappedMethodName(methodBinding));
		method.visibility(CSVisibility.Public);
		method.returnType(mappedTypeReference(methodBinding.getReturnType()));
		for (Object p : _node.parameters()) {
			mapParameter((VariableDeclaration) p, method);
		}
		CSBlock saved = _currentBlock;
		_currentBlock = method.body();
		_currentContinueLabel = null;
		final ASTNode body = _node.getBody();
		if (body instanceof Block) {
			body.accept(this);
		} else {
			Expression expression = (Expression) body;
			if (isJavaLangVoid(methodBinding.getReturnType())) {
				method.body().addStatement(mapExpression(expression));
			} else {
				method.body().addStatement(new CSReturnStatement(-1, mapExpression(expression)));
			}
		}
		_currentBlock = saved;

		_type.addMember(method);
	}

	private int flushCapturedVariables() {
		int capturedVariableCount = 0;
		if (isEnclosingReferenceRequired()) {
			capturedVariableCount++;
			CSField ef = createEnclosingField();
			addFieldParameter(ef);
			ITypeBinding bt = anonymousBaseType();
			if (bt != null && isNonStaticNestedType (bt)) {
				if (null == _constructor.chainedConstructorInvocation ())
					_constructor.chainedConstructorInvocation(new CSConstructorInvocationExpression(new CSBaseExpression()));
				_constructor.chainedConstructorInvocation().addArgument(new CSReferenceExpression(ef.name()));
			}
		}

		for (IVariableBinding variable : _capturedVariables) {
			capturedVariableCount++;
			addFieldParameter(identifier(variable.getName()), mappedTypeReference(variable.getType()));
		}

		return capturedVariableCount;
	}

	private void captureExternalLocalVariables() {
		_node.accept(new ASTVisitor() {

			IMethodBinding _currentMethodBinding;

			public boolean visit(MethodDeclaration node) {
				IMethodBinding saved = _currentMethodBinding;
				_currentMethodBinding = node.resolveBinding();
				node.getBody().accept(this);
				_currentMethodBinding = saved;
				return false;
			}

			public boolean visit(LambdaExpression node) {
				return node == _node;
			}

			public boolean visit(SimpleName node) {
				IBinding binding = node.resolveBinding();
				if (isExternalLocal(binding)) {
					_capturedVariables.add((IVariableBinding)binding);
				}
				return false;
			}

			boolean isExternalLocal(IBinding binding) {
				if (binding instanceof IVariableBinding) {
					IVariableBinding variable = (IVariableBinding)binding;
					if (!variable.isField()) {
						return variable.getDeclaringMethod() != _currentMethodBinding;
					}
				}
				return false;
			}
		});
	}

}
