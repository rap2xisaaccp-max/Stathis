package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.cit.stathis.task.entity.ExerciseDemonstration;
import jakarta.persistence.OptimisticLockException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.StaleObjectStateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Spring Data persists a null id and merges a non-null id. A new demonstration must leave the
 * UUID null, the same way evidence does. Assigning it first makes merge look for a row that
 * was never inserted.
 */
class ExerciseDemonstrationMetadataPersistenceTest {

  private static SessionFactory sessions;

  @BeforeAll
  static void start() {
    StandardServiceRegistry registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
            .applySetting("hibernate.connection.url", "jdbc:h2:mem:demo-meta;DB_CLOSE_DELAY=-1")
            .applySetting("hibernate.connection.username", "sa")
            .applySetting("hibernate.connection.password", "")
            .applySetting("hibernate.hbm2ddl.auto", "create-drop")
            .applySetting("jakarta.persistence.validation.mode", "none")
            .build();
    try {
      sessions =
          new MetadataSources(registry)
              .addAnnotatedClass(ExerciseDemonstration.class)
              .buildMetadata()
              .buildSessionFactory();
    } catch (RuntimeException ex) {
      StandardServiceRegistryBuilder.destroy(registry);
      throw ex;
    }
  }

  @AfterAll
  static void stop() {
    if (sessions != null) {
      sessions.close();
    }
  }

  @Test
  void nullIdPersistsLikeEvidence() {
    ExerciseDemonstration row = row("DEMO-NULL-ID");
    row.setTaskId("TASK-NULL");
    saveLikeSpringData(row);
    assertNotNull(row.getId());
  }

  @Test
  void assignedIdOnANewRowFailsBeforeInsert() {
    ExerciseDemonstration row = row("DEMO-ASSIGNED-ID");
    row.setTaskId("TASK-ASSIGNED");
    row.setId(UUID.randomUUID());
    OptimisticLockException ex =
        assertThrows(OptimisticLockException.class, () -> saveLikeSpringData(row));
    assertInstanceOf(StaleObjectStateException.class, ex.getCause());
  }

  @Test
  void existingDetachedRowCanBeReplaced() {
    ExerciseDemonstration created = row("DEMO-REPLACE");
    created.setTaskId("TASK-REPLACE");
    saveLikeSpringData(created);
    UUID id = created.getId();
    created.setOriginalFilename("replaced.mp4");
    saveLikeSpringData(created);
    try (Session session = sessions.openSession()) {
      ExerciseDemonstration loaded = session.find(ExerciseDemonstration.class, id);
      assertEquals("replaced.mp4", loaded.getOriginalFilename());
    }
  }

  private static void saveLikeSpringData(ExerciseDemonstration row) {
    try (Session session = sessions.openSession()) {
      session.beginTransaction();
      if (row.getId() == null) {
        session.persist(row);
      } else {
        session.merge(row);
      }
      session.getTransaction().commit();
    }
  }

  private static ExerciseDemonstration row(String physicalId) {
    ExerciseDemonstration row = new ExerciseDemonstration();
    row.setPhysicalId(physicalId);
    row.setTaskId("TASK-A");
    row.setExerciseTemplateId("EXERCISE-PUSH");
    row.setUploadedBy("TCH00000001");
    row.setOriginalFilename("push.mp4");
    row.setStorageKey("demos/TASK-A/EXERCISE-PUSH/" + physicalId + ".mp4");
    row.setContentType("video/mp4");
    row.setByteSize(12L);
    row.setCreatedAt(OffsetDateTime.now());
    return row;
  }

}
