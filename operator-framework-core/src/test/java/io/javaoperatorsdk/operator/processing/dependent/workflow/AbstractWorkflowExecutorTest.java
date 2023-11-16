package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

public class AbstractWorkflowExecutorTest {
  public static final String VALUE = "value";

  protected TestDependent dr1 = new TestDependent("DR_1");
  protected TestDependent dr2 = new TestDependent("DR_2");
  protected TestDeleterDependent drDeleter = new TestDeleterDependent("DR_DELETER");
  protected TestErrorDependent drError = new TestErrorDependent("ERROR_1");
  protected TestErrorDeleterDependent errorDD = new TestErrorDeleterDependent("ERROR_DELETER");
  protected GarbageCollectedDeleter gcDeleter = new GarbageCollectedDeleter("GC_DELETER");

  @SuppressWarnings("rawtypes")
  protected final Condition notMetCondition = (primary, secondary, context) -> false;
  @SuppressWarnings("rawtypes")
  protected final Condition metCondition = (primary, secondary, context) -> true;

  protected List<ReconcileRecord> executionHistory =
      Collections.synchronizedList(new ArrayList<>());

  public class TestDependent extends KubernetesDependentResource<ConfigMap, TestCustomResource> {

    private final String name;

    public TestDependent(String name) {
      super(ConfigMap.class);
      this.name = name;
    }

    @Override
    public ReconcileResult<ConfigMap> reconcile(TestCustomResource primary,
        Context<TestCustomResource> context) {
      executionHistory.add(new ReconcileRecord(this));
      return ReconcileResult
          .resourceCreated(new ConfigMapBuilder().addToBinaryData("key", VALUE).build());
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public class TestDeleterDependent extends TestDependent implements Deleter<TestCustomResource> {

    public TestDeleterDependent(String name) {
      super(name);
    }

    @Override
    public void delete(TestCustomResource primary, Context<TestCustomResource> context) {
      executionHistory.add(new ReconcileRecord(this, true));
    }
  }

  public class GarbageCollectedDeleter extends TestDeleterDependent
      implements GarbageCollected<TestCustomResource> {

    public GarbageCollectedDeleter(String name) {
      super(name);
    }
  }

  public class TestErrorDeleterDependent extends TestDependent
      implements Deleter<TestCustomResource> {

    public TestErrorDeleterDependent(String name) {
      super(name);
    }

    @Override
    public void delete(TestCustomResource primary, Context<TestCustomResource> context) {
      executionHistory.add(new ReconcileRecord(this, true));
      throw new IllegalStateException("Test exception");
    }
  }

  public class TestErrorDependent implements DependentResource<String, TestCustomResource> {
    private final String name;

    public TestErrorDependent(String name) {
      this.name = name;
    }

    @Override
    public ReconcileResult<String> reconcile(TestCustomResource primary,
        Context<TestCustomResource> context) {
      executionHistory.add(new ReconcileRecord(this));
      throw new IllegalStateException("Test exception");
    }

    @Override
    public Class<String> resourceType() {
      return String.class;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
