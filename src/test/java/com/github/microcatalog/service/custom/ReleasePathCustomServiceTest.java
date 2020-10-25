package com.github.microcatalog.service.custom;

import com.github.microcatalog.domain.Dependency;
import com.github.microcatalog.domain.Microservice;
import com.github.microcatalog.domain.custom.ReleaseGroup;
import com.github.microcatalog.domain.custom.ReleasePath;
import com.github.microcatalog.domain.custom.ReleaseStep;
import com.github.microcatalog.repository.DependencyRepository;
import com.github.microcatalog.repository.MicroserviceRepository;
import com.github.microcatalog.utils.DependencyBuilder;
import com.github.microcatalog.utils.MicroserviceBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@SpringBootTest(classes = ReleasePathCustomService.class)
class ReleasePathCustomServiceTest {

    @MockBean
    private MicroserviceRepository microserviceRepository;

    @MockBean
    private DependencyRepository dependencyRepository;

    @Autowired
    private ReleasePathCustomService service;

    @Test
    void getReleasePath_NoCycles_Success() {
        given(microserviceRepository.findAll()).willReturn(createMicroservices());

        given(dependencyRepository.findAll()).willReturn(createDependencies());

        Optional<ReleasePath> maybePath = service.getReleasePath(1L);
        assertThat(maybePath).isPresent();

        ReleasePath path = maybePath.get();

        ReleaseStep step = getStep(path, 0, 5L);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).extracting(Microservice::getId).containsExactlyInAnyOrder(4L, 7L);

