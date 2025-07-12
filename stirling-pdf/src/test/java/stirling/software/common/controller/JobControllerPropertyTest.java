package stirling.software.common.controller;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import stirling.software.common.model.job.JobResult;
import stirling.software.common.service.FileStorage;
import stirling.software.common.service.JobQueue;
import stirling.software.common.service.TaskManager;

import jakarta.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.*;

public class JobControllerPropertyTest {

    private static JobController controller;
    private static TaskManager taskManager;
    private static FileStorage fileStorage;
    private static JobQueue jobQueue;
    private static HttpServletRequest request;

    @BeforeContainer
    static void setup() {
        taskManager = mock(TaskManager.class);
        fileStorage = mock(FileStorage.class);
        jobQueue = mock(JobQueue.class);
        request = mock(HttpServletRequest.class);

        controller = new JobController(taskManager, fileStorage, jobQueue, request);
    }

    // example of using custom constraints to test a method in our class ith jqwik
    @Property
    void testGetJobStatus_returns404Or200(@ForAll("validJobIds") String jobId) {
        if (jobId == null || jobId.isEmpty()) return;

        boolean jobExists = Arbitraries.integers().between(0, 1).sample() == 1;
        if (jobExists) {
            JobResult mockResult = new JobResult();
            mockResult.setJobId(jobId);
            when(taskManager.getJobResult(jobId)).thenReturn(mockResult);
        } else {
            when(taskManager.getJobResult(jobId)).thenReturn(null);
        }

        ResponseEntity<?> response = controller.getJobStatus(jobId);

        int status = response.getStatusCodeValue();
        System.out.println("Status: " + status);
        Assertions.assertThat(status).isIn(200, 404);
    }

    @Provide
    Arbitrary<String> validJobIds() {
        return Arbitraries.strings()
            .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
            .ofLength(10);
    }
}
