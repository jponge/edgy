package org.acme.edgy.test.basic;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

class EdgyDevModeTest {

    // Start hot reload (DevMode) test with your extension loaded
    @RegisterExtension
    private static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

//    @Test
//    void writeYourOwnDevModeTest() {
//        // Write your dev mode tests here - see the testing extension guide https://quarkus.io/guides/writing-extensions#testing-hot-reload for more information
//        Assertions.assertTrue(true, "Add dev mode assertions to " + getClass().getName());
//    }
}
