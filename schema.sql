-- Employee & Task Management System
-- MySQL 8.x; execute manually with a schema-administration account.
-- Initial setup only: taskdb must not already contain users/tasks tables.
-- Prepare the demo seed section before importing this file.
-- DDL commits independently in MySQL. Stop on the first execution error.

-- 1. Application database. Existing database credentials are not part of this file.
CREATE DATABASE IF NOT EXISTS `taskdb` CHARACTER SET utf8mb4;
USE `taskdb`;

-- 2. Application users and employee-to-manager relationship.
CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `full_name` VARCHAR(100) NOT NULL,
    `email` VARCHAR(150) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `role` ENUM('ADMIN', 'MANAGER', 'EMPLOYEE') NOT NULL,
    `manager_id` BIGINT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (`id`),
    CONSTRAINT `uk_users_email` UNIQUE (`email`),
    KEY `idx_users_manager_active` (`manager_id`, `active`),
    KEY `idx_users_role_active` (`role`, `active`),

    CONSTRAINT `fk_users_manager`
        FOREIGN KEY (`manager_id`) REFERENCES `users` (`id`),
    CONSTRAINT `ck_users_manager_by_role`
        CHECK (
            (`role` = 'EMPLOYEE' AND `manager_id` IS NOT NULL)
            OR
            (`role` IN ('ADMIN', 'MANAGER') AND `manager_id` IS NULL)
        )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The FK verifies the referenced user exists. The service must also verify that
-- this user is an active MANAGER. A CHECK cannot inspect another user's row.
-- Default FK actions restrict deletion of referenced records; no cascade deletes.
-- Application user deletion sets active=false to retain task history.

-- 3. Tasks: assigned_to is an employee; assigned_by is the original manager/admin.
CREATE TABLE `tasks` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(200) NOT NULL,
    `description` TEXT NULL,
    `status` ENUM('PENDING', 'IN_PROGRESS', 'DONE') NOT NULL DEFAULT 'PENDING',
    `priority` ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL DEFAULT 'MEDIUM',
    `deadline` DATE NULL,
    `assigned_to` BIGINT NOT NULL,
    `assigned_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    KEY `idx_tasks_assignee_status` (`assigned_to`, `status`),
    KEY `idx_tasks_assigner_status` (`assigned_by`, `status`),
    KEY `idx_tasks_status_priority` (`status`, `priority`),

    CONSTRAINT `fk_tasks_assignee`
        FOREIGN KEY (`assigned_to`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_tasks_assigner`
        FOREIGN KEY (`assigned_by`) REFERENCES `users` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Ownership, role checks, task transitions, and team transfers are service rules.
-- A team transfer requires the employee to have no PENDING/IN_PROGRESS tasks.
-- ADMIN may first reassign unfinished tasks within their original team.
-- Completed tasks retain their original assignment history.

-- 4. Demo users and tasks (BCrypt hashes are prepared locally).
-- BEGIN DEMO SEED DATA
-- Demo data only. Passwords are BCrypt hashes prepared with Spring Security.
-- Demo dataset: 12 users, 24 tasks.
-- The administrative database login and backend connection password are unrelated.
START TRANSACTION;

-- Managers must exist before employees reference them.
INSERT INTO `users` (`id`, `full_name`, `email`, `password`, `role`, `manager_id`)
VALUES
    (1, 'Demo Admin', 'admin@demo.com', '$2a$10$3BCLR2dm1jd3o2xubW3VjuPvEep.BjOjCDi7e1ST8eZD16C1Ig4ha', 'ADMIN', NULL),
    (2, 'Demo Manager', 'manager@demo.com', '$2a$10$Cmh.fRt6rBSvxpiHIXP0UOnIXKr8xdewxoxEmPTLyBkw2njoZoKwq', 'MANAGER', NULL),
    (5, 'Second Manager', 'manager2@demo.com', '$2a$10$w1p7ujLP.YEbSgObxbHArOi0Ag8hZLJo1OTx3Z5YEyvrhuwLfJGiu', 'MANAGER', NULL),
    (7, 'Third Manager', 'manager3@demo.com', '$2a$10$pMhC4Vo2ePT4y7KWIHXvyur0jrXrCFvO6zw1eOz/Poz7C9WtTBOma', 'MANAGER', NULL);

INSERT INTO `users` (`id`, `full_name`, `email`, `password`, `role`, `manager_id`)
VALUES
    (3, 'Employee One', 'emp1@demo.com', '$2a$10$nU1VR1auVJn2flwEOpeOQOKFBRuFUZZwyakVWPE771X9gO/3HyqMe', 'EMPLOYEE', 2),
    (4, 'Employee Two', 'emp2@demo.com', '$2a$10$JfH6KuaDXuntON8kne1fgO9kcnOF5UIZH3CWQygwtj8H35jyoEG62', 'EMPLOYEE', 2),
    (6, 'Employee Three', 'emp3@demo.com', '$2a$10$mTXIuL.vVo77TUUo8DdUE.WnFESq9DrF0RFodDJtdbEO4IhJQ6x/W', 'EMPLOYEE', 5),
    (8, 'Employee Four', 'emp4@demo.com', '$2a$10$/PMbP79.deQ7zhmozrNZj.oXkZa9RdI3jcAQysq8nIznhtG5bnSrC', 'EMPLOYEE', 2),
    (9, 'Employee Five', 'emp5@demo.com', '$2a$10$tdOaHxwqkSZYfJLw0a9y4.yW4/H1FbvuYm.Y6GxjoQqUBwduwGtKG', 'EMPLOYEE', 5),
    (10, 'Employee Six', 'emp6@demo.com', '$2a$10$15bcDQznONSS1H2zAnF5..HhAZKP2FI6FJRTU8o.GfJA8bsIX9Z1K', 'EMPLOYEE', 7),
    (11, 'Employee Seven', 'emp7@demo.com', '$2a$10$nBMiGxXmvWWXe54unqs.X.DjK2mINbUNfKHS2c9KYCha5geH6tzeC', 'EMPLOYEE', 7),
    (12, 'Employee Eight', 'emp8@demo.com', '$2a$10$hcwkRU0/RWC7yLfU0qkgU.ivuCWXZ1PqtxDX9y5H86/t3Bdv6X4Ju', 'EMPLOYEE', 7);

