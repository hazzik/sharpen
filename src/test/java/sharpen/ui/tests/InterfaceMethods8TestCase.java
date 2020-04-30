package sharpen.ui.tests;

import org.eclipse.core.runtime.CoreException;
import org.junit.Test;
import sharpen.core.Configuration;

import java.io.IOException;

public class InterfaceMethods8TestCase extends AbstractConversionTestCase {
	@Test
	public void testDefaultMethods() throws Throwable {
		runResourceTestCase("DefaultMethods8");
	}

	@Test
	public void testStaticMethods() throws Throwable {
		runResourceTestCase("StaticMethods8");
	}

	@Override
	protected Configuration getConfiguration() {
		Configuration configuration = super.getConfiguration();
		configuration.setCSharpVersion(8);
		return configuration;
	}

	@Override
	protected void runResourceTestCase(String resourceName) throws IOException, CoreException {
		super.runResourceTestCase("interfaces/" + resourceName);
	}
}
