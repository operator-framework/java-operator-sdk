package io.javaoperatorsdk.operator.baseapi.secondarytoprimary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SecondaryToPrimaryIT {

    public static final String PROJECT_NAME = "project1";
    public static final String PROJECT_NAME_2 = "project2";

    public static final int NUM_OF_SECONDARY = 50;
    public static List<Member> initialMembers = new ArrayList<>();
    public static List<Project> initialProjects = new ArrayList<>();

    static {
        initialProjects.add(project(PROJECT_NAME));
        initialProjects.add(project(PROJECT_NAME_2));
        for (int i = 0; i < NUM_OF_SECONDARY; i++) {
            initialMembers.add(member("member-%d".formatted(i), PROJECT_NAME));
        }
        for (int i = 0; i < NUM_OF_SECONDARY; i++) {
            initialMembers.add(member("member2-%d".formatted(i), PROJECT_NAME_2));
        }
    }

    @RegisterExtension
    static LocallyRunOperatorExtension operator =
            LocallyRunOperatorExtension.builder()
                    .withAdditionalCustomResourceDefinition(Member.class)
                    .withReconciler(new ProjectReconciler(NUM_OF_SECONDARY))
                    .withInitialPrimaryResources(SecondaryToPrimaryIT.initialProjects)
                    .withInitialSecondaryResources(SecondaryToPrimaryIT.initialMembers)
                    .build();

    @Test
    void readsSecondary() throws InterruptedException {
        await()
                .pollDelay(Duration.ofMillis(1000))
                .until(() -> operator.getReconcilerOfType(ProjectReconciler.class).getFirstTimeSecondaryResourceCount() != -1);
        assertThat(operator.getReconcilerOfType(ProjectReconciler.class).getFirstTimeSecondaryResourceCountFromCache())
                .as("Secondary resource count from cache")
                .isEqualTo(NUM_OF_SECONDARY);
        assertThat(operator.getReconcilerOfType(ProjectReconciler.class).getFirstTimeSecondaryResourceCount())
                .as("Secondary resource count")
                .isEqualTo(NUM_OF_SECONDARY);
    }

    public static Member member(String name, String projectName) {
        var member = new Member();
        member.setMetadata(new ObjectMetaBuilder().withName(name).build());
        member.setSpec(new MemberSpec());
        member.getSpec().setProjectName(projectName);
        return member;
    }

    public static Project project(String name) {
        Project project = new Project();
        project.setMetadata(new ObjectMetaBuilder().withName(name).build());
        return project;
    }
}
