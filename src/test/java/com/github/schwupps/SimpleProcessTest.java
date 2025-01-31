package com.github.schwupps;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.inspections.InspectionUtility;
import io.camunda.zeebe.process.test.inspections.model.InspectedProcessInstance;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.camunda.zeebe.spring.test.ZeebeTestThreadSupport.waitForProcessInstanceCompleted;
import static io.camunda.zeebe.spring.test.ZeebeTestThreadSupport.waitForProcessInstanceHasPassedElement;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SimpleProcessTest.class)
@ZeebeSpringTest
public class SimpleProcessTest {

    @Autowired
    private ZeebeTestEngine engine;
    @Autowired
    private ZeebeClient client;

    private List<String> calledJobTypes;

    @BeforeEach
    void setUp() {
        calledJobTypes = new ArrayList<>();
    }

    @Test
    void process() {
        DeploymentEvent deploymentEvent = client.newDeployResourceCommand()
                .addResourceFromClasspath("hello.bpmn")
                .addResourceFromClasspath("hello.dmn")
                .send()
                .join();
        BpmnAssert.assertThat(deploymentEvent)
                .containsProcessesByResourceName("hello.bpmn");
        ProcessInstanceEvent processInstanceEvent = client.newCreateInstanceCommand()
                .bpmnProcessId("TestProcess")
                .latestVersion()
                .variables(Map.of(
                        "decisionInput", "value1",
                        "boolVar", Boolean.TRUE,
                        "numberVar", Math.PI
                ))
                .send()
                .join();

        waitForProcessInstanceHasPassedElement(processInstanceEvent, "DecisionTask");
        waitForProcessInstanceHasPassedElement(processInstanceEvent, "ServiceTask");
        waitForProcessInstanceHasPassedElement(processInstanceEvent, "SendTask");
        waitForProcessInstanceCompleted(processInstanceEvent);

        InspectedProcessInstance firstProcessInstance = InspectionUtility.findProcessEvents()
                .findFirstProcessInstance()
                .get();

        BpmnAssert.assertThat(firstProcessInstance)
                .isCompleted();
        assertThat(calledJobTypes).containsExactly(
                "WORKER_1", "WORKER_2"
        );
    }

    @JobWorker(type = "WORKER_1",
            name = "1",
            autoComplete = false)
    public void worker1(JobClient client, ActivatedJob job) {
        calledJobTypes.add("WORKER_1");

        client.newCompleteCommand(job)
                .variable("test_var", true)
                .send()
                .join();
    }

    @JobWorker(type = "WORKER_2",
            name = "2")
    public void worker2() {
        calledJobTypes.add("WORKER_2");
    }

    @JobWorker(type = "WORKER_3",
            name = "3")
    public void worker3() {
        calledJobTypes.add("WORKER_3");
    }
}
