package sharpen.core;

import org.eclipse.jdt.core.dom.*;
import sharpen.core.csharp.ast.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CSLambdaAnonymousClassBuilder extends AbstractNestedClassBuilder {

	private Expression _node;

	private CSClass _type;

	private CSConstructor _constructor;

	private Set<IVariableBinding> _capturedVariables = new LinkedHashSet<IVariableBinding>();

	public CSLambdaAnonymousClassBuilder(CSharpBuilder builder, Expression node) {
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
	public boolean visit(ThisExpression node) {
		//This always means _enclosing.
		pushExpression(createEnclosingThisReference(node.resolveTypeBinding(), true));
		return false;
	}

	@Override
	protected CSExpression createEnclosingThisReference(ITypeBinding enclosingClassBinding, boolean ignoreSuperclass) {
		requireEnclosingReference();
		CSExpression enclosing = new CSMemberReferenceExpression(new CSThisExpression(), "_enclosing");
		ITypeBinding binding = _currentTypeBinding;
		ITypeBinding to = enclosingClassBinding;
		while (binding != to) {
			enclosing = new CSMemberReferenceExpression(enclosing, "_enclosing");

			binding = binding.getDeclaringClass();
			if (null == binding) break;
		}
		return enclosing;
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
		IMethodBinding interfaceMethod = _node.resolveTypeBinding().getFunctionalInterfaceMethod();

		CSMethod method = new CSMethod(mappedMethodName(interfaceMethod));
		method.visibility(CSVisibility.Public);
		method.returnType(mappedTypeReference(interfaceMethod.getReturnType()));

		if (_node instanceof LambdaExpression) {
			processLambdaExpression(interfaceMethod, method);
		} else if (_node instanceof MethodReference) {
			List<CSExpression> parameters = new ArrayList<CSExpression>();
			ITypeBinding[] parameterTypes = interfaceMethod.getParameterTypes();
			for (int i = 0; i < parameterTypes.length; i++) {
				ITypeBinding parameterType = parameterTypes[i];
				String parameterName = "_a" + i;
				parameters.add(new CSReferenceExpression(parameterName));
				method.addParameter(parameterName, mappedTypeReference(parameterType));
			}

			CSExpression expression = createMethodInvocationFromReference(parameters);

			addExpressionStatement(interfaceMethod, method, expression);
		}
		_type.addMember(method);
	}

	private CSExpression createMethodInvocationFromReference(List<CSExpression> arguments) {
		if (_node instanceof CreationReference) {
			CreationReference node = (CreationReference) _node;
			CSConstructorInvocationExpression expression = new CSConstructorInvocationExpression(mappedTypeReference(node.getType()));

			for (CSExpression parameter : arguments) {
				expression.addArgument(parameter);
			}

			return expression;
		}

		if (_node instanceof ExpressionMethodReference) {
			ExpressionMethodReference node = (ExpressionMethodReference) _node;

			return createMethodReferenceExpression(node, arguments);
		}

		return null;
	}

	private CSExpression createMethodReferenceExpression(ExpressionMethodReference node, List<CSExpression> arguments) {
		IMethodBinding binding = node.resolveMethodBinding();

		CSExpression methodTarget;
		if (Modifier.isStatic(binding.getModifiers())) {
			methodTarget = createEnclosingTargetReferences(node.getName());
		} else if (node.getExpression() instanceof ThisExpression) {
			requireEnclosingReference();
			methodTarget = createEnclosingTargetReferences(node.getName());
		} else {
			methodTarget = arguments.remove(0);
		}

		Configuration.MemberMapping mapping = mappingForInvocation(binding);

		if (mapping != null && mapping.kind == MemberKind.Indexer) {
			return createIndexerInvocation(methodTarget, arguments);
		}

		CSMemberReferenceExpression referenceExpression = new CSMemberReferenceExpression(methodTarget, mappedMethodName(binding));

		if (mapping == null || mapping.kind == MemberKind.Method) {
			CSMethodInvocationExpression invocationExpression = new CSMethodInvocationExpression(referenceExpression);
			for (CSExpression argument : arguments) {
				invocationExpression.addArgument(argument);
			}

			return invocationExpression;
		}

		return referenceExpression;
	}

	private void processLambdaExpression(IMethodBinding interfaceMethod, CSMethod method) {
		LambdaExpression node = (LambdaExpression) _node;
		for (Object p : node.parameters()) {
			mapParameter((VariableDeclaration) p, method);
		}

		CSBlock saved = _currentBlock;
		_currentBlock = method.body();
		_currentContinueLabel = null;
		final ASTNode body = node.getBody();
		if (body instanceof Block) {
			body.accept(this);
		} else {
			addExpressionStatement(interfaceMethod, method, mapExpression((Expression) body));
		}
		_currentBlock = saved;
	}

	private void addExpressionStatement(IMethodBinding interfaceMethod, CSMethod method, CSExpression expression) {
		if (isJavaLangVoid(interfaceMethod.getReturnType())) {
			method.body().addStatement(expression);
		} else {
			method.body().addStatement(new CSReturnStatement(-1, expression));
		}
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
						final IMethodBinding declaringMethod = variable.getDeclaringMethod();
						return declaringMethod != _currentMethodBinding && declaringMethod != getResolveMethodBinding();
					}
				}
				return false;
			}
		});
	}

	private IMethodBinding getResolveMethodBinding() {
		if (_node instanceof LambdaExpression)
		return ((LambdaExpression)_node).resolveMethodBinding();
		return  null;
	}

}
