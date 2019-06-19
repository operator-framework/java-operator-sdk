package com.github.containersolutions.operator.spingboot.starter;

import com.github.containersolutions.operator.Operator;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class AutoconfigurationTest {

    @Autowired
    private OperatorProperties operatorProperties;


    @MockBean
    private Operator operator;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Autowired
    private List<ResourceController> resourceControllers;

    @Test
    public void configurationsLoadedProperly() {
        assertEquals("user", operatorProperties.getUsername());
        assertEquals("password", operatorProperties.getPassword());
        assertEquals("http://master.url",operatorProperties.getMasterUrl());
    }

    @Test
    public void beansCreated() {
        assertNotNull(kubernetesClient);
    }

    @Test
    public void resourceControllersAreDiscovered() {
        assertEquals(1,resourceControllers.size());
        assertTrue(resourceControllers.get(0) instanceof TestController);
    }

}
