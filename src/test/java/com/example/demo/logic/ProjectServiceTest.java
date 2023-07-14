package com.example.demo.logic;

import com.example.demo.TaskConfigurationProperties;
import com.example.demo.model.*;
import com.example.demo.model.projection.GroupReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceTest {
    @Test
    @DisplayName("should throw IllegalStateException when configured to allow just 1 group and the other undone group exists")
    void createGroup_noMultipleGroupsConfig_And_undoneGroupsExists_throwsIllegalStateException() {
        //given
        TaskGroupRepository mockGroupRepository = groupRepositoryReturning(true);

        //and
        TaskConfigurationProperties mockConfig = configurationReturning(false);

        //system under test
        var toTest = new ProjectService(null, mockGroupRepository, mockConfig, null);

        //when
        var exception = catchThrowable(() -> toTest.createGroup(0, LocalDateTime.now()));

        //then+
        assertThat(exception)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("one undone group");
        //when + then
        /*assertThatThrownBy(() -> {-
            toTest.createGroup(0, LocalDateTime.now());
        }).isInstanceOf(IllegalStateException.class);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                toTest.createGroup(0, LocalDateTime.now()));*/

    }
    @Test
    @DisplayName("should throw IllegalArgumentException when configuration ok and no projects for a given id")
    void createGroup_configurationOK_And_noProjects_throwsIllegalStateException() {

        //given
        ProjectRepository mockRepository = projectRepositoryReturning();
        //and
        TaskConfigurationProperties mockConfig = configurationReturning(true);

        //system under test
        var toTest = new ProjectService(mockRepository, null, mockConfig, null );

        //when
        var exception = catchThrowable(() -> toTest.createGroup(0, LocalDateTime.now()));

        //then+
        assertThat(exception)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id not Found");

    }
    @Test
    @DisplayName("should throw IllegalArgumentException when configured to allow just 1 group and no groups and no projects for a given id")
    void createGroup_noMultipleGroupsConfig_And_noUndoneGroupExists_noProjects_throwsIllegalStateException() {

        //given
        ProjectRepository mockRepository = projectRepositoryReturning();

        //and
        TaskGroupRepository mockGrouupRepository = groupRepositoryReturning(false);

        //and
        TaskConfigurationProperties mockConfig = configurationReturning(true);

        //system under test
        var toTest = new ProjectService(mockRepository, null, mockConfig, null);

        //when
        var exception = catchThrowable(() -> toTest.createGroup(0, LocalDateTime.now()));

        //then+
        assertThat(exception)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id not Found");

    }

    @Test
    @DisplayName("should create a new group from project")
    void createGroup_configurationOK_existingProject_createsAndSavesGroup(){
        //given
        var today = LocalDate.now().atStartOfDay();
        //and
        var project = projectWith("lorem", Set.of(-1, -2));

        //and
        var mockRepository = projectRepositoryReturning();
        when(mockRepository.findById(anyInt()))
                .thenReturn(Optional.of(project));

        //and
        InMemoryGroupRepository inMemoryGroupRepo = inMemoryGroupRepository();

        var serviceWithInMemoryRepo = dummyGroupService(inMemoryGroupRepo);

        int countBeforeCall = inMemoryGroupRepo.count();

        //and
        TaskConfigurationProperties mockConfig = configurationReturning(true);

        //system under test
        var toTest = new ProjectService(mockRepository, inMemoryGroupRepo, mockConfig, serviceWithInMemoryRepo);

        //when
        GroupReadModel result = toTest.createGroup(1, today);
        //then
        assertThat(countBeforeCall + 1)
                .isEqualTo(inMemoryGroupRepo.count());


        assertThat(result.getDescription())
                .isEqualTo("lorem");

        assertThat(result.getDeadline())
                .isEqualTo(today.minusDays(1)) ;

        assertThat(result.getTasks())
                .allMatch(task -> task.getDescription().equals("test"));

        assertThat(countBeforeCall + 1)
                .isEqualTo(inMemoryGroupRepo.count());
    }

    private static TaskGroupService dummyGroupService(InMemoryGroupRepository inMemoryGroup) {
        return new TaskGroupService(inMemoryGroup, null);
    }
    private Project projectWith(String projectDescription, Set<Integer> daysToDeadline){
        Set<ProjectSteps> steps = daysToDeadline.stream()
                .map(days -> {
                    var step = mock(ProjectSteps.class);
                    when(step.getDescription()).thenReturn("test");
                    when(step.getDaysToDeadline()).thenReturn(days);
                    return step;
                })
                .collect(Collectors.toSet());
        var result = mock(Project.class);
        when(result.getDescription()).thenReturn(projectDescription);
        when(result.getSteps()).thenReturn(steps);
        return result;
    }

    private static ProjectRepository projectRepositoryReturning() {
        var mockRepository = mock(ProjectRepository.class);
        when(mockRepository.findById(anyInt())).thenReturn(Optional.empty());
        return mockRepository;
    }

    private static TaskGroupRepository groupRepositoryReturning(boolean result) {
        var mockGroupRepository = mock(TaskGroupRepository.class);
        when(mockGroupRepository.existsByDoneIsFalseAndProject_Id(anyInt())).thenReturn(result);
        return mockGroupRepository;
    }

    private static TaskConfigurationProperties configurationReturning(final boolean result) {
        var mockTemplate = mock(TaskConfigurationProperties.Template.class);
        when(mockTemplate.isAllowMultipleTasks()).thenReturn(result);

        var mockConfig = mock(TaskConfigurationProperties.class);
        when(mockConfig.getTemplate()).thenReturn(mockTemplate);
        return mockConfig;
    }
    private InMemoryGroupRepository inMemoryGroupRepository(){
        return new InMemoryGroupRepository();
    }

    private static class InMemoryGroupRepository implements TaskGroupRepository{
        private int index = 0;
        private Map<Integer, TaskGroup> map = new HashMap<>();

        public int count(){
            return map.values().size();
        }

        @Override
        public List<TaskGroup> findAll() {
            return new ArrayList<>(map.values());
        }

        @Override
        public Optional<TaskGroup> findById(Integer id) {
            return Optional.ofNullable(map.get(id));
        }

        @Override
        public TaskGroup save(TaskGroup entity) {
            if(entity.getId() == 0){
                try {
                    var field = TaskGroup.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, ++index);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            map.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public boolean existsByDoneIsFalseAndProject_Id(Integer projectId) {
            return map.values().stream()
                    .filter(group -> !group.isDone())
                    .anyMatch(group -> group.getProject() != null && group.getProject().getId() == projectId);
        }

        @Override
        public boolean existsByDescription(String description) {
            return map.values().stream()
                    .anyMatch(group -> !group.getDescription().equals(description));
        }
    }
}