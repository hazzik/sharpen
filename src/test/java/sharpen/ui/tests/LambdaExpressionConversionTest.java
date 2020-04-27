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

	@Override
	protected void runResourceTestCase(String resourceName) throws IOException, CoreException {
		super.runResourceTestCase(resourcePath(resourceName));
	}

	private String resourcePath(String resourceName) {
		return "lambdas/" + resourceName;
	}
}