        step = getStep(path, 0, 8L);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).extracting(Microservice::getId).containsExactlyInAnyOrder(4L);

        step = getStep(path, 1, 7L);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).extracting(Microservice::getId).containsExactlyInAnyOrder(4L);

        step = getStep(path, 2, 4L);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).extracting(Microservice::getId).containsExactlyInAnyOrder(2L);

        step = getStep(path, 3, 2L);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).extracting(Microservice::getId).containsExactlyInAnyOrder(1L);

        step = getStep(path, 4, 1L);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).isEmpty();
    }

    @Test
    void getReleasePath_ContainsCyclesInSameComponent_ExceptionIsThrown() {
        given(microserviceRepository.findAll()).willReturn(createMicroservices());

        given(dependencyRepository.findAll()).willReturn(createDependenciesWithCycleInSameComponent());

        assertThatIllegalArgumentException()
            .isThrownBy(() ->
                service.getReleasePath(1L)
            )
            .withMessageStartingWith("There are cyclic dependencies between microservices");
    }

    @Test
    void getReleasePath_ContainsCyclesInOtherComponent_Success() {
        given(microserviceRepository.findAll()).willReturn(createMicroservices());

        given(dependencyRepository.findAll()).willReturn(createDependenciesWithCycleInOtherComponent());

        Optional<ReleasePath> maybePath = service.getReleasePath(1L);
        assertThat(maybePath).isPresent();

        ReleasePath path = maybePath.get();

        ReleaseStep step = getStep(path, 0, 3);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).extracting(Microservice::getId).containsExactlyInAnyOrder(2L);

        step = getStep(path, 0, 4);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).extracting(Microservice::getId).containsExactlyInAnyOrder(2L);

        step = getStep(path, 1, 2);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).extracting(Microservice::getId).containsExactlyInAnyOrder(1L);

        step = getStep(path, 2, 1L);
        assertThat(step).isNotNull();
        assertThat(step.getParentWorkItems()).isEmpty();
    }

    private ReleaseStep getStep(final ReleasePath path, int groupIndex, long microserviceId) {
        final Set<ReleaseStep> steps = getSteps(path, groupIndex);
        if (steps != null) {
            Optional<ReleaseStep> maybeStep = steps.stream()
                .filter(s -> s.getWorkItem() != null && microserviceId == s.getWorkItem().getId()).findFirst();
            if (maybeStep.isPresent()) {
                return maybeStep.get();
            }
        }

        return null;
    }

    private Set<ReleaseStep> getSteps(ReleasePath path, int groupIndex) {
        if (path.getGroups() == null) {
            return null;
        }

        if (groupIndex > path.getGroups().size()) {
            return null;
        }

        ReleaseGroup first = path.getGroups().get(groupIndex);
        if (first != null) {
            return first.getSteps();
        } else {
            return Collections.emptySet();
        }
    }

    private List<Microservice> createMicroservices() {
        return LongStream.rangeClosed(1, 12)
            .mapToObj(i -> new MicroserviceBuilder().withId(i).build())
            .collect(Collectors.toList());
    }

    /**
     * Graph will contain two connected components, one of them has cycle
     * <p>
     * First component with cycle 1->2->3->1
     * Second component without cycle 5->6, 5->7
     *
     * @return dependencies
     */
    private List<Dependency> createDependenciesWithCycleInSameComponent() {
        final List<Dependency> dependencies = new ArrayList<>();

        // First component with cycle 1->2->3->1
        dependencies.add(new DependencyBuilder()
            .withId(1L).withSource(1L).withTarget(2L)
            .build());
        dependencies.add(new DependencyBuilder()
            .withId(2L).withSource(2L).withTarget(3L)
            .build());
        dependencies.add(new DependencyBuilder()
            .withId(3L).withSource(3L).withTarget(1L)
            .build());

        // Second component without cycle 5->6, 5->7
        dependencies.add(new DependencyBuilder()
            .withId(4L).withSource(5L).withTarget(6L)
            .build());
        dependencies.add(new DependencyBuilder()
            .withId(5L).withSource(5L).withTarget(7L)
            .build());


        return dependencies;
    }

    /**
     * Graph will contain two connected components, one of them has cycle
     * First component without cycle 1->2, 2->3, 2->4
     * Second component with cycle 6->7->8->6
     *
     * @return dependencies
     */
    private List<Dependency> createDependenciesWithCycleInOtherComponent() {
        final List<Dependency> dependencies = new ArrayList<>();

        // First component without cycle 1->2, 2->3, 2->4
        dependencies.add(new DependencyBuilder()
            .withId(1L).withSource(1L).withTarget(2L)
            .build());
        dependencies.add(new DependencyBuilder()
            .withId(2L).withSource(2L).withTarget(3L)
            .build());
        dependencies.add(new DependencyBuilder()
            .withId(3L).withSource(2L).withTarget(4L)
            .build());

        // Second component with cycle 6->7->8->6
        dependencies.add(new DependencyBuilder()
            .withId(4L).withSource(5L).withTarget(6L)
            .build());
        dependencies.add(new DependencyBuilder()
            .withId(5L).withSource(6L).withTarget(7L)
            .build());
        dependencies.add(new DependencyBuilder()
            .withId(6L).withSource(7L).withTarget(8L)
            .build());
        dependencies.add(new DependencyBuilder()
            .withId(7L).withSource(8L).withTarget(6L)
            .build());


        return dependencies;
    }

    private List<Dependency> createDependencies() {
        final List<Dependency> dependencies = new ArrayList<>();

        dependencies.add(new DependencyBuilder()
            .withId(1L).withSource(1L).withTarget(2L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(2L).withSource(2L).withTarget(4L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(3L).withSource(6L).withTarget(4L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(4L).withSource(4L).withTarget(5L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(5L).withSource(4L).withTarget(7L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(6L).withSource(4L).withTarget(8L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(7L).withSource(3L).withTarget(9L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(8L).withSource(3L).withTarget(11L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(9L).withSource(12L).withTarget(1L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(10L).withSource(7L).withTarget(5L)
            .build());

        dependencies.add(new DependencyBuilder()
            .withId(11L).withSource(10L).withTarget(1L)
            .build());

        return dependencies;
    }
}