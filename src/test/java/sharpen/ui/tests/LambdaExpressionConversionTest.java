package sharpen.ui.tests;

import org.eclipse.core.runtime.CoreException;
import org.junit.Test;

import java.io.IOException;

public class LambdaExpressionConversionTest extends AbstractConversionTestCase {
	@Test
	public void testBlockLambda() throws Throwable {
		runResourceTestCase("BlockLambda");
	}

	@Test
	public void testExpressionLambda() throws Throwable {
		runResourceTestCase("ExpressionLambda");
	}

	@Test
	public void testClosureLambda() throws Throwable {
		runResourceTestCase("ClosureLambda");
	}

	@Test
	public void testGenericLambda() throws Throwable {
		runResourceTestCase("GenericLambda");
	}

	@Test
	public void testMultiArgumentLambda() throws Throwable {
		runResourceTestCase("MultiArgumentLambda");
	}

	@Test
	public void testTryCatch() throws Throwable {
		runResourceTestCase("TryCatch");
	}

	@Test
	public void testNested() throws Throwable {
		runResourceTestCase("Nested");
	}

	@Test
	public void testVoidExpression() throws Throwable {
		runResourceTestCase("VoidExpression");
	}

	@Override
	protected void runResourceTestCase(String resourceName) throws IOException, CoreException {
		super.runResourceTestCase(resourcePath(resourceName));
	}

	private String resourcePath(String resourceName) {
		return "lambdas/" + resourceName;
	}
}