INSERT INTO `tasks`
    (`id`, `title`, `description`, `status`, `priority`, `deadline`, `assigned_to`, `assigned_by`)
VALUES
    (1, 'Prepare weekly summary', 'Summarize the work completed by the team.',
        'PENDING', 'HIGH', DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY), 3, 2),
    (2, 'Review onboarding checklist', 'Review the checklist and identify missing steps.',
        'IN_PROGRESS', 'MEDIUM', DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY), 3, 2),
    (3, 'Update employee directory', 'Verify the team contact information.',
        'DONE', 'LOW', CURRENT_DATE, 4, 2),
    (4, 'Prepare training schedule', 'Draft the next team training schedule.',
        'PENDING', 'HIGH', DATE_ADD(CURRENT_DATE, INTERVAL 4 DAY), 6, 5),
    (5, 'Review training feedback', 'Summarize feedback from the previous session.',
        'DONE', 'MEDIUM', CURRENT_DATE, 6, 5),
    (6, 'Check equipment inventory', 'Verify the list of equipment assigned to the team.',
        'PENDING', 'MEDIUM', DATE_ADD(CURRENT_DATE, INTERVAL 5 DAY), 4, 1),
    (7, 'Prepare leave calendar', 'Collect planned leave dates for the team calendar.',
        'PENDING', 'LOW', DATE_ADD(CURRENT_DATE, INTERVAL 6 DAY), 8, 2),
    (8, 'Review attendance summary', 'Check the weekly attendance entries for missing records.',
        'IN_PROGRESS', 'HIGH', DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY), 4, 2),
    (9, 'Archive monthly records', 'Organize the completed monthly records in the shared archive.',
        'DONE', 'MEDIUM', CURRENT_DATE, 8, 2),
    (10, 'Publish meeting minutes', 'Prepare and share the minutes from the team meeting.',
        'DONE', 'HIGH', CURRENT_DATE, 3, 2),
    (11, 'Update contact list', 'Review the contact list and request any missing details.',
        'PENDING', 'LOW', DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY), 4, 2),
    (12, 'Prepare payroll checklist', 'Collect the checklist items needed for monthly processing.',
        'IN_PROGRESS', 'MEDIUM', DATE_ADD(CURRENT_DATE, INTERVAL 4 DAY), 8, 2),
    (13, 'Review support handover', 'Review open handover items and identify the next steps.',
        'IN_PROGRESS', 'LOW', DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY), 3, 2),
    (14, 'Plan onboarding session', 'Prepare the agenda for the next employee onboarding session.',
        'PENDING', 'MEDIUM', DATE_ADD(CURRENT_DATE, INTERVAL 6 DAY), 8, 2),
    (15, 'Organize training materials', 'Arrange the approved materials for the training session.',
        'DONE', 'HIGH', CURRENT_DATE, 9, 5),
    (16, 'Collect course feedback', 'Collect feedback responses and list the main themes.',
        'PENDING', 'LOW', DATE_ADD(CURRENT_DATE, INTERVAL 5 DAY), 6, 5),
    (17, 'Review workshop outline', 'Check that the workshop outline covers the agreed topics.',
        'IN_PROGRESS', 'HIGH', DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY), 9, 5),
    (18, 'Publish training notes', 'Share the reviewed notes with the training participants.',
        'DONE', 'MEDIUM', CURRENT_DATE, 6, 5),
    (19, 'Prepare equipment checklist', 'List equipment required for the upcoming team session.',
        'PENDING', 'HIGH', DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY), 10, 7),
    (20, 'Review maintenance requests', 'Check the open maintenance requests and record their progress.',
        'IN_PROGRESS', 'MEDIUM', DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY), 11, 7),
    (21, 'Update asset register', 'Record the latest approved equipment assignments.',
        'DONE', 'LOW', CURRENT_DATE, 12, 7),
    (22, 'Check meeting room readiness', 'Verify the room setup and list any missing supplies.',
        'IN_PROGRESS', 'LOW', DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY), 10, 7),
    (23, 'Record equipment handover', 'Document the completed handover of shared equipment.',
        'DONE', 'HIGH', CURRENT_DATE, 11, 7),
    (24, 'Prepare supply request', 'Draft the request for supplies needed by the team.',
        'PENDING', 'MEDIUM', DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY), 12, 7);

COMMIT;
-- END DEMO SEED DATA

-- 5. Post-import summary. Expected after expanded seed preparation: users=12, tasks=24.
SELECT 'users' AS `table_name`, COUNT(*) AS `row_count` FROM `users`
UNION ALL
SELECT 'tasks' AS `table_name`, COUNT(*) AS `row_count` FROM `tasks`;
