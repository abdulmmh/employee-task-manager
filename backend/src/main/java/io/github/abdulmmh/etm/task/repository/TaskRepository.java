package io.github.abdulmmh.etm.task.repository;

import io.github.abdulmmh.etm.report.dto.TaskReportRow;
import io.github.abdulmmh.etm.task.entity.Task;
import io.github.abdulmmh.etm.task.enums.TaskStatus;
import io.github.abdulmmh.etm.user.enums.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @Query("""
        select new io.github.abdulmmh.etm.report.dto.TaskReportRow(
            t.id, t.title, employee.fullName, assigner.fullName, t.status, t.priority, t.deadline)
        from Task t join t.assignedTo employee join t.assignedBy assigner order by t.id
        """)
    List<TaskReportRow> findAllReportRows();

    @Query("""
        select new io.github.abdulmmh.etm.report.dto.TaskReportRow(
            t.id, t.title, employee.fullName, assigner.fullName, t.status, t.priority, t.deadline)
        from Task t join t.assignedTo employee join t.assignedBy assigner
        where employee.manager.id = :managerId and employee.role = :employeeRole order by t.id
        """)
    List<TaskReportRow> findTeamReportRows(@Param("managerId") long managerId,
                                         @Param("employeeRole") Role employeeRole);

    @Query("select t.status as status, count(t) as total from Task t group by t.status")
    List<TaskStatusCount> countAllByStatus();

    @Query("select t.status as status, count(t) as total from Task t where t.assignedBy.id = :id group by t.status")
    List<TaskStatusCount> countByAssignerStatus(@Param("id") long id);

    @Query("select t.status as status, count(t) as total from Task t where t.assignedTo.id = :id group by t.status")
    List<TaskStatusCount> countByAssigneeStatus(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") long id);

    boolean existsByAssignedToIdAndStatusNot(long userId, TaskStatus status);

    @Query("""
        select case when count(t) > 0 then true else false end from Task t
        where (t.assignedTo.id = :userId or t.assignedBy.id = :userId)
          and t.status <> :completed
        """)
    boolean existsUnfinishedForUser(@Param("userId") long userId,
                                   @Param("completed") TaskStatus completed);
}
