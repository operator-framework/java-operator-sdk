package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

public class AbstractWorkflowExecutorTest {
  public static final String VALUE = "value";

  protected TestDependent dr1 = new TestDependent("DR_1");
  protected TestDependent dr2 = new TestDependent("DR_2");
  protected TestDeleterDependent drDeleter = new TestDeleterDependent("DR_DELETER");
  protected TestErrorDependent drError = new TestErrorDependent("ERROR_1");
  protected TestErrorDeleterDependent errorDD = new TestErrorDeleterDependent("ERROR_DELETER");

  protected final Condition noMetDeletePostCondition =
      (dependentResource, primary, context) -> false;
  protected final Condition metDeletePostCondition =
      (dependentResource, primary, context) -> true;

  protected List<ReconcileRecord> executionHistory =
      Collections.synchronizedList(new ArrayList<>());

  public class TestDependent implements DependentResource<String, TestCustomResource> {

    private String name;

    public TestDependent(String name) {
      this.name = name;
    }

    @Override
    public ReconcileResult<String> reconcile(TestCustomResource primary,
        Context<TestCustomResource> context) {
      executionHistory.add(new ReconcileRecord(this));
      return ReconcileResult.resourceCreated(VALUE);
    }

    @Override
    public Class<String> resourceType() {
      return String.class;
    }

    @Override
    public Optional<String> getSecondaryResource(TestCustomResource primary) {
      return Optional.of(VALUE);
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
    private String name;

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
    public Optional<String> getSecondaryResource(TestCustomResource primary) {
      return Optional.of(VALUE);
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
