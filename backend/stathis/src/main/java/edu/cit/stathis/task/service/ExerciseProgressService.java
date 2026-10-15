package edu.cit.stathis.task.service;

import edu.cit.stathis.auth.entity.UserProfile;
import edu.cit.stathis.auth.repository.UserProfileRepository;
import edu.cit.stathis.auth.service.PhysicalIdService;
import edu.cit.stathis.task.dto.ExerciseProgressDTO;
import edu.cit.stathis.task.repository.ScoreRepository;
import edu.cit.stathis.task.entity.Score;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ExerciseProgressService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PhysicalIdService physicalIdService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ScoreRepository scoreRepository;

    public void publishProgress(ExerciseProgressDTO progress) {
        if (progress.getStudentId() == null || progress.getStudentId().isBlank()) {
            progress.setStudentId(physicalIdService.getCurrentUserPhysicalId());
        }
        if (progress.getStudentName() == null || progress.getStudentName().isBlank()) {
            progress.setStudentName(resolveStudentName(progress.getStudentId()));
        }
        if (progress.getTimestamp() == null) {
            progress.setTimestamp(OffsetDateTime.now().toString());
        }
        if (progress.getTotalCaloriesBurned() == null
                && progress.getTaskId() != null
                && progress.getExerciseTemplateId() != null) {
            scoreRepository
                    .findExerciseScore(
                            progress.getStudentId(),
                            progress.getTaskId(),
                            progress.getExerciseTemplateId())
                    .map(Score::getCaloriesBurned)
                    .ifPresent(progress::setTotalCaloriesBurned);
        }

        if (progress.getClassroomId() != null && !progress.getClassroomId().isBlank()) {
            String destination = "/topic/classroom/" + progress.getClassroomId() + "/exercise-progress";
            messagingTemplate.convertAndSend(destination, progress);
        }
        messagingTemplate.convertAndSend("/topic/exercise-progress", progress);
    }

    private String resolveStudentName(String studentId) {
        return userProfileRepository
                .findByUser_PhysicalId(studentId)
                .map(profile -> {
                    String first = profile.getFirstName() != null ? profile.getFirstName() : "";
                    String last = profile.getLastName() != null ? profile.getLastName() : "";
                    return (first + " " + last).trim();
                })
                .filter(name -> !name.isBlank())
                .orElse(studentId);
    }
}
