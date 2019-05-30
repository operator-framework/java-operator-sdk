package jkube.operator;

import jkube.operator.api.Controller;
import jkube.operator.api.ResourceController;

@Controller(customResourceDefinitionName = TestResourceController.CUSTOM_RESOURCE_DEFINITION_NAME, customResourceClass = TestCustomResource.class)
public class TestResourceController implements ResourceController<TestCustomResource> {

    public static final String CUSTOM_RESOURCE_DEFINITION_NAME = "customResourceDefinition";

    @Override
    public void deleteResource(TestCustomResource resource, Context<TestCustomResource> context) {
    }

    @Override
    public TestCustomResource createOrUpdateResource(TestCustomResource resource, Context<TestCustomResource> context) {
        return null;
    }
}
