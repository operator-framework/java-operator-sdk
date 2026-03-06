/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.baseapi.ssaissue.specupdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class SSASpecUpdateReconciler
    implements Reconciler<SSASpecUpdateCustomResource>, Cleaner<SSASpecUpdateCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(SSASpecUpdateReconciler.class);

  @Override
  public UpdateControl<SSASpecUpdateCustomResource> reconcile(
      SSASpecUpdateCustomResource resource, Context<SSASpecUpdateCustomResource> context) {

    var copy = createFreshCopy(resource);
    copy.getSpec().setValue("value");
    var res =
        context
            .getClient()
            .resource(copy)
            .fieldManager(context.getControllerConfiguration().fieldManager())
            .serverSideApply();
    log.info("res: {}", res);
    return UpdateControl.noUpdate();
  }

  SSASpecUpdateCustomResource createFreshCopy(SSASpecUpdateCustomResource resource) {
    var res = new SSASpecUpdateCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    res.setSpec(new SSASpecUpdateCustomResourceSpec());
    return res;
  }

  @Override
  public DeleteControl cleanup(
      SSASpecUpdateCustomResource resource, Context<SSASpecUpdateCustomResource> context) {

    return DeleteControl.defaultDelete();
  }
}
