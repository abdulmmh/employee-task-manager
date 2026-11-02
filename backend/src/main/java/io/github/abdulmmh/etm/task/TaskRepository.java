package io.github.abdulmmh.etm.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import io.github.abdulmmh.etm.user.Role;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {






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
