package com.saproduction.command.eve.evaluation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.eve.*;
import com.saproduction.command.eve.cognitive.*;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskRepository;
import java.io.File;
import java.io.FileWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Eve100QuestionEvaluationTest {

    private LocalQwenModelProvider qwenProvider;
    private EveTemporalReasoningService temporalService;
    private EveCognitiveToolRegistry toolRegistry;
    private EveCognitiveRuntime cognitiveRuntime;

    private ProductionRepository productionRepo;
    private ProductionMemberRepository memberRepo;
    private WorkTaskRepository workTaskRepo;
    private EmployeeRepository employeeRepo;
    private EmployeeService employeeService;
    private FinanceReadService financeReadService;
    private EveRetrievalService retrievalService;

    private UUID sessionId;
    
    // Data lists
    private List<Employee> allEmployees = new ArrayList<>();
    private List<Production> allProductions = new ArrayList<>();

    @BeforeAll
    void setUpAll() {
        System.out.println("=================================================================");
        System.out.println("STARTING 100 QUESTION EVALUATION SERVER (8090)");
        System.out.println("=================================================================");

        File model = new File("eve/models/Qwen3-4B-Thinking-2507.Q4_K_M.gguf");
        File server = new File("eve/runtime/llama-server/llama-server.exe");
        if (!model.exists()) {
            model = new File("../../eve/models/Qwen3-4B-Thinking-2507.Q4_K_M.gguf");
            server = new File("../../eve/runtime/llama-server/llama-server.exe");
        }

        qwenProvider = new LocalQwenModelProvider(
            model.getPath(),
            server.getPath(),
            "http://127.0.0.1:8090",
            8090,
            6,
            4096,
            0,
            1024,
            "LOCAL_QWEN_THINKING");

        qwenProvider.init();

        ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");
        Instant anchor = LocalDate.of(2026, 10, 1).atStartOfDay(kolkataZone).toInstant();
        Clock fixedClock = Clock.fixed(anchor, kolkataZone);
        temporalService = new EveTemporalReasoningService(kolkataZone, fixedClock);

        productionRepo = mock(ProductionRepository.class);
        memberRepo = mock(ProductionMemberRepository.class);
        workTaskRepo = mock(WorkTaskRepository.class);
        employeeRepo = mock(EmployeeRepository.class);
        employeeService = mock(EmployeeService.class);
        financeReadService = mock(FinanceReadService.class);
        retrievalService = mock(EveRetrievalService.class);

        toolRegistry = new EveCognitiveToolRegistry(
            productionRepo,
            memberRepo,
            workTaskRepo,
            employeeRepo,
            employeeService,
            financeReadService,
            null,
            retrievalService,
            temporalService);

        cognitiveRuntime = new EveCognitiveRuntime(
            toolRegistry,
            temporalService,
            retrievalService,
            productionRepo,
            memberRepo,
            workTaskRepo,
            employeeRepo);
            
        seedData();
    }

    void seedData() {
        sessionId = UUID.randomUUID();
        // Employees

        Employee e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313 = new Employee();
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id = UUID.fromString("213b479d-d3f4-42bf-a5b1-f0e7f46f0313");
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.employeeCode = "EMP-001";
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.firstName = "Aarav";
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.lastName = "Mehta";
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.displayName = "Aarav Mehta";
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.roleTitle = "Production Manager";
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.department = "Production";
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.status = Employee.Status.ACTIVE;
        e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.baseSalaryMinor = 65000L * 100L;
        allEmployees.add(e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313);
        when(employeeRepo.findById(e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(Optional.of(e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313));

        Employee e_3020eff2_fa63_411f_8225_8826b4e16b0e = new Employee();
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.id = UUID.fromString("3020eff2-fa63-411f-8225-8826b4e16b0e");
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.employeeCode = "EMP-002";
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.firstName = "Kabir";
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.lastName = "Khan";
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.displayName = "Kabir Khan";
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.roleTitle = "Production Coordinator";
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.department = "Production";
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.status = Employee.Status.ACTIVE;
        e_3020eff2_fa63_411f_8225_8826b4e16b0e.baseSalaryMinor = 42000L * 100L;
        allEmployees.add(e_3020eff2_fa63_411f_8225_8826b4e16b0e);
        when(employeeRepo.findById(e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(Optional.of(e_3020eff2_fa63_411f_8225_8826b4e16b0e));

        Employee e_11c6c16d_8800_48e7_828a_4955e3f470a9 = new Employee();
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.id = UUID.fromString("11c6c16d-8800-48e7-828a-4955e3f470a9");
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.employeeCode = "EMP-003";
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.firstName = "Rohan";
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.lastName = "Sharma";
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.displayName = "Rohan Sharma";
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.roleTitle = "Senior Camera Operator";
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.department = "Camera";
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.status = Employee.Status.ACTIVE;
        e_11c6c16d_8800_48e7_828a_4955e3f470a9.baseSalaryMinor = 58000L * 100L;
        allEmployees.add(e_11c6c16d_8800_48e7_828a_4955e3f470a9);
        when(employeeRepo.findById(e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(Optional.of(e_11c6c16d_8800_48e7_828a_4955e3f470a9));

        Employee e_419d1b36_a304_4048_9388_6cf8146192e0 = new Employee();
        e_419d1b36_a304_4048_9388_6cf8146192e0.id = UUID.fromString("419d1b36-a304-4048-9388-6cf8146192e0");
        e_419d1b36_a304_4048_9388_6cf8146192e0.employeeCode = "EMP-004";
        e_419d1b36_a304_4048_9388_6cf8146192e0.firstName = "Aisha";
        e_419d1b36_a304_4048_9388_6cf8146192e0.lastName = "Verma";
        e_419d1b36_a304_4048_9388_6cf8146192e0.displayName = "Aisha Verma";
        e_419d1b36_a304_4048_9388_6cf8146192e0.roleTitle = "Camera Operator";
        e_419d1b36_a304_4048_9388_6cf8146192e0.department = "Camera";
        e_419d1b36_a304_4048_9388_6cf8146192e0.status = Employee.Status.ACTIVE;
        e_419d1b36_a304_4048_9388_6cf8146192e0.baseSalaryMinor = 40000L * 100L;
        allEmployees.add(e_419d1b36_a304_4048_9388_6cf8146192e0);
        when(employeeRepo.findById(e_419d1b36_a304_4048_9388_6cf8146192e0.id)).thenReturn(Optional.of(e_419d1b36_a304_4048_9388_6cf8146192e0));

        Employee e_8b770795_44fb_48a8_8b7f_3ad190fa4039 = new Employee();
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.id = UUID.fromString("8b770795-44fb-48a8-8b7f-3ad190fa4039");
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.employeeCode = "EMP-005";
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.firstName = "Sameer";
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.lastName = "Kapoor";
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.displayName = "Sameer Kapoor";
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.roleTitle = "Assistant Camera Operator";
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.department = "Camera";
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.status = Employee.Status.ACTIVE;
        e_8b770795_44fb_48a8_8b7f_3ad190fa4039.baseSalaryMinor = 32000L * 100L;
        allEmployees.add(e_8b770795_44fb_48a8_8b7f_3ad190fa4039);
        when(employeeRepo.findById(e_8b770795_44fb_48a8_8b7f_3ad190fa4039.id)).thenReturn(Optional.of(e_8b770795_44fb_48a8_8b7f_3ad190fa4039));

        Employee e_7cea4b78_21a7_4ee5_bacf_30914d8a2606 = new Employee();
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id = UUID.fromString("7cea4b78-21a7-4ee5-bacf-30914d8a2606");
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.employeeCode = "EMP-006";
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.firstName = "Zoya";
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.lastName = "Siddiqui";
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.displayName = "Zoya Siddiqui";
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.roleTitle = "Sound Engineer";
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.department = "Sound";
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.status = Employee.Status.ACTIVE;
        e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.baseSalaryMinor = 52000L * 100L;
        allEmployees.add(e_7cea4b78_21a7_4ee5_bacf_30914d8a2606);
        when(employeeRepo.findById(e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(Optional.of(e_7cea4b78_21a7_4ee5_bacf_30914d8a2606));

        Employee e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68 = new Employee();
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.id = UUID.fromString("bf8e1a3d-5695-4af1-a1f8-6bb76cd20d68");
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.employeeCode = "EMP-007";
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.firstName = "Aditya";
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.lastName = "Rao";
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.displayName = "Aditya Rao";
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.roleTitle = "Sound Assistant";
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.department = "Sound";
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.status = Employee.Status.ACTIVE;
        e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.baseSalaryMinor = 30000L * 100L;
        allEmployees.add(e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68);
        when(employeeRepo.findById(e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.id)).thenReturn(Optional.of(e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68));

        Employee e_a3a90e44_ddd3_4080_9806_56d71edc53ee = new Employee();
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id = UUID.fromString("a3a90e44-ddd3-4080-9806-56d71edc53ee");
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.employeeCode = "EMP-008";
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.firstName = "Danish";
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.lastName = "Ali";
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.displayName = "Danish Ali";
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.roleTitle = "Chief Lighting Technician";
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.department = "Lighting";
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.status = Employee.Status.ACTIVE;
        e_a3a90e44_ddd3_4080_9806_56d71edc53ee.baseSalaryMinor = 55000L * 100L;
        allEmployees.add(e_a3a90e44_ddd3_4080_9806_56d71edc53ee);
        when(employeeRepo.findById(e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id)).thenReturn(Optional.of(e_a3a90e44_ddd3_4080_9806_56d71edc53ee));

        Employee e_52116d05_09c6_4880_a3e3_fac7c5d105e1 = new Employee();
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id = UUID.fromString("52116d05-09c6-4880-a3e3-fac7c5d105e1");
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.employeeCode = "EMP-009";
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.firstName = "Meera";
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.lastName = "Iyer";
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.displayName = "Meera Iyer";
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.roleTitle = "Lighting Technician";
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.department = "Lighting";
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.status = Employee.Status.ACTIVE;
        e_52116d05_09c6_4880_a3e3_fac7c5d105e1.baseSalaryMinor = 36000L * 100L;
        allEmployees.add(e_52116d05_09c6_4880_a3e3_fac7c5d105e1);
        when(employeeRepo.findById(e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(Optional.of(e_52116d05_09c6_4880_a3e3_fac7c5d105e1));

        Employee e_3c0c72ea_6741_4258_9180_efe189abdb2f = new Employee();
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.id = UUID.fromString("3c0c72ea-6741-4258-9180-efe189abdb2f");
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.employeeCode = "EMP-010";
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.firstName = "Arjun";
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.lastName = "Malhotra";
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.displayName = "Arjun Malhotra";
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.roleTitle = "Gaffer";
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.department = "Lighting";
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.status = Employee.Status.ACTIVE;
        e_3c0c72ea_6741_4258_9180_efe189abdb2f.baseSalaryMinor = 48000L * 100L;
        allEmployees.add(e_3c0c72ea_6741_4258_9180_efe189abdb2f);
        when(employeeRepo.findById(e_3c0c72ea_6741_4258_9180_efe189abdb2f.id)).thenReturn(Optional.of(e_3c0c72ea_6741_4258_9180_efe189abdb2f));

        Employee e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4 = new Employee();
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.id = UUID.fromString("ac2f6abe-2ed2-4b57-8405-33202ae8ade4");
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.employeeCode = "EMP-011";
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.firstName = "Sana";
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.lastName = "Qureshi";
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.displayName = "Sana Qureshi";
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.roleTitle = "Production Assistant";
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.department = "Production";
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.status = Employee.Status.ACTIVE;
        e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.baseSalaryMinor = 28000L * 100L;
        allEmployees.add(e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4);
        when(employeeRepo.findById(e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.id)).thenReturn(Optional.of(e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4));

        Employee e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1 = new Employee();
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.id = UUID.fromString("6d5ce018-5099-44ba-80c3-d8d1de8df0e1");
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.employeeCode = "EMP-012";
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.firstName = "Vikram";
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.lastName = "Joshi";
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.displayName = "Vikram Joshi";
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.roleTitle = "Production Assistant";
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.department = "Production";
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.status = Employee.Status.ACTIVE;
        e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.baseSalaryMinor = 22000L * 100L;
        allEmployees.add(e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1);
        when(employeeRepo.findById(e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.id)).thenReturn(Optional.of(e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1));

        Employee e_374e916e_082a_4f73_9bbc_929ba3e68767 = new Employee();
        e_374e916e_082a_4f73_9bbc_929ba3e68767.id = UUID.fromString("374e916e-082a-4f73-9bbc-929ba3e68767");
        e_374e916e_082a_4f73_9bbc_929ba3e68767.employeeCode = "EMP-013";
        e_374e916e_082a_4f73_9bbc_929ba3e68767.firstName = "Neha";
        e_374e916e_082a_4f73_9bbc_929ba3e68767.lastName = "Bhatia";
        e_374e916e_082a_4f73_9bbc_929ba3e68767.displayName = "Neha Bhatia";
        e_374e916e_082a_4f73_9bbc_929ba3e68767.roleTitle = "Production Supervisor";
        e_374e916e_082a_4f73_9bbc_929ba3e68767.department = "Production";
        e_374e916e_082a_4f73_9bbc_929ba3e68767.status = Employee.Status.ACTIVE;
        e_374e916e_082a_4f73_9bbc_929ba3e68767.baseSalaryMinor = 50000L * 100L;
        allEmployees.add(e_374e916e_082a_4f73_9bbc_929ba3e68767);
        when(employeeRepo.findById(e_374e916e_082a_4f73_9bbc_929ba3e68767.id)).thenReturn(Optional.of(e_374e916e_082a_4f73_9bbc_929ba3e68767));

        Employee e_6562c77a_ce29_4703_93e2_986cd8128aad = new Employee();
        e_6562c77a_ce29_4703_93e2_986cd8128aad.id = UUID.fromString("6562c77a-ce29-4703-93e2-986cd8128aad");
        e_6562c77a_ce29_4703_93e2_986cd8128aad.employeeCode = "EMP-014";
        e_6562c77a_ce29_4703_93e2_986cd8128aad.firstName = "Farhan";
        e_6562c77a_ce29_4703_93e2_986cd8128aad.lastName = "Sheikh";
        e_6562c77a_ce29_4703_93e2_986cd8128aad.displayName = "Farhan Sheikh";
        e_6562c77a_ce29_4703_93e2_986cd8128aad.roleTitle = "Logistics Coordinator";
        e_6562c77a_ce29_4703_93e2_986cd8128aad.department = "Logistics";
        e_6562c77a_ce29_4703_93e2_986cd8128aad.status = Employee.Status.ACTIVE;
        e_6562c77a_ce29_4703_93e2_986cd8128aad.baseSalaryMinor = 39000L * 100L;
        allEmployees.add(e_6562c77a_ce29_4703_93e2_986cd8128aad);
        when(employeeRepo.findById(e_6562c77a_ce29_4703_93e2_986cd8128aad.id)).thenReturn(Optional.of(e_6562c77a_ce29_4703_93e2_986cd8128aad));

        Employee e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84 = new Employee();
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id = UUID.fromString("2a1c06f1-8e47-49b4-9e2a-7e354c7fbd84");
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.employeeCode = "EMP-015";
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.firstName = "Priya";
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.lastName = "Nair";
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.displayName = "Priya Nair";
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.roleTitle = "Art Director";
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.department = "Art";
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.status = Employee.Status.ACTIVE;
        e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.baseSalaryMinor = 57000L * 100L;
        allEmployees.add(e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84);
        when(employeeRepo.findById(e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(Optional.of(e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84));

        Employee e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd = new Employee();
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.id = UUID.fromString("31b07b64-bed1-42d1-8ac6-61e18b97f0fd");
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.employeeCode = "EMP-016";
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.firstName = "Yash";
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.lastName = "Gupta";
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.displayName = "Yash Gupta";
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.roleTitle = "Set Designer";
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.department = "Art";
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.status = Employee.Status.ACTIVE;
        e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.baseSalaryMinor = 45000L * 100L;
        allEmployees.add(e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd);
        when(employeeRepo.findById(e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.id)).thenReturn(Optional.of(e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd));

        Employee e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5 = new Employee();
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.id = UUID.fromString("2978aa6d-ffd1-4d6a-b47d-e065f6889fa5");
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.employeeCode = "EMP-017";
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.firstName = "Ananya";
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.lastName = "Desai";
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.displayName = "Ananya Desai";
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.roleTitle = "Editor";
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.department = "Post Production";
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.status = Employee.Status.ACTIVE;
        e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.baseSalaryMinor = 60000L * 100L;
        allEmployees.add(e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5);
        when(employeeRepo.findById(e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.id)).thenReturn(Optional.of(e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5));

        Employee e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2 = new Employee();
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.id = UUID.fromString("88b562b0-24fe-4f6a-98e6-52b61c71e3a2");
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.employeeCode = "EMP-018";
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.firstName = "Rehan";
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.lastName = "Siddiqui";
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.displayName = "Rehan Siddiqui";
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.roleTitle = "Assistant Editor";
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.department = "Post Production";
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.status = Employee.Status.ACTIVE;
        e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.baseSalaryMinor = 34000L * 100L;
        allEmployees.add(e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2);
        when(employeeRepo.findById(e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.id)).thenReturn(Optional.of(e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2));

        Employee e_87776c81_5950_4a43_a314_c8a164f06c05 = new Employee();
        e_87776c81_5950_4a43_a314_c8a164f06c05.id = UUID.fromString("87776c81-5950-4a43-a314-c8a164f06c05");
        e_87776c81_5950_4a43_a314_c8a164f06c05.employeeCode = "EMP-019";
        e_87776c81_5950_4a43_a314_c8a164f06c05.firstName = "Ishita";
        e_87776c81_5950_4a43_a314_c8a164f06c05.lastName = "Sen";
        e_87776c81_5950_4a43_a314_c8a164f06c05.displayName = "Ishita Sen";
        e_87776c81_5950_4a43_a314_c8a164f06c05.roleTitle = "DIT";
        e_87776c81_5950_4a43_a314_c8a164f06c05.department = "Post Production";
        e_87776c81_5950_4a43_a314_c8a164f06c05.status = Employee.Status.ACTIVE;
        e_87776c81_5950_4a43_a314_c8a164f06c05.baseSalaryMinor = 44000L * 100L;
        allEmployees.add(e_87776c81_5950_4a43_a314_c8a164f06c05);
        when(employeeRepo.findById(e_87776c81_5950_4a43_a314_c8a164f06c05.id)).thenReturn(Optional.of(e_87776c81_5950_4a43_a314_c8a164f06c05));

        Employee e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69 = new Employee();
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id = UUID.fromString("9969d4d9-d4dd-4b99-8a55-724ae73c9d69");
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.employeeCode = "EMP-020";
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.firstName = "Manav";
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.lastName = "Khanna";
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.displayName = "Manav Khanna";
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.roleTitle = "Photographer";
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.department = "Creative";
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.status = Employee.Status.ACTIVE;
        e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.baseSalaryMinor = 41000L * 100L;
        allEmployees.add(e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69);
        when(employeeRepo.findById(e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(Optional.of(e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69));

        Employee e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95 = new Employee();
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id = UUID.fromString("ee1cbfc7-bd4e-485f-af31-23ebc8fefb95");
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.employeeCode = "EMP-021";
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.firstName = "Sara";
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.lastName = "Khan";
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.displayName = "Sara Khan";
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.roleTitle = "Videographer";
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.department = "Camera";
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.status = Employee.Status.ACTIVE;
        e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.baseSalaryMinor = 43000L * 100L;
        allEmployees.add(e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95);
        when(employeeRepo.findById(e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(Optional.of(e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95));

        Employee e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be = new Employee();
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id = UUID.fromString("ef9abad4-2c31-49f7-b4f1-c6c94a4647be");
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.employeeCode = "EMP-022";
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.firstName = "Harsh";
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.lastName = "Tiwari";
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.displayName = "Harsh Tiwari";
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.roleTitle = "Floor Manager";
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.department = "Production";
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.status = Employee.Status.ACTIVE;
        e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.baseSalaryMinor = 46000L * 100L;
        allEmployees.add(e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be);
        when(employeeRepo.findById(e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(Optional.of(e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be));

        Employee e_0c483d68_03f5_4afc_97af_97b5eeab7494 = new Employee();
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.id = UUID.fromString("0c483d68-03f5-4afc-97af-97b5eeab7494");
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.employeeCode = "EMP-023";
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.firstName = "Alina";
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.lastName = "Roy";
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.displayName = "Alina Roy";
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.roleTitle = "Client Coordinator";
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.department = "Client Services";
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.status = Employee.Status.ACTIVE;
        e_0c483d68_03f5_4afc_97af_97b5eeab7494.baseSalaryMinor = 38000L * 100L;
        allEmployees.add(e_0c483d68_03f5_4afc_97af_97b5eeab7494);
        when(employeeRepo.findById(e_0c483d68_03f5_4afc_97af_97b5eeab7494.id)).thenReturn(Optional.of(e_0c483d68_03f5_4afc_97af_97b5eeab7494));

        Employee e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee = new Employee();
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id = UUID.fromString("68f0f5a7-a293-449c-83ad-3b1c99ab69ee");
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.employeeCode = "EMP-024";
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.firstName = "Imran";
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.lastName = "Hussain";
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.displayName = "Imran Hussain";
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.roleTitle = "Technical Director";
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.department = "Technical";
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.status = Employee.Status.ACTIVE;
        e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.baseSalaryMinor = 68000L * 100L;
        allEmployees.add(e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee);
        when(employeeRepo.findById(e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(Optional.of(e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee));

        Employee e_ebd0c373_13cb_4569_b019_18ed449564ef = new Employee();
        e_ebd0c373_13cb_4569_b019_18ed449564ef.id = UUID.fromString("ebd0c373-13cb-4569-b019-18ed449564ef");
        e_ebd0c373_13cb_4569_b019_18ed449564ef.employeeCode = "EMP-025";
        e_ebd0c373_13cb_4569_b019_18ed449564ef.firstName = "Kavya";
        e_ebd0c373_13cb_4569_b019_18ed449564ef.lastName = "Menon";
        e_ebd0c373_13cb_4569_b019_18ed449564ef.displayName = "Kavya Menon";
        e_ebd0c373_13cb_4569_b019_18ed449564ef.roleTitle = "Equipment Manager";
        e_ebd0c373_13cb_4569_b019_18ed449564ef.department = "Headquarters";
        e_ebd0c373_13cb_4569_b019_18ed449564ef.status = Employee.Status.ACTIVE;
        e_ebd0c373_13cb_4569_b019_18ed449564ef.baseSalaryMinor = 47000L * 100L;
        allEmployees.add(e_ebd0c373_13cb_4569_b019_18ed449564ef);
        when(employeeRepo.findById(e_ebd0c373_13cb_4569_b019_18ed449564ef.id)).thenReturn(Optional.of(e_ebd0c373_13cb_4569_b019_18ed449564ef));

        Employee e_4e995283_bbcc_4010_8a30_3ff757109e94 = new Employee();
        e_4e995283_bbcc_4010_8a30_3ff757109e94.id = UUID.fromString("4e995283-bbcc-4010-8a30-3ff757109e94");
        e_4e995283_bbcc_4010_8a30_3ff757109e94.employeeCode = "EMP-026";
        e_4e995283_bbcc_4010_8a30_3ff757109e94.firstName = "Rahul";
        e_4e995283_bbcc_4010_8a30_3ff757109e94.lastName = "Verma";
        e_4e995283_bbcc_4010_8a30_3ff757109e94.displayName = "Rahul Verma";
        e_4e995283_bbcc_4010_8a30_3ff757109e94.roleTitle = "Production Coordinator";
        e_4e995283_bbcc_4010_8a30_3ff757109e94.department = "Production";
        e_4e995283_bbcc_4010_8a30_3ff757109e94.status = Employee.Status.ACTIVE;
        e_4e995283_bbcc_4010_8a30_3ff757109e94.baseSalaryMinor = 41000L * 100L;
        allEmployees.add(e_4e995283_bbcc_4010_8a30_3ff757109e94);
        when(employeeRepo.findById(e_4e995283_bbcc_4010_8a30_3ff757109e94.id)).thenReturn(Optional.of(e_4e995283_bbcc_4010_8a30_3ff757109e94));

        Employee e_677c4b84_cd2b_4ad4_87d7_5d402432161b = new Employee();
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.id = UUID.fromString("677c4b84-cd2b-4ad4-87d7-5d402432161b");
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.employeeCode = "EMP-027";
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.firstName = "Nikhil";
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.lastName = "Arora";
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.displayName = "Nikhil Arora";
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.roleTitle = "Camera Operator";
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.department = "Camera";
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.status = Employee.Status.ON_LEAVE;
        e_677c4b84_cd2b_4ad4_87d7_5d402432161b.baseSalaryMinor = 39000L * 100L;
        allEmployees.add(e_677c4b84_cd2b_4ad4_87d7_5d402432161b);
        when(employeeRepo.findById(e_677c4b84_cd2b_4ad4_87d7_5d402432161b.id)).thenReturn(Optional.of(e_677c4b84_cd2b_4ad4_87d7_5d402432161b));

        Employee e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280 = new Employee();
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.id = UUID.fromString("6d6d7328-bc92-42b5-82c0-d0aa21ac0280");
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.employeeCode = "EMP-028";
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.firstName = "Fatima";
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.lastName = "Noor";
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.displayName = "Fatima Noor";
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.roleTitle = "Sound Engineer";
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.department = "Sound";
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.status = Employee.Status.ACTIVE;
        e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.baseSalaryMinor = 50000L * 100L;
        allEmployees.add(e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280);
        when(employeeRepo.findById(e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.id)).thenReturn(Optional.of(e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280));

        Employee e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614 = new Employee();
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id = UUID.fromString("e9ce13f2-13bb-4a55-bb68-b09d4b6ca614");
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.employeeCode = "EMP-029";
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.firstName = "Dev";
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.lastName = "Kapoor";
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.displayName = "Dev Kapoor";
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.roleTitle = "Senior Gaffer";
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.department = "Lighting";
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.status = Employee.Status.ACTIVE;
        e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.baseSalaryMinor = 62000L * 100L;
        allEmployees.add(e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614);
        when(employeeRepo.findById(e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(Optional.of(e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614));

        Employee e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c = new Employee();
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.id = UUID.fromString("b05870fa-ab9a-490d-bfc0-2953b5e70f1c");
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.employeeCode = "EMP-030";
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.firstName = "Riya";
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.lastName = "Chawla";
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.displayName = "Riya Chawla";
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.roleTitle = "Production Assistant";
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.department = "Production";
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.status = Employee.Status.ACTIVE;
        e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.baseSalaryMinor = 24000L * 100L;
        allEmployees.add(e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c);
        when(employeeRepo.findById(e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.id)).thenReturn(Optional.of(e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c));

        Employee e_55aebc57_4b14_4740_9181_c6a297807ea2 = new Employee();
        e_55aebc57_4b14_4740_9181_c6a297807ea2.id = UUID.fromString("55aebc57-4b14-4740-9181-c6a297807ea2");
        e_55aebc57_4b14_4740_9181_c6a297807ea2.employeeCode = "EMP-031";
        e_55aebc57_4b14_4740_9181_c6a297807ea2.firstName = "Omar";
        e_55aebc57_4b14_4740_9181_c6a297807ea2.lastName = "Khan";
        e_55aebc57_4b14_4740_9181_c6a297807ea2.displayName = "Omar Khan";
        e_55aebc57_4b14_4740_9181_c6a297807ea2.roleTitle = "Logistics Manager";
        e_55aebc57_4b14_4740_9181_c6a297807ea2.department = "Logistics";
        e_55aebc57_4b14_4740_9181_c6a297807ea2.status = Employee.Status.ACTIVE;
        e_55aebc57_4b14_4740_9181_c6a297807ea2.baseSalaryMinor = 54000L * 100L;
        allEmployees.add(e_55aebc57_4b14_4740_9181_c6a297807ea2);
        when(employeeRepo.findById(e_55aebc57_4b14_4740_9181_c6a297807ea2.id)).thenReturn(Optional.of(e_55aebc57_4b14_4740_9181_c6a297807ea2));

        Employee e_489f72ca_cb16_466d_ae5a_6fc258389d8c = new Employee();
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.id = UUID.fromString("489f72ca-cb16-466d-ae5a-6fc258389d8c");
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.employeeCode = "EMP-032";
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.firstName = "Simran";
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.lastName = "Kaur";
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.displayName = "Simran Kaur";
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.roleTitle = "Costume Coordinator";
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.department = "Art";
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.status = Employee.Status.ACTIVE;
        e_489f72ca_cb16_466d_ae5a_6fc258389d8c.baseSalaryMinor = 37000L * 100L;
        allEmployees.add(e_489f72ca_cb16_466d_ae5a_6fc258389d8c);
        when(employeeRepo.findById(e_489f72ca_cb16_466d_ae5a_6fc258389d8c.id)).thenReturn(Optional.of(e_489f72ca_cb16_466d_ae5a_6fc258389d8c));

        Employee e_d11fda00_248e_4d83_acb5_03179f8ab04c = new Employee();
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.id = UUID.fromString("d11fda00-248e-4d83-acb5-03179f8ab04c");
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.employeeCode = "EMP-033";
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.firstName = "Ayush";
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.lastName = "Saxena";
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.displayName = "Ayush Saxena";
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.roleTitle = "Video Engineer";
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.department = "Technical";
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.status = Employee.Status.ACTIVE;
        e_d11fda00_248e_4d83_acb5_03179f8ab04c.baseSalaryMinor = 52000L * 100L;
        allEmployees.add(e_d11fda00_248e_4d83_acb5_03179f8ab04c);
        when(employeeRepo.findById(e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(Optional.of(e_d11fda00_248e_4d83_acb5_03179f8ab04c));

        Employee e_dc328b21_2525_49ac_95d5_c829c395eff4 = new Employee();
        e_dc328b21_2525_49ac_95d5_c829c395eff4.id = UUID.fromString("dc328b21-2525-49ac-95d5-c829c395eff4");
        e_dc328b21_2525_49ac_95d5_c829c395eff4.employeeCode = "EMP-034";
        e_dc328b21_2525_49ac_95d5_c829c395eff4.firstName = "Noor";
        e_dc328b21_2525_49ac_95d5_c829c395eff4.lastName = "Fatima";
        e_dc328b21_2525_49ac_95d5_c829c395eff4.displayName = "Noor Fatima";
        e_dc328b21_2525_49ac_95d5_c829c395eff4.roleTitle = "Runner";
        e_dc328b21_2525_49ac_95d5_c829c395eff4.department = "Production";
        e_dc328b21_2525_49ac_95d5_c829c395eff4.status = Employee.Status.ACTIVE;
        e_dc328b21_2525_49ac_95d5_c829c395eff4.baseSalaryMinor = 20000L * 100L;
        allEmployees.add(e_dc328b21_2525_49ac_95d5_c829c395eff4);
        when(employeeRepo.findById(e_dc328b21_2525_49ac_95d5_c829c395eff4.id)).thenReturn(Optional.of(e_dc328b21_2525_49ac_95d5_c829c395eff4));

        Employee e_e88946e4_27cb_403b_9784_2a807850c9c1 = new Employee();
        e_e88946e4_27cb_403b_9784_2a807850c9c1.id = UUID.fromString("e88946e4-27cb-403b-9784-2a807850c9c1");
        e_e88946e4_27cb_403b_9784_2a807850c9c1.employeeCode = "EMP-035";
        e_e88946e4_27cb_403b_9784_2a807850c9c1.firstName = "Karan";
        e_e88946e4_27cb_403b_9784_2a807850c9c1.lastName = "Oberoi";
        e_e88946e4_27cb_403b_9784_2a807850c9c1.displayName = "Karan Oberoi";
        e_e88946e4_27cb_403b_9784_2a807850c9c1.roleTitle = "Senior Editor";
        e_e88946e4_27cb_403b_9784_2a807850c9c1.department = "Post Production";
        e_e88946e4_27cb_403b_9784_2a807850c9c1.status = Employee.Status.ACTIVE;
        e_e88946e4_27cb_403b_9784_2a807850c9c1.baseSalaryMinor = 65000L * 100L;
        allEmployees.add(e_e88946e4_27cb_403b_9784_2a807850c9c1);
        when(employeeRepo.findById(e_e88946e4_27cb_403b_9784_2a807850c9c1.id)).thenReturn(Optional.of(e_e88946e4_27cb_403b_9784_2a807850c9c1));

        Employee e_a6476cec_f682_4c4f_a856_e83ddeb7b46b = new Employee();
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.id = UUID.fromString("a6476cec-f682-4c4f-a856-e83ddeb7b46b");
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.employeeCode = "EMP-036";
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.firstName = "Pooja";
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.lastName = "Sethi";
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.displayName = "Pooja Sethi";
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.roleTitle = "Makeup & Styling Coordinator";
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.department = "Creative";
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.status = Employee.Status.ACTIVE;
        e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.baseSalaryMinor = 35000L * 100L;
        allEmployees.add(e_a6476cec_f682_4c4f_a856_e83ddeb7b46b);
        when(employeeRepo.findById(e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.id)).thenReturn(Optional.of(e_a6476cec_f682_4c4f_a856_e83ddeb7b46b));

        Employee e_92cad804_b034_4b76_9002_e3df86876889 = new Employee();
        e_92cad804_b034_4b76_9002_e3df86876889.id = UUID.fromString("92cad804-b034-4b76-9002-e3df86876889");
        e_92cad804_b034_4b76_9002_e3df86876889.employeeCode = "EMP-037";
        e_92cad804_b034_4b76_9002_e3df86876889.firstName = "Armaan";
        e_92cad804_b034_4b76_9002_e3df86876889.lastName = "Rizvi";
        e_92cad804_b034_4b76_9002_e3df86876889.displayName = "Armaan Rizvi";
        e_92cad804_b034_4b76_9002_e3df86876889.roleTitle = "Event Coordinator";
        e_92cad804_b034_4b76_9002_e3df86876889.department = "Production";
        e_92cad804_b034_4b76_9002_e3df86876889.status = Employee.Status.ACTIVE;
        e_92cad804_b034_4b76_9002_e3df86876889.baseSalaryMinor = 45000L * 100L;
        allEmployees.add(e_92cad804_b034_4b76_9002_e3df86876889);
        when(employeeRepo.findById(e_92cad804_b034_4b76_9002_e3df86876889.id)).thenReturn(Optional.of(e_92cad804_b034_4b76_9002_e3df86876889));

        Employee e_34ccd1f5_73d0_4781_9d73_87601c79f38d = new Employee();
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.id = UUID.fromString("34ccd1f5-73d0-4781-9d73-87601c79f38d");
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.employeeCode = "EMP-038";
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.firstName = "Tanya";
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.lastName = "Kapoor";
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.displayName = "Tanya Kapoor";
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.roleTitle = "Junior Camera Operator";
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.department = "Camera";
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.status = Employee.Status.ACTIVE;
        e_34ccd1f5_73d0_4781_9d73_87601c79f38d.baseSalaryMinor = 30000L * 100L;
        allEmployees.add(e_34ccd1f5_73d0_4781_9d73_87601c79f38d);
        when(employeeRepo.findById(e_34ccd1f5_73d0_4781_9d73_87601c79f38d.id)).thenReturn(Optional.of(e_34ccd1f5_73d0_4781_9d73_87601c79f38d));

        Employee e_b9736af3_cc70_4160_a98c_ce2aa403d099 = new Employee();
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.id = UUID.fromString("b9736af3-cc70-4160-a98c-ce2aa403d099");
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.employeeCode = "EMP-039";
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.firstName = "Faisal";
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.lastName = "Mirza";
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.displayName = "Faisal Mirza";
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.roleTitle = "Senior Sound Engineer";
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.department = "Sound";
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.status = Employee.Status.ACTIVE;
        e_b9736af3_cc70_4160_a98c_ce2aa403d099.baseSalaryMinor = 63000L * 100L;
        allEmployees.add(e_b9736af3_cc70_4160_a98c_ce2aa403d099);
        when(employeeRepo.findById(e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(Optional.of(e_b9736af3_cc70_4160_a98c_ce2aa403d099));

        Employee e_8b7752e6_d94e_497a_8695_6703580e7c45 = new Employee();
        e_8b7752e6_d94e_497a_8695_6703580e7c45.id = UUID.fromString("8b7752e6-d94e-497a-8695-6703580e7c45");
        e_8b7752e6_d94e_497a_8695_6703580e7c45.employeeCode = "EMP-040";
        e_8b7752e6_d94e_497a_8695_6703580e7c45.firstName = "Diya";
        e_8b7752e6_d94e_497a_8695_6703580e7c45.lastName = "Agarwal";
        e_8b7752e6_d94e_497a_8695_6703580e7c45.displayName = "Diya Agarwal";
        e_8b7752e6_d94e_497a_8695_6703580e7c45.roleTitle = "Content Coordinator";
        e_8b7752e6_d94e_497a_8695_6703580e7c45.department = "Creative";
        e_8b7752e6_d94e_497a_8695_6703580e7c45.status = Employee.Status.ACTIVE;
        e_8b7752e6_d94e_497a_8695_6703580e7c45.baseSalaryMinor = 40000L * 100L;
        allEmployees.add(e_8b7752e6_d94e_497a_8695_6703580e7c45);
        when(employeeRepo.findById(e_8b7752e6_d94e_497a_8695_6703580e7c45.id)).thenReturn(Optional.of(e_8b7752e6_d94e_497a_8695_6703580e7c45));

        Employee e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54 = new Employee();
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.id = UUID.fromString("50e850ab-7d22-4110-a1ba-f8e6a20e9f54");
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.employeeCode = "EMP-041";
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.firstName = "Sameer";
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.lastName = "Khan";
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.displayName = "Sameer Khan";
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.roleTitle = "Stage Manager";
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.department = "Production";
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.status = Employee.Status.ACTIVE;
        e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.baseSalaryMinor = 48000L * 100L;
        allEmployees.add(e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54);
        when(employeeRepo.findById(e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.id)).thenReturn(Optional.of(e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54));

        Employee e_06ea9607_d17f_4bbc_ba19_5900c649c825 = new Employee();
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.id = UUID.fromString("06ea9607-d17f-4bbc-ba19-5900c649c825");
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.employeeCode = "EMP-042";
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.firstName = "Aditi";
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.lastName = "Sharma";
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.displayName = "Aditi Sharma";
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.roleTitle = "Production Assistant";
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.department = "Production";
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.status = Employee.Status.ACTIVE;
        e_06ea9607_d17f_4bbc_ba19_5900c649c825.baseSalaryMinor = 27000L * 100L;
        allEmployees.add(e_06ea9607_d17f_4bbc_ba19_5900c649c825);
        when(employeeRepo.findById(e_06ea9607_d17f_4bbc_ba19_5900c649c825.id)).thenReturn(Optional.of(e_06ea9607_d17f_4bbc_ba19_5900c649c825));

        Employee e_46370709_7ade_498e_bb8d_bda4eb872db5 = new Employee();
        e_46370709_7ade_498e_bb8d_bda4eb872db5.id = UUID.fromString("46370709-7ade-498e-bb8d-bda4eb872db5");
        e_46370709_7ade_498e_bb8d_bda4eb872db5.employeeCode = "EMP-043";
        e_46370709_7ade_498e_bb8d_bda4eb872db5.firstName = "Zain";
        e_46370709_7ade_498e_bb8d_bda4eb872db5.lastName = "Ahmed";
        e_46370709_7ade_498e_bb8d_bda4eb872db5.displayName = "Zain Ahmed";
        e_46370709_7ade_498e_bb8d_bda4eb872db5.roleTitle = "Lighting Designer";
        e_46370709_7ade_498e_bb8d_bda4eb872db5.department = "Lighting";
        e_46370709_7ade_498e_bb8d_bda4eb872db5.status = Employee.Status.ACTIVE;
        e_46370709_7ade_498e_bb8d_bda4eb872db5.baseSalaryMinor = 56000L * 100L;
        allEmployees.add(e_46370709_7ade_498e_bb8d_bda4eb872db5);
        when(employeeRepo.findById(e_46370709_7ade_498e_bb8d_bda4eb872db5.id)).thenReturn(Optional.of(e_46370709_7ade_498e_bb8d_bda4eb872db5));

        Employee e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1 = new Employee();
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.id = UUID.fromString("f5ef4005-7ec8-40cb-9b4c-be86105aa5e1");
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.employeeCode = "EMP-044";
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.firstName = "Muskan";
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.lastName = "Jain";
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.displayName = "Muskan Jain";
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.roleTitle = "Accounts Coordinator";
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.department = "Finance";
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.status = Employee.Status.ACTIVE;
        e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.baseSalaryMinor = 39000L * 100L;
        allEmployees.add(e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1);
        when(employeeRepo.findById(e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.id)).thenReturn(Optional.of(e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1));

        Employee e_c0b394c7_4a08_4a84_b934_8c5923f317f4 = new Employee();
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.id = UUID.fromString("c0b394c7-4a08-4a84-b934-8c5923f317f4");
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.employeeCode = "EMP-045";
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.firstName = "Yuvraj";
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.lastName = "Singh";
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.displayName = "Yuvraj Singh";
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.roleTitle = "Transport Coordinator";
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.department = "Logistics";
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.status = Employee.Status.ACTIVE;
        e_c0b394c7_4a08_4a84_b934_8c5923f317f4.baseSalaryMinor = 36000L * 100L;
        allEmployees.add(e_c0b394c7_4a08_4a84_b934_8c5923f317f4);
        when(employeeRepo.findById(e_c0b394c7_4a08_4a84_b934_8c5923f317f4.id)).thenReturn(Optional.of(e_c0b394c7_4a08_4a84_b934_8c5923f317f4));

        Employee e_254413b9_81d8_440c_9b7f_40f6143ed206 = new Employee();
        e_254413b9_81d8_440c_9b7f_40f6143ed206.id = UUID.fromString("254413b9-81d8-440c-9b7f-40f6143ed206");
        e_254413b9_81d8_440c_9b7f_40f6143ed206.employeeCode = "EMP-046";
        e_254413b9_81d8_440c_9b7f_40f6143ed206.firstName = "Iqra";
        e_254413b9_81d8_440c_9b7f_40f6143ed206.lastName = "Hasan";
        e_254413b9_81d8_440c_9b7f_40f6143ed206.displayName = "Iqra Hasan";
        e_254413b9_81d8_440c_9b7f_40f6143ed206.roleTitle = "Client Relations Executive";
        e_254413b9_81d8_440c_9b7f_40f6143ed206.department = "Client Services";
        e_254413b9_81d8_440c_9b7f_40f6143ed206.status = Employee.Status.ACTIVE;
        e_254413b9_81d8_440c_9b7f_40f6143ed206.baseSalaryMinor = 35000L * 100L;
        allEmployees.add(e_254413b9_81d8_440c_9b7f_40f6143ed206);
        when(employeeRepo.findById(e_254413b9_81d8_440c_9b7f_40f6143ed206.id)).thenReturn(Optional.of(e_254413b9_81d8_440c_9b7f_40f6143ed206));

        Employee e_e1d5967b_b53a_4609_90a2_4078a406ae2b = new Employee();
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id = UUID.fromString("e1d5967b-b53a-4609-90a2-4078a406ae2b");
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.employeeCode = "EMP-047";
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.firstName = "Mohit";
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.lastName = "Bansal";
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.displayName = "Mohit Bansal";
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.roleTitle = "Operations Manager";
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.department = "Operations";
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.status = Employee.Status.ACTIVE;
        e_e1d5967b_b53a_4609_90a2_4078a406ae2b.baseSalaryMinor = 70000L * 100L;
        allEmployees.add(e_e1d5967b_b53a_4609_90a2_4078a406ae2b);
        when(employeeRepo.findById(e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(Optional.of(e_e1d5967b_b53a_4609_90a2_4078a406ae2b));

        Employee e_9b0a12a8_38db_46db_9956_f807d4fd3ca0 = new Employee();
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.id = UUID.fromString("9b0a12a8-38db-46db-9956-f807d4fd3ca0");
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.employeeCode = "EMP-048";
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.firstName = "Hina";
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.lastName = "Ansari";
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.displayName = "Hina Ansari";
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.roleTitle = "Set Decorator";
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.department = "Art";
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.status = Employee.Status.ON_LEAVE;
        e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.baseSalaryMinor = 38000L * 100L;
        allEmployees.add(e_9b0a12a8_38db_46db_9956_f807d4fd3ca0);
        when(employeeRepo.findById(e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.id)).thenReturn(Optional.of(e_9b0a12a8_38db_46db_9956_f807d4fd3ca0));

        Employee e_466339ec_f819_41bd_9ec5_861aadf073a6 = new Employee();
        e_466339ec_f819_41bd_9ec5_861aadf073a6.id = UUID.fromString("466339ec-f819-41bd-9ec5-861aadf073a6");
        e_466339ec_f819_41bd_9ec5_861aadf073a6.employeeCode = "EMP-049";
        e_466339ec_f819_41bd_9ec5_861aadf073a6.firstName = "Varun";
        e_466339ec_f819_41bd_9ec5_861aadf073a6.lastName = "Mehra";
        e_466339ec_f819_41bd_9ec5_861aadf073a6.displayName = "Varun Mehra";
        e_466339ec_f819_41bd_9ec5_861aadf073a6.roleTitle = "Studio Engineer";
        e_466339ec_f819_41bd_9ec5_861aadf073a6.department = "Technical";
        e_466339ec_f819_41bd_9ec5_861aadf073a6.status = Employee.Status.ACTIVE;
        e_466339ec_f819_41bd_9ec5_861aadf073a6.baseSalaryMinor = 51000L * 100L;
        allEmployees.add(e_466339ec_f819_41bd_9ec5_861aadf073a6);
        when(employeeRepo.findById(e_466339ec_f819_41bd_9ec5_861aadf073a6.id)).thenReturn(Optional.of(e_466339ec_f819_41bd_9ec5_861aadf073a6));

        Employee e_79df0d7e_13dc_4744_b10f_b06184694faf = new Employee();
        e_79df0d7e_13dc_4744_b10f_b06184694faf.id = UUID.fromString("79df0d7e-13dc-4744-b10f-b06184694faf");
        e_79df0d7e_13dc_4744_b10f_b06184694faf.employeeCode = "EMP-050";
        e_79df0d7e_13dc_4744_b10f_b06184694faf.firstName = "Elena";
        e_79df0d7e_13dc_4744_b10f_b06184694faf.lastName = "D'Souza";
        e_79df0d7e_13dc_4744_b10f_b06184694faf.displayName = "Elena D'Souza";
        e_79df0d7e_13dc_4744_b10f_b06184694faf.roleTitle = "Executive Producer";
        e_79df0d7e_13dc_4744_b10f_b06184694faf.department = "Production";
        e_79df0d7e_13dc_4744_b10f_b06184694faf.status = Employee.Status.ACTIVE;
        e_79df0d7e_13dc_4744_b10f_b06184694faf.baseSalaryMinor = 75000L * 100L;
        allEmployees.add(e_79df0d7e_13dc_4744_b10f_b06184694faf);
        when(employeeRepo.findById(e_79df0d7e_13dc_4744_b10f_b06184694faf.id)).thenReturn(Optional.of(e_79df0d7e_13dc_4744_b10f_b06184694faf));

        // Employee Resolvers

        EveRetrievalService.Candidate c_213b479d_d3f4_42bf_a5b1_f0e7f46f0313 = new EveRetrievalService.Candidate(
            e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id, "EMPLOYEE", "Aarav Mehta", "EMP-001", "Production Manager");
        when(retrievalService.resolveEmployee("Aarav"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_213b479d_d3f4_42bf_a5b1_f0e7f46f0313, EveRetrievalService.MatchMethod.EXACT_NAME, "Aarav"));

        EveRetrievalService.Candidate c_3020eff2_fa63_411f_8225_8826b4e16b0e = new EveRetrievalService.Candidate(
            e_3020eff2_fa63_411f_8225_8826b4e16b0e.id, "EMPLOYEE", "Kabir Khan", "EMP-002", "Production Coordinator");
        when(retrievalService.resolveEmployee("Kabir"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_3020eff2_fa63_411f_8225_8826b4e16b0e, EveRetrievalService.MatchMethod.EXACT_NAME, "Kabir"));

        EveRetrievalService.Candidate c_11c6c16d_8800_48e7_828a_4955e3f470a9 = new EveRetrievalService.Candidate(
            e_11c6c16d_8800_48e7_828a_4955e3f470a9.id, "EMPLOYEE", "Rohan Sharma", "EMP-003", "Senior Camera Operator");
        when(retrievalService.resolveEmployee("Rohan"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_11c6c16d_8800_48e7_828a_4955e3f470a9, EveRetrievalService.MatchMethod.EXACT_NAME, "Rohan"));

        EveRetrievalService.Candidate c_419d1b36_a304_4048_9388_6cf8146192e0 = new EveRetrievalService.Candidate(
            e_419d1b36_a304_4048_9388_6cf8146192e0.id, "EMPLOYEE", "Aisha Verma", "EMP-004", "Camera Operator");
        when(retrievalService.resolveEmployee("Aisha"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_419d1b36_a304_4048_9388_6cf8146192e0, EveRetrievalService.MatchMethod.EXACT_NAME, "Aisha"));

        EveRetrievalService.Candidate c_8b770795_44fb_48a8_8b7f_3ad190fa4039 = new EveRetrievalService.Candidate(
            e_8b770795_44fb_48a8_8b7f_3ad190fa4039.id, "EMPLOYEE", "Sameer Kapoor", "EMP-005", "Assistant Camera Operator");
        when(retrievalService.resolveEmployee("Sameer"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_8b770795_44fb_48a8_8b7f_3ad190fa4039, EveRetrievalService.MatchMethod.EXACT_NAME, "Sameer"));

        EveRetrievalService.Candidate c_7cea4b78_21a7_4ee5_bacf_30914d8a2606 = new EveRetrievalService.Candidate(
            e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id, "EMPLOYEE", "Zoya Siddiqui", "EMP-006", "Sound Engineer");
        when(retrievalService.resolveEmployee("Zoya"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_7cea4b78_21a7_4ee5_bacf_30914d8a2606, EveRetrievalService.MatchMethod.EXACT_NAME, "Zoya"));

        EveRetrievalService.Candidate c_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68 = new EveRetrievalService.Candidate(
            e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.id, "EMPLOYEE", "Aditya Rao", "EMP-007", "Sound Assistant");
        when(retrievalService.resolveEmployee("Aditya"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68, EveRetrievalService.MatchMethod.EXACT_NAME, "Aditya"));

        EveRetrievalService.Candidate c_a3a90e44_ddd3_4080_9806_56d71edc53ee = new EveRetrievalService.Candidate(
            e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id, "EMPLOYEE", "Danish Ali", "EMP-008", "Chief Lighting Technician");
        when(retrievalService.resolveEmployee("Danish"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_a3a90e44_ddd3_4080_9806_56d71edc53ee, EveRetrievalService.MatchMethod.EXACT_NAME, "Danish"));

        EveRetrievalService.Candidate c_52116d05_09c6_4880_a3e3_fac7c5d105e1 = new EveRetrievalService.Candidate(
            e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id, "EMPLOYEE", "Meera Iyer", "EMP-009", "Lighting Technician");
        when(retrievalService.resolveEmployee("Meera"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_52116d05_09c6_4880_a3e3_fac7c5d105e1, EveRetrievalService.MatchMethod.EXACT_NAME, "Meera"));

        EveRetrievalService.Candidate c_3c0c72ea_6741_4258_9180_efe189abdb2f = new EveRetrievalService.Candidate(
            e_3c0c72ea_6741_4258_9180_efe189abdb2f.id, "EMPLOYEE", "Arjun Malhotra", "EMP-010", "Gaffer");
        when(retrievalService.resolveEmployee("Arjun"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_3c0c72ea_6741_4258_9180_efe189abdb2f, EveRetrievalService.MatchMethod.EXACT_NAME, "Arjun"));

        EveRetrievalService.Candidate c_ac2f6abe_2ed2_4b57_8405_33202ae8ade4 = new EveRetrievalService.Candidate(
            e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.id, "EMPLOYEE", "Sana Qureshi", "EMP-011", "Production Assistant");
        when(retrievalService.resolveEmployee("Sana"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_ac2f6abe_2ed2_4b57_8405_33202ae8ade4, EveRetrievalService.MatchMethod.EXACT_NAME, "Sana"));

        EveRetrievalService.Candidate c_6d5ce018_5099_44ba_80c3_d8d1de8df0e1 = new EveRetrievalService.Candidate(
            e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.id, "EMPLOYEE", "Vikram Joshi", "EMP-012", "Production Assistant");
        when(retrievalService.resolveEmployee("Vikram"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_6d5ce018_5099_44ba_80c3_d8d1de8df0e1, EveRetrievalService.MatchMethod.EXACT_NAME, "Vikram"));

        EveRetrievalService.Candidate c_374e916e_082a_4f73_9bbc_929ba3e68767 = new EveRetrievalService.Candidate(
            e_374e916e_082a_4f73_9bbc_929ba3e68767.id, "EMPLOYEE", "Neha Bhatia", "EMP-013", "Production Supervisor");
        when(retrievalService.resolveEmployee("Neha"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_374e916e_082a_4f73_9bbc_929ba3e68767, EveRetrievalService.MatchMethod.EXACT_NAME, "Neha"));

        EveRetrievalService.Candidate c_6562c77a_ce29_4703_93e2_986cd8128aad = new EveRetrievalService.Candidate(
            e_6562c77a_ce29_4703_93e2_986cd8128aad.id, "EMPLOYEE", "Farhan Sheikh", "EMP-014", "Logistics Coordinator");
        when(retrievalService.resolveEmployee("Farhan"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_6562c77a_ce29_4703_93e2_986cd8128aad, EveRetrievalService.MatchMethod.EXACT_NAME, "Farhan"));

        EveRetrievalService.Candidate c_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84 = new EveRetrievalService.Candidate(
            e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id, "EMPLOYEE", "Priya Nair", "EMP-015", "Art Director");
        when(retrievalService.resolveEmployee("Priya"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84, EveRetrievalService.MatchMethod.EXACT_NAME, "Priya"));

        EveRetrievalService.Candidate c_31b07b64_bed1_42d1_8ac6_61e18b97f0fd = new EveRetrievalService.Candidate(
            e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.id, "EMPLOYEE", "Yash Gupta", "EMP-016", "Set Designer");
        when(retrievalService.resolveEmployee("Yash"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_31b07b64_bed1_42d1_8ac6_61e18b97f0fd, EveRetrievalService.MatchMethod.EXACT_NAME, "Yash"));

        EveRetrievalService.Candidate c_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5 = new EveRetrievalService.Candidate(
            e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.id, "EMPLOYEE", "Ananya Desai", "EMP-017", "Editor");
        when(retrievalService.resolveEmployee("Ananya"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5, EveRetrievalService.MatchMethod.EXACT_NAME, "Ananya"));

        EveRetrievalService.Candidate c_88b562b0_24fe_4f6a_98e6_52b61c71e3a2 = new EveRetrievalService.Candidate(
            e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.id, "EMPLOYEE", "Rehan Siddiqui", "EMP-018", "Assistant Editor");
        when(retrievalService.resolveEmployee("Rehan"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_88b562b0_24fe_4f6a_98e6_52b61c71e3a2, EveRetrievalService.MatchMethod.EXACT_NAME, "Rehan"));

        EveRetrievalService.Candidate c_87776c81_5950_4a43_a314_c8a164f06c05 = new EveRetrievalService.Candidate(
            e_87776c81_5950_4a43_a314_c8a164f06c05.id, "EMPLOYEE", "Ishita Sen", "EMP-019", "DIT");
        when(retrievalService.resolveEmployee("Ishita"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_87776c81_5950_4a43_a314_c8a164f06c05, EveRetrievalService.MatchMethod.EXACT_NAME, "Ishita"));

        EveRetrievalService.Candidate c_9969d4d9_d4dd_4b99_8a55_724ae73c9d69 = new EveRetrievalService.Candidate(
            e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id, "EMPLOYEE", "Manav Khanna", "EMP-020", "Photographer");
        when(retrievalService.resolveEmployee("Manav"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_9969d4d9_d4dd_4b99_8a55_724ae73c9d69, EveRetrievalService.MatchMethod.EXACT_NAME, "Manav"));

        EveRetrievalService.Candidate c_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95 = new EveRetrievalService.Candidate(
            e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id, "EMPLOYEE", "Sara Khan", "EMP-021", "Videographer");
        when(retrievalService.resolveEmployee("Sara"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95, EveRetrievalService.MatchMethod.EXACT_NAME, "Sara"));

        EveRetrievalService.Candidate c_ef9abad4_2c31_49f7_b4f1_c6c94a4647be = new EveRetrievalService.Candidate(
            e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id, "EMPLOYEE", "Harsh Tiwari", "EMP-022", "Floor Manager");
        when(retrievalService.resolveEmployee("Harsh"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_ef9abad4_2c31_49f7_b4f1_c6c94a4647be, EveRetrievalService.MatchMethod.EXACT_NAME, "Harsh"));

        EveRetrievalService.Candidate c_0c483d68_03f5_4afc_97af_97b5eeab7494 = new EveRetrievalService.Candidate(
            e_0c483d68_03f5_4afc_97af_97b5eeab7494.id, "EMPLOYEE", "Alina Roy", "EMP-023", "Client Coordinator");
        when(retrievalService.resolveEmployee("Alina"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_0c483d68_03f5_4afc_97af_97b5eeab7494, EveRetrievalService.MatchMethod.EXACT_NAME, "Alina"));

        EveRetrievalService.Candidate c_68f0f5a7_a293_449c_83ad_3b1c99ab69ee = new EveRetrievalService.Candidate(
            e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id, "EMPLOYEE", "Imran Hussain", "EMP-024", "Technical Director");
        when(retrievalService.resolveEmployee("Imran"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_68f0f5a7_a293_449c_83ad_3b1c99ab69ee, EveRetrievalService.MatchMethod.EXACT_NAME, "Imran"));

        EveRetrievalService.Candidate c_ebd0c373_13cb_4569_b019_18ed449564ef = new EveRetrievalService.Candidate(
            e_ebd0c373_13cb_4569_b019_18ed449564ef.id, "EMPLOYEE", "Kavya Menon", "EMP-025", "Equipment Manager");
        when(retrievalService.resolveEmployee("Kavya"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_ebd0c373_13cb_4569_b019_18ed449564ef, EveRetrievalService.MatchMethod.EXACT_NAME, "Kavya"));

        EveRetrievalService.Candidate c_4e995283_bbcc_4010_8a30_3ff757109e94 = new EveRetrievalService.Candidate(
            e_4e995283_bbcc_4010_8a30_3ff757109e94.id, "EMPLOYEE", "Rahul Verma", "EMP-026", "Production Coordinator");
        when(retrievalService.resolveEmployee("Rahul"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_4e995283_bbcc_4010_8a30_3ff757109e94, EveRetrievalService.MatchMethod.EXACT_NAME, "Rahul"));

        EveRetrievalService.Candidate c_677c4b84_cd2b_4ad4_87d7_5d402432161b = new EveRetrievalService.Candidate(
            e_677c4b84_cd2b_4ad4_87d7_5d402432161b.id, "EMPLOYEE", "Nikhil Arora", "EMP-027", "Camera Operator");
        when(retrievalService.resolveEmployee("Nikhil"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_677c4b84_cd2b_4ad4_87d7_5d402432161b, EveRetrievalService.MatchMethod.EXACT_NAME, "Nikhil"));

        EveRetrievalService.Candidate c_6d6d7328_bc92_42b5_82c0_d0aa21ac0280 = new EveRetrievalService.Candidate(
            e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.id, "EMPLOYEE", "Fatima Noor", "EMP-028", "Sound Engineer");
        when(retrievalService.resolveEmployee("Fatima"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_6d6d7328_bc92_42b5_82c0_d0aa21ac0280, EveRetrievalService.MatchMethod.EXACT_NAME, "Fatima"));

        EveRetrievalService.Candidate c_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614 = new EveRetrievalService.Candidate(
            e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id, "EMPLOYEE", "Dev Kapoor", "EMP-029", "Senior Gaffer");
        when(retrievalService.resolveEmployee("Dev"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614, EveRetrievalService.MatchMethod.EXACT_NAME, "Dev"));

        EveRetrievalService.Candidate c_b05870fa_ab9a_490d_bfc0_2953b5e70f1c = new EveRetrievalService.Candidate(
            e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.id, "EMPLOYEE", "Riya Chawla", "EMP-030", "Production Assistant");
        when(retrievalService.resolveEmployee("Riya"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_b05870fa_ab9a_490d_bfc0_2953b5e70f1c, EveRetrievalService.MatchMethod.EXACT_NAME, "Riya"));

        EveRetrievalService.Candidate c_55aebc57_4b14_4740_9181_c6a297807ea2 = new EveRetrievalService.Candidate(
            e_55aebc57_4b14_4740_9181_c6a297807ea2.id, "EMPLOYEE", "Omar Khan", "EMP-031", "Logistics Manager");
        when(retrievalService.resolveEmployee("Omar"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_55aebc57_4b14_4740_9181_c6a297807ea2, EveRetrievalService.MatchMethod.EXACT_NAME, "Omar"));

        EveRetrievalService.Candidate c_489f72ca_cb16_466d_ae5a_6fc258389d8c = new EveRetrievalService.Candidate(
            e_489f72ca_cb16_466d_ae5a_6fc258389d8c.id, "EMPLOYEE", "Simran Kaur", "EMP-032", "Costume Coordinator");
        when(retrievalService.resolveEmployee("Simran"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_489f72ca_cb16_466d_ae5a_6fc258389d8c, EveRetrievalService.MatchMethod.EXACT_NAME, "Simran"));

        EveRetrievalService.Candidate c_d11fda00_248e_4d83_acb5_03179f8ab04c = new EveRetrievalService.Candidate(
            e_d11fda00_248e_4d83_acb5_03179f8ab04c.id, "EMPLOYEE", "Ayush Saxena", "EMP-033", "Video Engineer");
        when(retrievalService.resolveEmployee("Ayush"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_d11fda00_248e_4d83_acb5_03179f8ab04c, EveRetrievalService.MatchMethod.EXACT_NAME, "Ayush"));

        EveRetrievalService.Candidate c_dc328b21_2525_49ac_95d5_c829c395eff4 = new EveRetrievalService.Candidate(
            e_dc328b21_2525_49ac_95d5_c829c395eff4.id, "EMPLOYEE", "Noor Fatima", "EMP-034", "Runner");
        when(retrievalService.resolveEmployee("Noor"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_dc328b21_2525_49ac_95d5_c829c395eff4, EveRetrievalService.MatchMethod.EXACT_NAME, "Noor"));

        EveRetrievalService.Candidate c_e88946e4_27cb_403b_9784_2a807850c9c1 = new EveRetrievalService.Candidate(
            e_e88946e4_27cb_403b_9784_2a807850c9c1.id, "EMPLOYEE", "Karan Oberoi", "EMP-035", "Senior Editor");
        when(retrievalService.resolveEmployee("Karan"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_e88946e4_27cb_403b_9784_2a807850c9c1, EveRetrievalService.MatchMethod.EXACT_NAME, "Karan"));

        EveRetrievalService.Candidate c_a6476cec_f682_4c4f_a856_e83ddeb7b46b = new EveRetrievalService.Candidate(
            e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.id, "EMPLOYEE", "Pooja Sethi", "EMP-036", "Makeup & Styling Coordinator");
        when(retrievalService.resolveEmployee("Pooja"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_a6476cec_f682_4c4f_a856_e83ddeb7b46b, EveRetrievalService.MatchMethod.EXACT_NAME, "Pooja"));

        EveRetrievalService.Candidate c_92cad804_b034_4b76_9002_e3df86876889 = new EveRetrievalService.Candidate(
            e_92cad804_b034_4b76_9002_e3df86876889.id, "EMPLOYEE", "Armaan Rizvi", "EMP-037", "Event Coordinator");
        when(retrievalService.resolveEmployee("Armaan"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_92cad804_b034_4b76_9002_e3df86876889, EveRetrievalService.MatchMethod.EXACT_NAME, "Armaan"));

        EveRetrievalService.Candidate c_34ccd1f5_73d0_4781_9d73_87601c79f38d = new EveRetrievalService.Candidate(
            e_34ccd1f5_73d0_4781_9d73_87601c79f38d.id, "EMPLOYEE", "Tanya Kapoor", "EMP-038", "Junior Camera Operator");
        when(retrievalService.resolveEmployee("Tanya"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_34ccd1f5_73d0_4781_9d73_87601c79f38d, EveRetrievalService.MatchMethod.EXACT_NAME, "Tanya"));

        EveRetrievalService.Candidate c_b9736af3_cc70_4160_a98c_ce2aa403d099 = new EveRetrievalService.Candidate(
            e_b9736af3_cc70_4160_a98c_ce2aa403d099.id, "EMPLOYEE", "Faisal Mirza", "EMP-039", "Senior Sound Engineer");
        when(retrievalService.resolveEmployee("Faisal"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_b9736af3_cc70_4160_a98c_ce2aa403d099, EveRetrievalService.MatchMethod.EXACT_NAME, "Faisal"));

        EveRetrievalService.Candidate c_8b7752e6_d94e_497a_8695_6703580e7c45 = new EveRetrievalService.Candidate(
            e_8b7752e6_d94e_497a_8695_6703580e7c45.id, "EMPLOYEE", "Diya Agarwal", "EMP-040", "Content Coordinator");
        when(retrievalService.resolveEmployee("Diya"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_8b7752e6_d94e_497a_8695_6703580e7c45, EveRetrievalService.MatchMethod.EXACT_NAME, "Diya"));

        EveRetrievalService.Candidate c_50e850ab_7d22_4110_a1ba_f8e6a20e9f54 = new EveRetrievalService.Candidate(
            e_50e850ab_7d22_4110_a1ba_f8e6a20e9f54.id, "EMPLOYEE", "Sameer Khan", "EMP-041", "Stage Manager");
        when(retrievalService.resolveEmployee("Sameer"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_50e850ab_7d22_4110_a1ba_f8e6a20e9f54, EveRetrievalService.MatchMethod.EXACT_NAME, "Sameer"));

        EveRetrievalService.Candidate c_06ea9607_d17f_4bbc_ba19_5900c649c825 = new EveRetrievalService.Candidate(
            e_06ea9607_d17f_4bbc_ba19_5900c649c825.id, "EMPLOYEE", "Aditi Sharma", "EMP-042", "Production Assistant");
        when(retrievalService.resolveEmployee("Aditi"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_06ea9607_d17f_4bbc_ba19_5900c649c825, EveRetrievalService.MatchMethod.EXACT_NAME, "Aditi"));

        EveRetrievalService.Candidate c_46370709_7ade_498e_bb8d_bda4eb872db5 = new EveRetrievalService.Candidate(
            e_46370709_7ade_498e_bb8d_bda4eb872db5.id, "EMPLOYEE", "Zain Ahmed", "EMP-043", "Lighting Designer");
        when(retrievalService.resolveEmployee("Zain"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_46370709_7ade_498e_bb8d_bda4eb872db5, EveRetrievalService.MatchMethod.EXACT_NAME, "Zain"));

        EveRetrievalService.Candidate c_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1 = new EveRetrievalService.Candidate(
            e_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1.id, "EMPLOYEE", "Muskan Jain", "EMP-044", "Accounts Coordinator");
        when(retrievalService.resolveEmployee("Muskan"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_f5ef4005_7ec8_40cb_9b4c_be86105aa5e1, EveRetrievalService.MatchMethod.EXACT_NAME, "Muskan"));

        EveRetrievalService.Candidate c_c0b394c7_4a08_4a84_b934_8c5923f317f4 = new EveRetrievalService.Candidate(
            e_c0b394c7_4a08_4a84_b934_8c5923f317f4.id, "EMPLOYEE", "Yuvraj Singh", "EMP-045", "Transport Coordinator");
        when(retrievalService.resolveEmployee("Yuvraj"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_c0b394c7_4a08_4a84_b934_8c5923f317f4, EveRetrievalService.MatchMethod.EXACT_NAME, "Yuvraj"));

        EveRetrievalService.Candidate c_254413b9_81d8_440c_9b7f_40f6143ed206 = new EveRetrievalService.Candidate(
            e_254413b9_81d8_440c_9b7f_40f6143ed206.id, "EMPLOYEE", "Iqra Hasan", "EMP-046", "Client Relations Executive");
        when(retrievalService.resolveEmployee("Iqra"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_254413b9_81d8_440c_9b7f_40f6143ed206, EveRetrievalService.MatchMethod.EXACT_NAME, "Iqra"));

        EveRetrievalService.Candidate c_e1d5967b_b53a_4609_90a2_4078a406ae2b = new EveRetrievalService.Candidate(
            e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id, "EMPLOYEE", "Mohit Bansal", "EMP-047", "Operations Manager");
        when(retrievalService.resolveEmployee("Mohit"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_e1d5967b_b53a_4609_90a2_4078a406ae2b, EveRetrievalService.MatchMethod.EXACT_NAME, "Mohit"));

        EveRetrievalService.Candidate c_9b0a12a8_38db_46db_9956_f807d4fd3ca0 = new EveRetrievalService.Candidate(
            e_9b0a12a8_38db_46db_9956_f807d4fd3ca0.id, "EMPLOYEE", "Hina Ansari", "EMP-048", "Set Decorator");
        when(retrievalService.resolveEmployee("Hina"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_9b0a12a8_38db_46db_9956_f807d4fd3ca0, EveRetrievalService.MatchMethod.EXACT_NAME, "Hina"));

        EveRetrievalService.Candidate c_466339ec_f819_41bd_9ec5_861aadf073a6 = new EveRetrievalService.Candidate(
            e_466339ec_f819_41bd_9ec5_861aadf073a6.id, "EMPLOYEE", "Varun Mehra", "EMP-049", "Studio Engineer");
        when(retrievalService.resolveEmployee("Varun"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_466339ec_f819_41bd_9ec5_861aadf073a6, EveRetrievalService.MatchMethod.EXACT_NAME, "Varun"));

        EveRetrievalService.Candidate c_79df0d7e_13dc_4744_b10f_b06184694faf = new EveRetrievalService.Candidate(
            e_79df0d7e_13dc_4744_b10f_b06184694faf.id, "EMPLOYEE", "Elena D'Souza", "EMP-050", "Executive Producer");
        when(retrievalService.resolveEmployee("Elena"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_79df0d7e_13dc_4744_b10f_b06184694faf, EveRetrievalService.MatchMethod.EXACT_NAME, "Elena"));

        when(employeeRepo.findAll()).thenReturn(allEmployees);

        // Productions

        Production p_84a327f4_5eaf_45c0_8dfd_73d1810035f4 = new Production();
        p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id = UUID.fromString("84a327f4-5eaf-45c0-8dfd-73d1810035f4");
        p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.title = "Sharma Wedding";
        p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.clientName = "Sharma Family";
        p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.eventDate = LocalDate.parse("2026-09-30");
        p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.venueName = "Royal Orchid";
        p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.status = Production.Status.PRODUCTION;
        p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.priority = Production.Priority.HIGH;
        p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.description = "Equipment Needed: 2 Cameras, 4 Lights, Audio Kit";
        allProductions.add(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4);
        when(productionRepo.findById(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id)).thenReturn(Optional.of(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4));

        Production p_82376a08_f232_4029_8f19_3ecae088641a = new Production();
        p_82376a08_f232_4029_8f19_3ecae088641a.id = UUID.fromString("82376a08-f232-4029-8f19-3ecae088641a");
        p_82376a08_f232_4029_8f19_3ecae088641a.title = "Sharma Reception";
        p_82376a08_f232_4029_8f19_3ecae088641a.clientName = "Sharma Family";
        p_82376a08_f232_4029_8f19_3ecae088641a.eventDate = LocalDate.parse("2026-10-02");
        p_82376a08_f232_4029_8f19_3ecae088641a.venueName = "Grand Palace Hall";
        p_82376a08_f232_4029_8f19_3ecae088641a.status = Production.Status.PRODUCTION;
        p_82376a08_f232_4029_8f19_3ecae088641a.priority = Production.Priority.HIGH;
        p_82376a08_f232_4029_8f19_3ecae088641a.description = "Equipment Needed: 3 Cameras, 6 Lights, Wireless Mics";
        allProductions.add(p_82376a08_f232_4029_8f19_3ecae088641a);
        when(productionRepo.findById(p_82376a08_f232_4029_8f19_3ecae088641a.id)).thenReturn(Optional.of(p_82376a08_f232_4029_8f19_3ecae088641a));

        Production p_df39bfa7_2eaf_4305_b77f_3980ed946a02 = new Production();
        p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id = UUID.fromString("df39bfa7-2eaf-4305-b77f-3980ed946a02");
        p_df39bfa7_2eaf_4305_b77f_3980ed946a02.title = "Arora Corporate Summit";
        p_df39bfa7_2eaf_4305_b77f_3980ed946a02.clientName = "Arora Technologies";
        p_df39bfa7_2eaf_4305_b77f_3980ed946a02.eventDate = LocalDate.parse("2026-10-05");
        p_df39bfa7_2eaf_4305_b77f_3980ed946a02.venueName = "Convention Centre";
        p_df39bfa7_2eaf_4305_b77f_3980ed946a02.status = Production.Status.PRODUCTION;
        p_df39bfa7_2eaf_4305_b77f_3980ed946a02.priority = Production.Priority.NORMAL;
        p_df39bfa7_2eaf_4305_b77f_3980ed946a02.description = "Equipment Needed: 3 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_df39bfa7_2eaf_4305_b77f_3980ed946a02);
        when(productionRepo.findById(p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id)).thenReturn(Optional.of(p_df39bfa7_2eaf_4305_b77f_3980ed946a02));

        Production p_4cae2b69_a431_46bb_a4b4_de13ddafd83e = new Production();
        p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id = UUID.fromString("4cae2b69-a431-46bb-a4b4-de13ddafd83e");
        p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.title = "Kapoor Product Launch";
        p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.clientName = "Kapoor Electronics";
        p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.eventDate = LocalDate.parse("2026-10-08");
        p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.venueName = "The Leela Ballroom";
        p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.status = Production.Status.PRODUCTION;
        p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.priority = Production.Priority.HIGH;
        p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.description = "Equipment Needed: 4 Lights, 2 Cameras, LED Wall";
        allProductions.add(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e);
        when(productionRepo.findById(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id)).thenReturn(Optional.of(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e));

        Production p_bcdd676b_724e_44d8_bfda_48abc2d9ce21 = new Production();
        p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id = UUID.fromString("bcdd676b-724e-44d8-bfda-48abc2d9ce21");
        p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.title = "Mehta Family Wedding";
        p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.clientName = "Mehta Family";
        p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.eventDate = LocalDate.parse("2026-10-11");
        p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.venueName = "Taj Palace Lawn";
        p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.status = Production.Status.PRODUCTION;
        p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.priority = Production.Priority.NORMAL;
        p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.description = "Equipment Needed: 3 Cameras, 5 Lights, Audio Kit";
        allProductions.add(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21);
        when(productionRepo.findById(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id)).thenReturn(Optional.of(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21));

        Production p_14b741e3_4096_46f4_b6ff_c21d14559654 = new Production();
        p_14b741e3_4096_46f4_b6ff_c21d14559654.id = UUID.fromString("14b741e3-4096-46f4-b6ff-c21d14559654");
        p_14b741e3_4096_46f4_b6ff_c21d14559654.title = "TechNova Annual Meet";
        p_14b741e3_4096_46f4_b6ff_c21d14559654.clientName = "TechNova India";
        p_14b741e3_4096_46f4_b6ff_c21d14559654.eventDate = LocalDate.parse("2026-10-15");
        p_14b741e3_4096_46f4_b6ff_c21d14559654.venueName = "Hyatt Regency";
        p_14b741e3_4096_46f4_b6ff_c21d14559654.status = Production.Status.PRODUCTION;
        p_14b741e3_4096_46f4_b6ff_c21d14559654.priority = Production.Priority.NORMAL;
        p_14b741e3_4096_46f4_b6ff_c21d14559654.description = "Equipment Needed: LED Wall, 3 Cameras, Audio Kit";
        allProductions.add(p_14b741e3_4096_46f4_b6ff_c21d14559654);
        when(productionRepo.findById(p_14b741e3_4096_46f4_b6ff_c21d14559654.id)).thenReturn(Optional.of(p_14b741e3_4096_46f4_b6ff_c21d14559654));

        Production p_f21e84ca_d0fa_4137_ad9e_c438fd37b888 = new Production();
        p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.id = UUID.fromString("f21e84ca-d0fa-4137-ad9e-c438fd37b888");
        p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.title = "Khan Nikah Ceremony";
        p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.clientName = "Khan Family";
        p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.eventDate = LocalDate.parse("2026-10-18");
        p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.venueName = "Pearl Banquet";
        p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.status = Production.Status.PRODUCTION;
        p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.priority = Production.Priority.HIGH;
        p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.description = "Equipment Needed: 2 Cameras, 3 Lights, Audio Kit";
        allProductions.add(p_f21e84ca_d0fa_4137_ad9e_c438fd37b888);
        when(productionRepo.findById(p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.id)).thenReturn(Optional.of(p_f21e84ca_d0fa_4137_ad9e_c438fd37b888));

        Production p_67cf9f86_3641_46ce_ac5a_fd760d4567e5 = new Production();
        p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id = UUID.fromString("67cf9f86-3641-46ce-ac5a-fd760d4567e5");
        p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.title = "GreenEarth Sustainability Expo";
        p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.clientName = "GreenEarth Foundation";
        p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.eventDate = LocalDate.parse("2026-10-21");
        p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.venueName = "Expo Centre";
        p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.status = Production.Status.PRODUCTION;
        p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.priority = Production.Priority.NORMAL;
        p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.description = "Equipment Needed: 4 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5);
        when(productionRepo.findById(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id)).thenReturn(Optional.of(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5));

        Production p_af0f456b_aa71_422c_8ccc_4e54814a65f4 = new Production();
        p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id = UUID.fromString("af0f456b-aa71-422c-8ccc-4e54814a65f4");
        p_af0f456b_aa71_422c_8ccc_4e54814a65f4.title = "Royal Fashion Night";
        p_af0f456b_aa71_422c_8ccc_4e54814a65f4.clientName = "Royal Threads";
        p_af0f456b_aa71_422c_8ccc_4e54814a65f4.eventDate = LocalDate.parse("2026-10-25");
        p_af0f456b_aa71_422c_8ccc_4e54814a65f4.venueName = "Imperial Ballroom";
        p_af0f456b_aa71_422c_8ccc_4e54814a65f4.status = Production.Status.PRODUCTION;
        p_af0f456b_aa71_422c_8ccc_4e54814a65f4.priority = Production.Priority.HIGH;
        p_af0f456b_aa71_422c_8ccc_4e54814a65f4.description = "Equipment Needed: 4 Cameras, 8 Lights, LED Wall";
        allProductions.add(p_af0f456b_aa71_422c_8ccc_4e54814a65f4);
        when(productionRepo.findById(p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id)).thenReturn(Optional.of(p_af0f456b_aa71_422c_8ccc_4e54814a65f4));

        Production p_b9d08d9b_e84f_4f27_a720_3a45db5a3876 = new Production();
        p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.id = UUID.fromString("b9d08d9b-e84f-4f27-a720-3a45db5a3876");
        p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.title = "Kapoor Anniversary";
        p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.clientName = "Kapoor Family";
        p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.eventDate = LocalDate.parse("2026-10-28");
        p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.venueName = "Lakeview Resort";
        p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.status = Production.Status.PRODUCTION;
        p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.priority = Production.Priority.NORMAL;
        p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.description = "Equipment Needed: 2 Cameras, Audio Kit, 3 Lights";
        allProductions.add(p_b9d08d9b_e84f_4f27_a720_3a45db5a3876);
        when(productionRepo.findById(p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.id)).thenReturn(Optional.of(p_b9d08d9b_e84f_4f27_a720_3a45db5a3876));

        Production p_162396c3_000d_43f3_96e0_5ff2942dce0d = new Production();
        p_162396c3_000d_43f3_96e0_5ff2942dce0d.id = UUID.fromString("162396c3-000d-43f3-96e0-5ff2942dce0d");
        p_162396c3_000d_43f3_96e0_5ff2942dce0d.title = "BrightStart Investor Day";
        p_162396c3_000d_43f3_96e0_5ff2942dce0d.clientName = "BrightStart Ventures";
        p_162396c3_000d_43f3_96e0_5ff2942dce0d.eventDate = LocalDate.parse("2026-10-31");
        p_162396c3_000d_43f3_96e0_5ff2942dce0d.venueName = "ITC Grand";
        p_162396c3_000d_43f3_96e0_5ff2942dce0d.status = Production.Status.PRODUCTION;
        p_162396c3_000d_43f3_96e0_5ff2942dce0d.priority = Production.Priority.HIGH;
        p_162396c3_000d_43f3_96e0_5ff2942dce0d.description = "Equipment Needed: 3 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_162396c3_000d_43f3_96e0_5ff2942dce0d);
        when(productionRepo.findById(p_162396c3_000d_43f3_96e0_5ff2942dce0d.id)).thenReturn(Optional.of(p_162396c3_000d_43f3_96e0_5ff2942dce0d));

        Production p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b = new Production();
        p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id = UUID.fromString("470c4be3-4bbb-483b-b9c8-cf35b513cd6b");
        p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.title = "Verma Wedding";
        p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.clientName = "Verma Family";
        p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.eventDate = LocalDate.parse("2026-11-04");
        p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.venueName = "The Grand Hyatt";
        p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.status = Production.Status.PRODUCTION;
        p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.priority = Production.Priority.HIGH;
        p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.description = "Equipment Needed: 4 Cameras, 6 Lights, Audio Kit";
        allProductions.add(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b);
        when(productionRepo.findById(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id)).thenReturn(Optional.of(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b));

        Production p_016c8cd3_5c99_4c48_accb_d950d384963d = new Production();
        p_016c8cd3_5c99_4c48_accb_d950d384963d.id = UUID.fromString("016c8cd3-5c99-4c48-accb-d950d384963d");
        p_016c8cd3_5c99_4c48_accb_d950d384963d.title = "FutureBuild Construction Expo";
        p_016c8cd3_5c99_4c48_accb_d950d384963d.clientName = "FutureBuild India";
        p_016c8cd3_5c99_4c48_accb_d950d384963d.eventDate = LocalDate.parse("2026-11-07");
        p_016c8cd3_5c99_4c48_accb_d950d384963d.venueName = "India Expo Mart";
        p_016c8cd3_5c99_4c48_accb_d950d384963d.status = Production.Status.PRODUCTION;
        p_016c8cd3_5c99_4c48_accb_d950d384963d.priority = Production.Priority.NORMAL;
        p_016c8cd3_5c99_4c48_accb_d950d384963d.description = "Equipment Needed: 4 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_016c8cd3_5c99_4c48_accb_d950d384963d);
        when(productionRepo.findById(p_016c8cd3_5c99_4c48_accb_d950d384963d.id)).thenReturn(Optional.of(p_016c8cd3_5c99_4c48_accb_d950d384963d));

        Production p_947ce209_a660_46fc_8008_c2359d55d334 = new Production();
        p_947ce209_a660_46fc_8008_c2359d55d334.id = UUID.fromString("947ce209-a660-46fc-8008-c2359d55d334");
        p_947ce209_a660_46fc_8008_c2359d55d334.title = "Singh Engagement";
        p_947ce209_a660_46fc_8008_c2359d55d334.clientName = "Singh Family";
        p_947ce209_a660_46fc_8008_c2359d55d334.eventDate = LocalDate.parse("2026-11-10");
        p_947ce209_a660_46fc_8008_c2359d55d334.venueName = "Gardenia Resort";
        p_947ce209_a660_46fc_8008_c2359d55d334.status = Production.Status.PRODUCTION;
        p_947ce209_a660_46fc_8008_c2359d55d334.priority = Production.Priority.NORMAL;
        p_947ce209_a660_46fc_8008_c2359d55d334.description = "Equipment Needed: 2 Cameras, 3 Lights, Audio Kit";
        allProductions.add(p_947ce209_a660_46fc_8008_c2359d55d334);
        when(productionRepo.findById(p_947ce209_a660_46fc_8008_c2359d55d334.id)).thenReturn(Optional.of(p_947ce209_a660_46fc_8008_c2359d55d334));

        Production p_3a52884d_60ba_4f84_9056_8165a12f2998 = new Production();
        p_3a52884d_60ba_4f84_9056_8165a12f2998.id = UUID.fromString("3a52884d-60ba-4f84-9056-8165a12f2998");
        p_3a52884d_60ba_4f84_9056_8165a12f2998.title = "Urban Beats Festival";
        p_3a52884d_60ba_4f84_9056_8165a12f2998.clientName = "Urban Beats Collective";
        p_3a52884d_60ba_4f84_9056_8165a12f2998.eventDate = LocalDate.parse("2026-11-14");
        p_3a52884d_60ba_4f84_9056_8165a12f2998.venueName = "Riverside Grounds";
        p_3a52884d_60ba_4f84_9056_8165a12f2998.status = Production.Status.PRODUCTION;
        p_3a52884d_60ba_4f84_9056_8165a12f2998.priority = Production.Priority.HIGH;
        p_3a52884d_60ba_4f84_9056_8165a12f2998.description = "Equipment Needed: 5 Cameras, 10 Lights, LED Wall, Audio Kit";
        allProductions.add(p_3a52884d_60ba_4f84_9056_8165a12f2998);
        when(productionRepo.findById(p_3a52884d_60ba_4f84_9056_8165a12f2998.id)).thenReturn(Optional.of(p_3a52884d_60ba_4f84_9056_8165a12f2998));

        Production p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe = new Production();
        p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.id = UUID.fromString("c7a9a4c6-9934-4b86-a691-f4b5e54429fe");
        p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.title = "Nova Motors Dealer Meet";
        p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.clientName = "Nova Motors";
        p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.eventDate = LocalDate.parse("2026-11-18");
        p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.venueName = "JW Marriott";
        p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.status = Production.Status.PRODUCTION;
        p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.priority = Production.Priority.NORMAL;
        p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.description = "Equipment Needed: 3 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe);
        when(productionRepo.findById(p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.id)).thenReturn(Optional.of(p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe));

        Production p_af3f50b7_b5de_430a_aba3_e4340328148f = new Production();
        p_af3f50b7_b5de_430a_aba3_e4340328148f.id = UUID.fromString("af3f50b7-b5de-430a-aba3-e4340328148f");
        p_af3f50b7_b5de_430a_aba3_e4340328148f.title = "Malhotra Wedding";
        p_af3f50b7_b5de_430a_aba3_e4340328148f.clientName = "Malhotra Family";
        p_af3f50b7_b5de_430a_aba3_e4340328148f.eventDate = LocalDate.parse("2026-11-21");
        p_af3f50b7_b5de_430a_aba3_e4340328148f.venueName = "Heritage Palace";
        p_af3f50b7_b5de_430a_aba3_e4340328148f.status = Production.Status.PRODUCTION;
        p_af3f50b7_b5de_430a_aba3_e4340328148f.priority = Production.Priority.HIGH;
        p_af3f50b7_b5de_430a_aba3_e4340328148f.description = "Equipment Needed: 4 Cameras, 7 Lights, Audio Kit";
        allProductions.add(p_af3f50b7_b5de_430a_aba3_e4340328148f);
        when(productionRepo.findById(p_af3f50b7_b5de_430a_aba3_e4340328148f.id)).thenReturn(Optional.of(p_af3f50b7_b5de_430a_aba3_e4340328148f));

        Production p_fd637b69_5370_4417_b416_e81dd4e73806 = new Production();
        p_fd637b69_5370_4417_b416_e81dd4e73806.id = UUID.fromString("fd637b69-5370-4417-b416-e81dd4e73806");
        p_fd637b69_5370_4417_b416_e81dd4e73806.title = "EduCon University Summit";
        p_fd637b69_5370_4417_b416_e81dd4e73806.clientName = "EduCon Network";
        p_fd637b69_5370_4417_b416_e81dd4e73806.eventDate = LocalDate.parse("2026-11-24");
        p_fd637b69_5370_4417_b416_e81dd4e73806.venueName = "University Auditorium";
        p_fd637b69_5370_4417_b416_e81dd4e73806.status = Production.Status.PRODUCTION;
        p_fd637b69_5370_4417_b416_e81dd4e73806.priority = Production.Priority.NORMAL;
        p_fd637b69_5370_4417_b416_e81dd4e73806.description = "Equipment Needed: 3 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_fd637b69_5370_4417_b416_e81dd4e73806);
        when(productionRepo.findById(p_fd637b69_5370_4417_b416_e81dd4e73806.id)).thenReturn(Optional.of(p_fd637b69_5370_4417_b416_e81dd4e73806));

        Production p_8b3dde04_42b8_4c21_9355_e05cec9e8f30 = new Production();
        p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id = UUID.fromString("8b3dde04-42b8-4c21-9355-e05cec9e8f30");
        p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.title = "PixelWorks Brand Film";
        p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.clientName = "PixelWorks";
        p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.eventDate = LocalDate.parse("2026-11-27");
        p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.venueName = "Studio 9";
        p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.status = Production.Status.PRODUCTION;
        p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.priority = Production.Priority.HIGH;
        p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.description = "Equipment Needed: 2 Cameras, 5 Lights, Audio Kit";
        allProductions.add(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30);
        when(productionRepo.findById(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id)).thenReturn(Optional.of(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30));

        Production p_7bce4b62_fe3d_4653_99c8_af3f6331f662 = new Production();
        p_7bce4b62_fe3d_4653_99c8_af3f6331f662.id = UUID.fromString("7bce4b62-fe3d-4653-99c8-af3f6331f662");
        p_7bce4b62_fe3d_4653_99c8_af3f6331f662.title = "Desai Family Celebration";
        p_7bce4b62_fe3d_4653_99c8_af3f6331f662.clientName = "Desai Family";
        p_7bce4b62_fe3d_4653_99c8_af3f6331f662.eventDate = LocalDate.parse("2026-11-30");
        p_7bce4b62_fe3d_4653_99c8_af3f6331f662.venueName = "Silver Oak Resort";
        p_7bce4b62_fe3d_4653_99c8_af3f6331f662.status = Production.Status.PRODUCTION;
        p_7bce4b62_fe3d_4653_99c8_af3f6331f662.priority = Production.Priority.NORMAL;
        p_7bce4b62_fe3d_4653_99c8_af3f6331f662.description = "Equipment Needed: 2 Cameras, 3 Lights, Audio Kit";
        allProductions.add(p_7bce4b62_fe3d_4653_99c8_af3f6331f662);
        when(productionRepo.findById(p_7bce4b62_fe3d_4653_99c8_af3f6331f662.id)).thenReturn(Optional.of(p_7bce4b62_fe3d_4653_99c8_af3f6331f662));

        Production p_200eb30c_93f1_4cad_a1ad_37ddf498abd1 = new Production();
        p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id = UUID.fromString("200eb30c-93f1-4cad-a1ad-37ddf498abd1");
        p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.title = "FinEdge Leadership Forum";
        p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.clientName = "FinEdge Capital";
        p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.eventDate = LocalDate.parse("2026-12-03");
        p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.venueName = "Oberoi Conference Centre";
        p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.status = Production.Status.PRODUCTION;
        p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.priority = Production.Priority.HIGH;
        p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.description = "Equipment Needed: 3 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1);
        when(productionRepo.findById(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id)).thenReturn(Optional.of(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1));

        Production p_0e0d86d5_6751_45d3_be9b_72989057b93e = new Production();
        p_0e0d86d5_6751_45d3_be9b_72989057b93e.id = UUID.fromString("0e0d86d5-6751-45d3-be9b-72989057b93e");
        p_0e0d86d5_6751_45d3_be9b_72989057b93e.title = "Noor Wedding";
        p_0e0d86d5_6751_45d3_be9b_72989057b93e.clientName = "Noor Family";
        p_0e0d86d5_6751_45d3_be9b_72989057b93e.eventDate = LocalDate.parse("2026-12-06");
        p_0e0d86d5_6751_45d3_be9b_72989057b93e.venueName = "Emerald Palace";
        p_0e0d86d5_6751_45d3_be9b_72989057b93e.status = Production.Status.PRODUCTION;
        p_0e0d86d5_6751_45d3_be9b_72989057b93e.priority = Production.Priority.HIGH;
        p_0e0d86d5_6751_45d3_be9b_72989057b93e.description = "Equipment Needed: 4 Cameras, 6 Lights, Audio Kit";
        allProductions.add(p_0e0d86d5_6751_45d3_be9b_72989057b93e);
        when(productionRepo.findById(p_0e0d86d5_6751_45d3_be9b_72989057b93e.id)).thenReturn(Optional.of(p_0e0d86d5_6751_45d3_be9b_72989057b93e));

        Production p_73d6b776_193a_4346_b64b_2cccf2b9d8fd = new Production();
        p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id = UUID.fromString("73d6b776-193a-4346-b64b-2cccf2b9d8fd");
        p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.title = "AutoTech Launch";
        p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.clientName = "AutoTech Systems";
        p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.eventDate = LocalDate.parse("2026-12-09");
        p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.venueName = "Expo Arena";
        p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.status = Production.Status.PRODUCTION;
        p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.priority = Production.Priority.HIGH;
        p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.description = "Equipment Needed: 4 Cameras, LED Wall, 8 Lights";
        allProductions.add(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd);
        when(productionRepo.findById(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id)).thenReturn(Optional.of(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd));

        Production p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df = new Production();
        p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id = UUID.fromString("6f7e6aaa-c1c8-49e9-9233-8b9620f771df");
        p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.title = "Kapoor Christmas Gala";
        p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.clientName = "Kapoor Industries";
        p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.eventDate = LocalDate.parse("2026-12-12");
        p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.venueName = "Grand Hyatt Ballroom";
        p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.status = Production.Status.PRODUCTION;
        p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.priority = Production.Priority.NORMAL;
        p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.description = "Equipment Needed: 3 Cameras, 5 Lights, Audio Kit";
        allProductions.add(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df);
        when(productionRepo.findById(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id)).thenReturn(Optional.of(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df));

        Production p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930 = new Production();
        p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id = UUID.fromString("69e1a4d4-e6e2-4e8f-bcd5-4e0ac0d0a930");
        p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.title = "Winter Beats Concert";
        p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.clientName = "Winter Beats";
        p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.eventDate = LocalDate.parse("2026-12-15");
        p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.venueName = "Open Air Arena";
        p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.status = Production.Status.PRODUCTION;
        p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.priority = Production.Priority.HIGH;
        p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.description = "Equipment Needed: 5 Cameras, 12 Lights, LED Wall, Audio Kit";
        allProductions.add(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930);
        when(productionRepo.findById(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id)).thenReturn(Optional.of(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930));

        Production p_b7881ee9_d263_4613_99e2_a04593826120 = new Production();
        p_b7881ee9_d263_4613_99e2_a04593826120.id = UUID.fromString("b7881ee9-d263-4613-99e2-a04593826120");
        p_b7881ee9_d263_4613_99e2_a04593826120.title = "Sharma Corporate Retreat";
        p_b7881ee9_d263_4613_99e2_a04593826120.clientName = "Sharma Enterprises";
        p_b7881ee9_d263_4613_99e2_a04593826120.eventDate = LocalDate.parse("2026-12-18");
        p_b7881ee9_d263_4613_99e2_a04593826120.venueName = "Hillview Resort";
        p_b7881ee9_d263_4613_99e2_a04593826120.status = Production.Status.PRODUCTION;
        p_b7881ee9_d263_4613_99e2_a04593826120.priority = Production.Priority.NORMAL;
        p_b7881ee9_d263_4613_99e2_a04593826120.description = "Equipment Needed: 3 Cameras, 4 Lights, Audio Kit";
        allProductions.add(p_b7881ee9_d263_4613_99e2_a04593826120);
        when(productionRepo.findById(p_b7881ee9_d263_4613_99e2_a04593826120.id)).thenReturn(Optional.of(p_b7881ee9_d263_4613_99e2_a04593826120));

        Production p_e5e78614_8b26_4673_a590_3e18c52a270b = new Production();
        p_e5e78614_8b26_4673_a590_3e18c52a270b.id = UUID.fromString("e5e78614-8b26-4673-a590-3e18c52a270b");
        p_e5e78614_8b26_4673_a590_3e18c52a270b.title = "Fashion Forward 2026";
        p_e5e78614_8b26_4673_a590_3e18c52a270b.clientName = "Fashion Forward India";
        p_e5e78614_8b26_4673_a590_3e18c52a270b.eventDate = LocalDate.parse("2026-12-21");
        p_e5e78614_8b26_4673_a590_3e18c52a270b.venueName = "Convention Hall";
        p_e5e78614_8b26_4673_a590_3e18c52a270b.status = Production.Status.PRODUCTION;
        p_e5e78614_8b26_4673_a590_3e18c52a270b.priority = Production.Priority.HIGH;
        p_e5e78614_8b26_4673_a590_3e18c52a270b.description = "Equipment Needed: 5 Cameras, 10 Lights, LED Wall";
        allProductions.add(p_e5e78614_8b26_4673_a590_3e18c52a270b);
        when(productionRepo.findById(p_e5e78614_8b26_4673_a590_3e18c52a270b.id)).thenReturn(Optional.of(p_e5e78614_8b26_4673_a590_3e18c52a270b));

        Production p_b77b3a24_e78c_4411_ba72_3d0064245676 = new Production();
        p_b77b3a24_e78c_4411_ba72_3d0064245676.id = UUID.fromString("b77b3a24-e78c-4411-ba72-3d0064245676");
        p_b77b3a24_e78c_4411_ba72_3d0064245676.title = "TechVista Product Demo";
        p_b77b3a24_e78c_4411_ba72_3d0064245676.clientName = "TechVista Labs";
        p_b77b3a24_e78c_4411_ba72_3d0064245676.eventDate = LocalDate.parse("2027-01-04");
        p_b77b3a24_e78c_4411_ba72_3d0064245676.venueName = "Innovation Hub";
        p_b77b3a24_e78c_4411_ba72_3d0064245676.status = Production.Status.PRODUCTION;
        p_b77b3a24_e78c_4411_ba72_3d0064245676.priority = Production.Priority.NORMAL;
        p_b77b3a24_e78c_4411_ba72_3d0064245676.description = "Equipment Needed: 3 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_b77b3a24_e78c_4411_ba72_3d0064245676);
        when(productionRepo.findById(p_b77b3a24_e78c_4411_ba72_3d0064245676.id)).thenReturn(Optional.of(p_b77b3a24_e78c_4411_ba72_3d0064245676));

        Production p_93ac8653_be1d_4720_bcac_2036d8aff61a = new Production();
        p_93ac8653_be1d_4720_bcac_2036d8aff61a.id = UUID.fromString("93ac8653-be1d-4720-bcac-2036d8aff61a");
        p_93ac8653_be1d_4720_bcac_2036d8aff61a.title = "Khan Family Reception";
        p_93ac8653_be1d_4720_bcac_2036d8aff61a.clientName = "Khan Family";
        p_93ac8653_be1d_4720_bcac_2036d8aff61a.eventDate = LocalDate.parse("2027-01-08");
        p_93ac8653_be1d_4720_bcac_2036d8aff61a.venueName = "Sapphire Banquet";
        p_93ac8653_be1d_4720_bcac_2036d8aff61a.status = Production.Status.PRODUCTION;
        p_93ac8653_be1d_4720_bcac_2036d8aff61a.priority = Production.Priority.NORMAL;
        p_93ac8653_be1d_4720_bcac_2036d8aff61a.description = "Equipment Needed: 3 Cameras, 4 Lights, Audio Kit";
        allProductions.add(p_93ac8653_be1d_4720_bcac_2036d8aff61a);
        when(productionRepo.findById(p_93ac8653_be1d_4720_bcac_2036d8aff61a.id)).thenReturn(Optional.of(p_93ac8653_be1d_4720_bcac_2036d8aff61a));

        Production p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb = new Production();
        p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id = UUID.fromString("5e28dc02-b2ec-4a34-9113-b1dcb5c575fb");
        p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.title = "GreenFest Cultural Night";
        p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.clientName = "GreenFest Foundation";
        p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.eventDate = LocalDate.parse("2027-01-12");
        p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.venueName = "City Amphitheatre";
        p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.status = Production.Status.PRODUCTION;
        p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.priority = Production.Priority.NORMAL;
        p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.description = "Equipment Needed: 4 Cameras, 8 Lights, LED Wall";
        allProductions.add(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb);
        when(productionRepo.findById(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id)).thenReturn(Optional.of(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb));

        Production p_07741b03_7ea0_4c5f_8e3a_250f0415770b = new Production();
        p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id = UUID.fromString("07741b03-7ea0-4c5f-8e3a-250f0415770b");
        p_07741b03_7ea0_4c5f_8e3a_250f0415770b.title = "Royal Heritage Exhibition";
        p_07741b03_7ea0_4c5f_8e3a_250f0415770b.clientName = "Heritage Collective";
        p_07741b03_7ea0_4c5f_8e3a_250f0415770b.eventDate = LocalDate.parse("2027-01-16");
        p_07741b03_7ea0_4c5f_8e3a_250f0415770b.venueName = "City Palace Grounds";
        p_07741b03_7ea0_4c5f_8e3a_250f0415770b.status = Production.Status.PRODUCTION;
        p_07741b03_7ea0_4c5f_8e3a_250f0415770b.priority = Production.Priority.HIGH;
        p_07741b03_7ea0_4c5f_8e3a_250f0415770b.description = "Equipment Needed: 4 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_07741b03_7ea0_4c5f_8e3a_250f0415770b);
        when(productionRepo.findById(p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id)).thenReturn(Optional.of(p_07741b03_7ea0_4c5f_8e3a_250f0415770b));

        Production p_44f1b053_6e0a_455d_94e7_90557d375440 = new Production();
        p_44f1b053_6e0a_455d_94e7_90557d375440.id = UUID.fromString("44f1b053-6e0a-455d-94e7-90557d375440");
        p_44f1b053_6e0a_455d_94e7_90557d375440.title = "Mehra Wedding";
        p_44f1b053_6e0a_455d_94e7_90557d375440.clientName = "Mehra Family";
        p_44f1b053_6e0a_455d_94e7_90557d375440.eventDate = LocalDate.parse("2027-01-20");
        p_44f1b053_6e0a_455d_94e7_90557d375440.venueName = "Rosewood Resort";
        p_44f1b053_6e0a_455d_94e7_90557d375440.status = Production.Status.PRODUCTION;
        p_44f1b053_6e0a_455d_94e7_90557d375440.priority = Production.Priority.HIGH;
        p_44f1b053_6e0a_455d_94e7_90557d375440.description = "Equipment Needed: 4 Cameras, 6 Lights, Audio Kit";
        allProductions.add(p_44f1b053_6e0a_455d_94e7_90557d375440);
        when(productionRepo.findById(p_44f1b053_6e0a_455d_94e7_90557d375440.id)).thenReturn(Optional.of(p_44f1b053_6e0a_455d_94e7_90557d375440));

        Production p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4 = new Production();
        p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.id = UUID.fromString("4db4f7aa-4df3-4f1b-98d2-65ae7ebff7e4");
        p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.title = "StartupX Demo Day";
        p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.clientName = "StartupX Network";
        p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.eventDate = LocalDate.parse("2027-01-24");
        p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.venueName = "Startup Hub";
        p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.status = Production.Status.PRODUCTION;
        p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.priority = Production.Priority.NORMAL;
        p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.description = "Equipment Needed: 3 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4);
        when(productionRepo.findById(p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.id)).thenReturn(Optional.of(p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4));

        Production p_2e9268e6_0ee5_43b3_a9f9_46c503706176 = new Production();
        p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id = UUID.fromString("2e9268e6-0ee5-43b3-a9f9-46c503706176");
        p_2e9268e6_0ee5_43b3_a9f9_46c503706176.title = "Nova Fashion Preview";
        p_2e9268e6_0ee5_43b3_a9f9_46c503706176.clientName = "Nova Fashion House";
        p_2e9268e6_0ee5_43b3_a9f9_46c503706176.eventDate = LocalDate.parse("2027-01-28");
        p_2e9268e6_0ee5_43b3_a9f9_46c503706176.venueName = "The Imperial";
        p_2e9268e6_0ee5_43b3_a9f9_46c503706176.status = Production.Status.PRODUCTION;
        p_2e9268e6_0ee5_43b3_a9f9_46c503706176.priority = Production.Priority.HIGH;
        p_2e9268e6_0ee5_43b3_a9f9_46c503706176.description = "Equipment Needed: 4 Cameras, 8 Lights, LED Wall";
        allProductions.add(p_2e9268e6_0ee5_43b3_a9f9_46c503706176);
        when(productionRepo.findById(p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id)).thenReturn(Optional.of(p_2e9268e6_0ee5_43b3_a9f9_46c503706176));

        Production p_35f4203a_a135_4032_88ba_2afc0b474b58 = new Production();
        p_35f4203a_a135_4032_88ba_2afc0b474b58.id = UUID.fromString("35f4203a-a135-4032-88ba-2afc0b474b58");
        p_35f4203a_a135_4032_88ba_2afc0b474b58.title = "Kapoor Family Anniversary";
        p_35f4203a_a135_4032_88ba_2afc0b474b58.clientName = "Kapoor Family";
        p_35f4203a_a135_4032_88ba_2afc0b474b58.eventDate = LocalDate.parse("2027-02-02");
        p_35f4203a_a135_4032_88ba_2afc0b474b58.venueName = "Lakeside Resort";
        p_35f4203a_a135_4032_88ba_2afc0b474b58.status = Production.Status.PRODUCTION;
        p_35f4203a_a135_4032_88ba_2afc0b474b58.priority = Production.Priority.NORMAL;
        p_35f4203a_a135_4032_88ba_2afc0b474b58.description = "Equipment Needed: 2 Cameras, 3 Lights, Audio Kit";
        allProductions.add(p_35f4203a_a135_4032_88ba_2afc0b474b58);
        when(productionRepo.findById(p_35f4203a_a135_4032_88ba_2afc0b474b58.id)).thenReturn(Optional.of(p_35f4203a_a135_4032_88ba_2afc0b474b58));

        Production p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93 = new Production();
        p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id = UUID.fromString("c5d08cf4-7d07-4b8f-8a1f-4d21fafa5d93");
        p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.title = "Global Finance Conference";
        p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.clientName = "Global Finance Forum";
        p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.eventDate = LocalDate.parse("2027-02-06");
        p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.venueName = "Convention Centre";
        p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.status = Production.Status.PRODUCTION;
        p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.priority = Production.Priority.HIGH;
        p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.description = "Equipment Needed: 4 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93);
        when(productionRepo.findById(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id)).thenReturn(Optional.of(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93));

        Production p_728a153a_0303_49ca_9482_47763062cf95 = new Production();
        p_728a153a_0303_49ca_9482_47763062cf95.id = UUID.fromString("728a153a-0303-49ca-9482-47763062cf95");
        p_728a153a_0303_49ca_9482_47763062cf95.title = "Sharma Sangeet & Reception";
        p_728a153a_0303_49ca_9482_47763062cf95.clientName = "Sharma Family";
        p_728a153a_0303_49ca_9482_47763062cf95.eventDate = LocalDate.parse("2027-02-10");
        p_728a153a_0303_49ca_9482_47763062cf95.venueName = "Royal Orchid";
        p_728a153a_0303_49ca_9482_47763062cf95.status = Production.Status.PRODUCTION;
        p_728a153a_0303_49ca_9482_47763062cf95.priority = Production.Priority.HIGH;
        p_728a153a_0303_49ca_9482_47763062cf95.description = "Equipment Needed: 4 Cameras, 7 Lights, Audio Kit";
        allProductions.add(p_728a153a_0303_49ca_9482_47763062cf95);
        when(productionRepo.findById(p_728a153a_0303_49ca_9482_47763062cf95.id)).thenReturn(Optional.of(p_728a153a_0303_49ca_9482_47763062cf95));

        Production p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430 = new Production();
        p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.id = UUID.fromString("aacf81e1-4dfe-43ff-ba4d-f46c10c2a430");
        p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.title = "Urban Design Expo";
        p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.clientName = "Urban Design Council";
        p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.eventDate = LocalDate.parse("2027-02-14");
        p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.venueName = "Expo Centre";
        p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.status = Production.Status.PRODUCTION;
        p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.priority = Production.Priority.NORMAL;
        p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.description = "Equipment Needed: 4 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430);
        when(productionRepo.findById(p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.id)).thenReturn(Optional.of(p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430));

        Production p_e32200e4_2da0_4fe6_9d7b_27890036adbc = new Production();
        p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id = UUID.fromString("e32200e4-2da0-4fe6-9d7b-27890036adbc");
        p_e32200e4_2da0_4fe6_9d7b_27890036adbc.title = "EduWorld Convocation";
        p_e32200e4_2da0_4fe6_9d7b_27890036adbc.clientName = "EduWorld University";
        p_e32200e4_2da0_4fe6_9d7b_27890036adbc.eventDate = LocalDate.parse("2027-02-18");
        p_e32200e4_2da0_4fe6_9d7b_27890036adbc.venueName = "University Auditorium";
        p_e32200e4_2da0_4fe6_9d7b_27890036adbc.status = Production.Status.PRODUCTION;
        p_e32200e4_2da0_4fe6_9d7b_27890036adbc.priority = Production.Priority.HIGH;
        p_e32200e4_2da0_4fe6_9d7b_27890036adbc.description = "Equipment Needed: 4 Cameras, 6 Lights, LED Wall";
        allProductions.add(p_e32200e4_2da0_4fe6_9d7b_27890036adbc);
        when(productionRepo.findById(p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id)).thenReturn(Optional.of(p_e32200e4_2da0_4fe6_9d7b_27890036adbc));

        Production p_c561bba7_f103_4d86_a779_baffc289408c = new Production();
        p_c561bba7_f103_4d86_a779_baffc289408c.id = UUID.fromString("c561bba7-f103-4d86-a779-baffc289408c");
        p_c561bba7_f103_4d86_a779_baffc289408c.title = "Music Makers Live";
        p_c561bba7_f103_4d86_a779_baffc289408c.clientName = "Music Makers Collective";
        p_c561bba7_f103_4d86_a779_baffc289408c.eventDate = LocalDate.parse("2027-02-22");
        p_c561bba7_f103_4d86_a779_baffc289408c.venueName = "Riverside Arena";
        p_c561bba7_f103_4d86_a779_baffc289408c.status = Production.Status.PRODUCTION;
        p_c561bba7_f103_4d86_a779_baffc289408c.priority = Production.Priority.HIGH;
        p_c561bba7_f103_4d86_a779_baffc289408c.description = "Equipment Needed: 5 Cameras, 10 Lights, Audio Kit, LED Wall";
        allProductions.add(p_c561bba7_f103_4d86_a779_baffc289408c);
        when(productionRepo.findById(p_c561bba7_f103_4d86_a779_baffc289408c.id)).thenReturn(Optional.of(p_c561bba7_f103_4d86_a779_baffc289408c));

        Production p_a4c049c4_ad39_4e2b_8afa_e0595b897982 = new Production();
        p_a4c049c4_ad39_4e2b_8afa_e0595b897982.id = UUID.fromString("a4c049c4-ad39-4e2b-8afa-e0595b897982");
        p_a4c049c4_ad39_4e2b_8afa_e0595b897982.title = "BrightKids Annual Function";
        p_a4c049c4_ad39_4e2b_8afa_e0595b897982.clientName = "BrightKids School";
        p_a4c049c4_ad39_4e2b_8afa_e0595b897982.eventDate = LocalDate.parse("2027-02-26");
        p_a4c049c4_ad39_4e2b_8afa_e0595b897982.venueName = "School Auditorium";
        p_a4c049c4_ad39_4e2b_8afa_e0595b897982.status = Production.Status.PRODUCTION;
        p_a4c049c4_ad39_4e2b_8afa_e0595b897982.priority = Production.Priority.NORMAL;
        p_a4c049c4_ad39_4e2b_8afa_e0595b897982.description = "Equipment Needed: 3 Cameras, 5 Lights, Audio Kit";
        allProductions.add(p_a4c049c4_ad39_4e2b_8afa_e0595b897982);
        when(productionRepo.findById(p_a4c049c4_ad39_4e2b_8afa_e0595b897982.id)).thenReturn(Optional.of(p_a4c049c4_ad39_4e2b_8afa_e0595b897982));

        Production p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b = new Production();
        p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.id = UUID.fromString("e34d77ee-845f-4148-b0a9-5f2c1a9ed11b");
        p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.title = "AutoWorld Dealer Conference";
        p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.clientName = "AutoWorld India";
        p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.eventDate = LocalDate.parse("2027-03-02");
        p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.venueName = "ITC Grand";
        p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.status = Production.Status.PRODUCTION;
        p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.priority = Production.Priority.HIGH;
        p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.description = "Equipment Needed: 4 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b);
        when(productionRepo.findById(p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.id)).thenReturn(Optional.of(p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b));

        Production p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0 = new Production();
        p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id = UUID.fromString("bc03b9aa-b4f8-487e-8ec1-b9e3d585c0f0");
        p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.title = "Rizvi Wedding";
        p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.clientName = "Rizvi Family";
        p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.eventDate = LocalDate.parse("2027-03-06");
        p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.venueName = "Heritage Palace";
        p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.status = Production.Status.PRODUCTION;
        p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.priority = Production.Priority.HIGH;
        p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.description = "Equipment Needed: 4 Cameras, 7 Lights, Audio Kit";
        allProductions.add(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0);
        when(productionRepo.findById(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id)).thenReturn(Optional.of(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0));

        Production p_cec78c6a_ce4c_47bd_9910_322d3476ea45 = new Production();
        p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id = UUID.fromString("cec78c6a-ce4c-47bd-9910-322d3476ea45");
        p_cec78c6a_ce4c_47bd_9910_322d3476ea45.title = "FutureTech Annual Summit";
        p_cec78c6a_ce4c_47bd_9910_322d3476ea45.clientName = "FutureTech Labs";
        p_cec78c6a_ce4c_47bd_9910_322d3476ea45.eventDate = LocalDate.parse("2027-03-10");
        p_cec78c6a_ce4c_47bd_9910_322d3476ea45.venueName = "Convention Hall";
        p_cec78c6a_ce4c_47bd_9910_322d3476ea45.status = Production.Status.PRODUCTION;
        p_cec78c6a_ce4c_47bd_9910_322d3476ea45.priority = Production.Priority.HIGH;
        p_cec78c6a_ce4c_47bd_9910_322d3476ea45.description = "Equipment Needed: 4 Cameras, LED Wall, 6 Lights";
        allProductions.add(p_cec78c6a_ce4c_47bd_9910_322d3476ea45);
        when(productionRepo.findById(p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id)).thenReturn(Optional.of(p_cec78c6a_ce4c_47bd_9910_322d3476ea45));

        Production p_c5ecd742_10dd_4a98_9791_c268b04e4619 = new Production();
        p_c5ecd742_10dd_4a98_9791_c268b04e4619.id = UUID.fromString("c5ecd742-10dd-4a98-9791-c268b04e4619");
        p_c5ecd742_10dd_4a98_9791_c268b04e4619.title = "Cultural Roots Festival";
        p_c5ecd742_10dd_4a98_9791_c268b04e4619.clientName = "Cultural Roots Society";
        p_c5ecd742_10dd_4a98_9791_c268b04e4619.eventDate = LocalDate.parse("2027-03-14");
        p_c5ecd742_10dd_4a98_9791_c268b04e4619.venueName = "City Grounds";
        p_c5ecd742_10dd_4a98_9791_c268b04e4619.status = Production.Status.PRODUCTION;
        p_c5ecd742_10dd_4a98_9791_c268b04e4619.priority = Production.Priority.NORMAL;
        p_c5ecd742_10dd_4a98_9791_c268b04e4619.description = "Equipment Needed: 4 Cameras, 8 Lights, Audio Kit";
        allProductions.add(p_c5ecd742_10dd_4a98_9791_c268b04e4619);
        when(productionRepo.findById(p_c5ecd742_10dd_4a98_9791_c268b04e4619.id)).thenReturn(Optional.of(p_c5ecd742_10dd_4a98_9791_c268b04e4619));

        Production p_4979f9e1_f045_4e6c_8cc8_bace86255d3c = new Production();
        p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.id = UUID.fromString("4979f9e1-f045-4e6c-8cc8-bace86255d3c");
        p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.title = "Kapoor Business Forum";
        p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.clientName = "Kapoor Industries";
        p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.eventDate = LocalDate.parse("2027-03-18");
        p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.venueName = "Business Centre";
        p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.status = Production.Status.PRODUCTION;
        p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.priority = Production.Priority.NORMAL;
        p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.description = "Equipment Needed: 3 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_4979f9e1_f045_4e6c_8cc8_bace86255d3c);
        when(productionRepo.findById(p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.id)).thenReturn(Optional.of(p_4979f9e1_f045_4e6c_8cc8_bace86255d3c));

        Production p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3 = new Production();
        p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id = UUID.fromString("635b561d-269b-4fa0-99ce-0b2d0e4f87f3");
        p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.title = "Spring Fashion Showcase";
        p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.clientName = "Springline Fashion";
        p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.eventDate = LocalDate.parse("2027-03-22");
        p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.venueName = "Imperial Ballroom";
        p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.status = Production.Status.PRODUCTION;
        p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.priority = Production.Priority.HIGH;
        p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.description = "Equipment Needed: 5 Cameras, 10 Lights, LED Wall";
        allProductions.add(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3);
        when(productionRepo.findById(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id)).thenReturn(Optional.of(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3));

        Production p_c1841e09_088d_4b1f_8da5_3cc44b867aa8 = new Production();
        p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id = UUID.fromString("c1841e09-088d-4b1f-8da5-3cc44b867aa8");
        p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.title = "Mehta Corporate Gala";
        p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.clientName = "Mehta Industries";
        p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.eventDate = LocalDate.parse("2027-03-26");
        p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.venueName = "Taj Palace";
        p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.status = Production.Status.PRODUCTION;
        p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.priority = Production.Priority.NORMAL;
        p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.description = "Equipment Needed: 4 Cameras, 6 Lights, Audio Kit";
        allProductions.add(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8);
        when(productionRepo.findById(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id)).thenReturn(Optional.of(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8));

        Production p_1b43095c_e415_4154_b6e5_493b748c3965 = new Production();
        p_1b43095c_e415_4154_b6e5_493b748c3965.id = UUID.fromString("1b43095c-e415-4154-b6e5-493b748c3965");
        p_1b43095c_e415_4154_b6e5_493b748c3965.title = "Digital India Innovation Expo";
        p_1b43095c_e415_4154_b6e5_493b748c3965.clientName = "Digital India Collective";
        p_1b43095c_e415_4154_b6e5_493b748c3965.eventDate = LocalDate.parse("2027-03-30");
        p_1b43095c_e415_4154_b6e5_493b748c3965.venueName = "India Expo Mart";
        p_1b43095c_e415_4154_b6e5_493b748c3965.status = Production.Status.PRODUCTION;
        p_1b43095c_e415_4154_b6e5_493b748c3965.priority = Production.Priority.HIGH;
        p_1b43095c_e415_4154_b6e5_493b748c3965.description = "Equipment Needed: 5 Cameras, LED Wall, Audio Kit";
        allProductions.add(p_1b43095c_e415_4154_b6e5_493b748c3965);
        when(productionRepo.findById(p_1b43095c_e415_4154_b6e5_493b748c3965.id)).thenReturn(Optional.of(p_1b43095c_e415_4154_b6e5_493b748c3965));

        Production p_1efea8db_556f_4645_b986_90c1c8da45c6 = new Production();
        p_1efea8db_556f_4645_b986_90c1c8da45c6.id = UUID.fromString("1efea8db-556f-4645-b986-90c1c8da45c6");
        p_1efea8db_556f_4645_b986_90c1c8da45c6.title = "Grand Spring Wedding";
        p_1efea8db_556f_4645_b986_90c1c8da45c6.clientName = "Singh Family";
        p_1efea8db_556f_4645_b986_90c1c8da45c6.eventDate = LocalDate.parse("2027-04-03");
        p_1efea8db_556f_4645_b986_90c1c8da45c6.venueName = "The Grand Palace";
        p_1efea8db_556f_4645_b986_90c1c8da45c6.status = Production.Status.PRODUCTION;
        p_1efea8db_556f_4645_b986_90c1c8da45c6.priority = Production.Priority.HIGH;
        p_1efea8db_556f_4645_b986_90c1c8da45c6.description = "Equipment Needed: 4 Cameras, 8 Lights, Audio Kit, LED Wall";
        allProductions.add(p_1efea8db_556f_4645_b986_90c1c8da45c6);
        when(productionRepo.findById(p_1efea8db_556f_4645_b986_90c1c8da45c6.id)).thenReturn(Optional.of(p_1efea8db_556f_4645_b986_90c1c8da45c6));

        // Production Resolvers

        EveRetrievalService.Candidate cp_84a327f4_5eaf_45c0_8dfd_73d1810035f4 = new EveRetrievalService.Candidate(
            p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id, "PRODUCTION", "Sharma Wedding", "Sharma Wedding", "Royal Orchid");
        when(retrievalService.resolveProduction("Sharma Wedding"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_84a327f4_5eaf_45c0_8dfd_73d1810035f4, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma Wedding"));

        EveRetrievalService.Candidate cp_82376a08_f232_4029_8f19_3ecae088641a = new EveRetrievalService.Candidate(
            p_82376a08_f232_4029_8f19_3ecae088641a.id, "PRODUCTION", "Sharma Reception", "Sharma Reception", "Grand Palace Hall");
        when(retrievalService.resolveProduction("Sharma Reception"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_82376a08_f232_4029_8f19_3ecae088641a, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma Reception"));

        EveRetrievalService.Candidate cp_df39bfa7_2eaf_4305_b77f_3980ed946a02 = new EveRetrievalService.Candidate(
            p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id, "PRODUCTION", "Arora Corporate Summit", "Arora Corporate Summit", "Convention Centre");
        when(retrievalService.resolveProduction("Arora Corporate Summit"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_df39bfa7_2eaf_4305_b77f_3980ed946a02, EveRetrievalService.MatchMethod.EXACT_NAME, "Arora Corporate Summit"));

        EveRetrievalService.Candidate cp_4cae2b69_a431_46bb_a4b4_de13ddafd83e = new EveRetrievalService.Candidate(
            p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id, "PRODUCTION", "Kapoor Product Launch", "Kapoor Product Launch", "The Leela Ballroom");
        when(retrievalService.resolveProduction("Kapoor Product Launch"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_4cae2b69_a431_46bb_a4b4_de13ddafd83e, EveRetrievalService.MatchMethod.EXACT_NAME, "Kapoor Product Launch"));

        EveRetrievalService.Candidate cp_bcdd676b_724e_44d8_bfda_48abc2d9ce21 = new EveRetrievalService.Candidate(
            p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id, "PRODUCTION", "Mehta Family Wedding", "Mehta Family Wedding", "Taj Palace Lawn");
        when(retrievalService.resolveProduction("Mehta Family Wedding"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_bcdd676b_724e_44d8_bfda_48abc2d9ce21, EveRetrievalService.MatchMethod.EXACT_NAME, "Mehta Family Wedding"));

        EveRetrievalService.Candidate cp_14b741e3_4096_46f4_b6ff_c21d14559654 = new EveRetrievalService.Candidate(
            p_14b741e3_4096_46f4_b6ff_c21d14559654.id, "PRODUCTION", "TechNova Annual Meet", "TechNova Annual Meet", "Hyatt Regency");
        when(retrievalService.resolveProduction("TechNova Annual Meet"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_14b741e3_4096_46f4_b6ff_c21d14559654, EveRetrievalService.MatchMethod.EXACT_NAME, "TechNova Annual Meet"));

        EveRetrievalService.Candidate cp_f21e84ca_d0fa_4137_ad9e_c438fd37b888 = new EveRetrievalService.Candidate(
            p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.id, "PRODUCTION", "Khan Nikah Ceremony", "Khan Nikah Ceremony", "Pearl Banquet");
        when(retrievalService.resolveProduction("Khan Nikah Ceremony"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_f21e84ca_d0fa_4137_ad9e_c438fd37b888, EveRetrievalService.MatchMethod.EXACT_NAME, "Khan Nikah Ceremony"));

        EveRetrievalService.Candidate cp_67cf9f86_3641_46ce_ac5a_fd760d4567e5 = new EveRetrievalService.Candidate(
            p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id, "PRODUCTION", "GreenEarth Sustainability Expo", "GreenEarth Sustainability Expo", "Expo Centre");
        when(retrievalService.resolveProduction("GreenEarth Sustainability Expo"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_67cf9f86_3641_46ce_ac5a_fd760d4567e5, EveRetrievalService.MatchMethod.EXACT_NAME, "GreenEarth Sustainability Expo"));

        EveRetrievalService.Candidate cp_af0f456b_aa71_422c_8ccc_4e54814a65f4 = new EveRetrievalService.Candidate(
            p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id, "PRODUCTION", "Royal Fashion Night", "Royal Fashion Night", "Imperial Ballroom");
        when(retrievalService.resolveProduction("Royal Fashion Night"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_af0f456b_aa71_422c_8ccc_4e54814a65f4, EveRetrievalService.MatchMethod.EXACT_NAME, "Royal Fashion Night"));

        EveRetrievalService.Candidate cp_b9d08d9b_e84f_4f27_a720_3a45db5a3876 = new EveRetrievalService.Candidate(
            p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.id, "PRODUCTION", "Kapoor Anniversary", "Kapoor Anniversary", "Lakeview Resort");
        when(retrievalService.resolveProduction("Kapoor Anniversary"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_b9d08d9b_e84f_4f27_a720_3a45db5a3876, EveRetrievalService.MatchMethod.EXACT_NAME, "Kapoor Anniversary"));

        EveRetrievalService.Candidate cp_162396c3_000d_43f3_96e0_5ff2942dce0d = new EveRetrievalService.Candidate(
            p_162396c3_000d_43f3_96e0_5ff2942dce0d.id, "PRODUCTION", "BrightStart Investor Day", "BrightStart Investor Day", "ITC Grand");
        when(retrievalService.resolveProduction("BrightStart Investor Day"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_162396c3_000d_43f3_96e0_5ff2942dce0d, EveRetrievalService.MatchMethod.EXACT_NAME, "BrightStart Investor Day"));

        EveRetrievalService.Candidate cp_470c4be3_4bbb_483b_b9c8_cf35b513cd6b = new EveRetrievalService.Candidate(
            p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id, "PRODUCTION", "Verma Wedding", "Verma Wedding", "The Grand Hyatt");
        when(retrievalService.resolveProduction("Verma Wedding"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_470c4be3_4bbb_483b_b9c8_cf35b513cd6b, EveRetrievalService.MatchMethod.EXACT_NAME, "Verma Wedding"));

        EveRetrievalService.Candidate cp_016c8cd3_5c99_4c48_accb_d950d384963d = new EveRetrievalService.Candidate(
            p_016c8cd3_5c99_4c48_accb_d950d384963d.id, "PRODUCTION", "FutureBuild Construction Expo", "FutureBuild Construction Expo", "India Expo Mart");
        when(retrievalService.resolveProduction("FutureBuild Construction Expo"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_016c8cd3_5c99_4c48_accb_d950d384963d, EveRetrievalService.MatchMethod.EXACT_NAME, "FutureBuild Construction Expo"));

        EveRetrievalService.Candidate cp_947ce209_a660_46fc_8008_c2359d55d334 = new EveRetrievalService.Candidate(
            p_947ce209_a660_46fc_8008_c2359d55d334.id, "PRODUCTION", "Singh Engagement", "Singh Engagement", "Gardenia Resort");
        when(retrievalService.resolveProduction("Singh Engagement"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_947ce209_a660_46fc_8008_c2359d55d334, EveRetrievalService.MatchMethod.EXACT_NAME, "Singh Engagement"));

        EveRetrievalService.Candidate cp_3a52884d_60ba_4f84_9056_8165a12f2998 = new EveRetrievalService.Candidate(
            p_3a52884d_60ba_4f84_9056_8165a12f2998.id, "PRODUCTION", "Urban Beats Festival", "Urban Beats Festival", "Riverside Grounds");
        when(retrievalService.resolveProduction("Urban Beats Festival"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_3a52884d_60ba_4f84_9056_8165a12f2998, EveRetrievalService.MatchMethod.EXACT_NAME, "Urban Beats Festival"));

        EveRetrievalService.Candidate cp_c7a9a4c6_9934_4b86_a691_f4b5e54429fe = new EveRetrievalService.Candidate(
            p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.id, "PRODUCTION", "Nova Motors Dealer Meet", "Nova Motors Dealer Meet", "JW Marriott");
        when(retrievalService.resolveProduction("Nova Motors Dealer Meet"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_c7a9a4c6_9934_4b86_a691_f4b5e54429fe, EveRetrievalService.MatchMethod.EXACT_NAME, "Nova Motors Dealer Meet"));

        EveRetrievalService.Candidate cp_af3f50b7_b5de_430a_aba3_e4340328148f = new EveRetrievalService.Candidate(
            p_af3f50b7_b5de_430a_aba3_e4340328148f.id, "PRODUCTION", "Malhotra Wedding", "Malhotra Wedding", "Heritage Palace");
        when(retrievalService.resolveProduction("Malhotra Wedding"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_af3f50b7_b5de_430a_aba3_e4340328148f, EveRetrievalService.MatchMethod.EXACT_NAME, "Malhotra Wedding"));

        EveRetrievalService.Candidate cp_fd637b69_5370_4417_b416_e81dd4e73806 = new EveRetrievalService.Candidate(
            p_fd637b69_5370_4417_b416_e81dd4e73806.id, "PRODUCTION", "EduCon University Summit", "EduCon University Summit", "University Auditorium");
        when(retrievalService.resolveProduction("EduCon University Summit"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_fd637b69_5370_4417_b416_e81dd4e73806, EveRetrievalService.MatchMethod.EXACT_NAME, "EduCon University Summit"));

        EveRetrievalService.Candidate cp_8b3dde04_42b8_4c21_9355_e05cec9e8f30 = new EveRetrievalService.Candidate(
            p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id, "PRODUCTION", "PixelWorks Brand Film", "PixelWorks Brand Film", "Studio 9");
        when(retrievalService.resolveProduction("PixelWorks Brand Film"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_8b3dde04_42b8_4c21_9355_e05cec9e8f30, EveRetrievalService.MatchMethod.EXACT_NAME, "PixelWorks Brand Film"));

        EveRetrievalService.Candidate cp_7bce4b62_fe3d_4653_99c8_af3f6331f662 = new EveRetrievalService.Candidate(
            p_7bce4b62_fe3d_4653_99c8_af3f6331f662.id, "PRODUCTION", "Desai Family Celebration", "Desai Family Celebration", "Silver Oak Resort");
        when(retrievalService.resolveProduction("Desai Family Celebration"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_7bce4b62_fe3d_4653_99c8_af3f6331f662, EveRetrievalService.MatchMethod.EXACT_NAME, "Desai Family Celebration"));

        EveRetrievalService.Candidate cp_200eb30c_93f1_4cad_a1ad_37ddf498abd1 = new EveRetrievalService.Candidate(
            p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id, "PRODUCTION", "FinEdge Leadership Forum", "FinEdge Leadership Forum", "Oberoi Conference Centre");
        when(retrievalService.resolveProduction("FinEdge Leadership Forum"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_200eb30c_93f1_4cad_a1ad_37ddf498abd1, EveRetrievalService.MatchMethod.EXACT_NAME, "FinEdge Leadership Forum"));

        EveRetrievalService.Candidate cp_0e0d86d5_6751_45d3_be9b_72989057b93e = new EveRetrievalService.Candidate(
            p_0e0d86d5_6751_45d3_be9b_72989057b93e.id, "PRODUCTION", "Noor Wedding", "Noor Wedding", "Emerald Palace");
        when(retrievalService.resolveProduction("Noor Wedding"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_0e0d86d5_6751_45d3_be9b_72989057b93e, EveRetrievalService.MatchMethod.EXACT_NAME, "Noor Wedding"));

        EveRetrievalService.Candidate cp_73d6b776_193a_4346_b64b_2cccf2b9d8fd = new EveRetrievalService.Candidate(
            p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id, "PRODUCTION", "AutoTech Launch", "AutoTech Launch", "Expo Arena");
        when(retrievalService.resolveProduction("AutoTech Launch"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_73d6b776_193a_4346_b64b_2cccf2b9d8fd, EveRetrievalService.MatchMethod.EXACT_NAME, "AutoTech Launch"));

        EveRetrievalService.Candidate cp_6f7e6aaa_c1c8_49e9_9233_8b9620f771df = new EveRetrievalService.Candidate(
            p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id, "PRODUCTION", "Kapoor Christmas Gala", "Kapoor Christmas Gala", "Grand Hyatt Ballroom");
        when(retrievalService.resolveProduction("Kapoor Christmas Gala"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_6f7e6aaa_c1c8_49e9_9233_8b9620f771df, EveRetrievalService.MatchMethod.EXACT_NAME, "Kapoor Christmas Gala"));

        EveRetrievalService.Candidate cp_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930 = new EveRetrievalService.Candidate(
            p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id, "PRODUCTION", "Winter Beats Concert", "Winter Beats Concert", "Open Air Arena");
        when(retrievalService.resolveProduction("Winter Beats Concert"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930, EveRetrievalService.MatchMethod.EXACT_NAME, "Winter Beats Concert"));

        EveRetrievalService.Candidate cp_b7881ee9_d263_4613_99e2_a04593826120 = new EveRetrievalService.Candidate(
            p_b7881ee9_d263_4613_99e2_a04593826120.id, "PRODUCTION", "Sharma Corporate Retreat", "Sharma Corporate Retreat", "Hillview Resort");
        when(retrievalService.resolveProduction("Sharma Corporate Retreat"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_b7881ee9_d263_4613_99e2_a04593826120, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma Corporate Retreat"));

        EveRetrievalService.Candidate cp_e5e78614_8b26_4673_a590_3e18c52a270b = new EveRetrievalService.Candidate(
            p_e5e78614_8b26_4673_a590_3e18c52a270b.id, "PRODUCTION", "Fashion Forward 2026", "Fashion Forward 2026", "Convention Hall");
        when(retrievalService.resolveProduction("Fashion Forward 2026"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_e5e78614_8b26_4673_a590_3e18c52a270b, EveRetrievalService.MatchMethod.EXACT_NAME, "Fashion Forward 2026"));

        EveRetrievalService.Candidate cp_b77b3a24_e78c_4411_ba72_3d0064245676 = new EveRetrievalService.Candidate(
            p_b77b3a24_e78c_4411_ba72_3d0064245676.id, "PRODUCTION", "TechVista Product Demo", "TechVista Product Demo", "Innovation Hub");
        when(retrievalService.resolveProduction("TechVista Product Demo"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_b77b3a24_e78c_4411_ba72_3d0064245676, EveRetrievalService.MatchMethod.EXACT_NAME, "TechVista Product Demo"));

        EveRetrievalService.Candidate cp_93ac8653_be1d_4720_bcac_2036d8aff61a = new EveRetrievalService.Candidate(
            p_93ac8653_be1d_4720_bcac_2036d8aff61a.id, "PRODUCTION", "Khan Family Reception", "Khan Family Reception", "Sapphire Banquet");
        when(retrievalService.resolveProduction("Khan Family Reception"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_93ac8653_be1d_4720_bcac_2036d8aff61a, EveRetrievalService.MatchMethod.EXACT_NAME, "Khan Family Reception"));

        EveRetrievalService.Candidate cp_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb = new EveRetrievalService.Candidate(
            p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id, "PRODUCTION", "GreenFest Cultural Night", "GreenFest Cultural Night", "City Amphitheatre");
        when(retrievalService.resolveProduction("GreenFest Cultural Night"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb, EveRetrievalService.MatchMethod.EXACT_NAME, "GreenFest Cultural Night"));

        EveRetrievalService.Candidate cp_07741b03_7ea0_4c5f_8e3a_250f0415770b = new EveRetrievalService.Candidate(
            p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id, "PRODUCTION", "Royal Heritage Exhibition", "Royal Heritage Exhibition", "City Palace Grounds");
        when(retrievalService.resolveProduction("Royal Heritage Exhibition"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_07741b03_7ea0_4c5f_8e3a_250f0415770b, EveRetrievalService.MatchMethod.EXACT_NAME, "Royal Heritage Exhibition"));

        EveRetrievalService.Candidate cp_44f1b053_6e0a_455d_94e7_90557d375440 = new EveRetrievalService.Candidate(
            p_44f1b053_6e0a_455d_94e7_90557d375440.id, "PRODUCTION", "Mehra Wedding", "Mehra Wedding", "Rosewood Resort");
        when(retrievalService.resolveProduction("Mehra Wedding"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_44f1b053_6e0a_455d_94e7_90557d375440, EveRetrievalService.MatchMethod.EXACT_NAME, "Mehra Wedding"));

        EveRetrievalService.Candidate cp_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4 = new EveRetrievalService.Candidate(
            p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.id, "PRODUCTION", "StartupX Demo Day", "StartupX Demo Day", "Startup Hub");
        when(retrievalService.resolveProduction("StartupX Demo Day"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4, EveRetrievalService.MatchMethod.EXACT_NAME, "StartupX Demo Day"));

        EveRetrievalService.Candidate cp_2e9268e6_0ee5_43b3_a9f9_46c503706176 = new EveRetrievalService.Candidate(
            p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id, "PRODUCTION", "Nova Fashion Preview", "Nova Fashion Preview", "The Imperial");
        when(retrievalService.resolveProduction("Nova Fashion Preview"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_2e9268e6_0ee5_43b3_a9f9_46c503706176, EveRetrievalService.MatchMethod.EXACT_NAME, "Nova Fashion Preview"));

        EveRetrievalService.Candidate cp_35f4203a_a135_4032_88ba_2afc0b474b58 = new EveRetrievalService.Candidate(
            p_35f4203a_a135_4032_88ba_2afc0b474b58.id, "PRODUCTION", "Kapoor Family Anniversary", "Kapoor Family Anniversary", "Lakeside Resort");
        when(retrievalService.resolveProduction("Kapoor Family Anniversary"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_35f4203a_a135_4032_88ba_2afc0b474b58, EveRetrievalService.MatchMethod.EXACT_NAME, "Kapoor Family Anniversary"));

        EveRetrievalService.Candidate cp_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93 = new EveRetrievalService.Candidate(
            p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id, "PRODUCTION", "Global Finance Conference", "Global Finance Conference", "Convention Centre");
        when(retrievalService.resolveProduction("Global Finance Conference"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93, EveRetrievalService.MatchMethod.EXACT_NAME, "Global Finance Conference"));

        EveRetrievalService.Candidate cp_728a153a_0303_49ca_9482_47763062cf95 = new EveRetrievalService.Candidate(
            p_728a153a_0303_49ca_9482_47763062cf95.id, "PRODUCTION", "Sharma Sangeet & Reception", "Sharma Sangeet & Reception", "Royal Orchid");
        when(retrievalService.resolveProduction("Sharma Sangeet & Reception"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_728a153a_0303_49ca_9482_47763062cf95, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma Sangeet & Reception"));

        EveRetrievalService.Candidate cp_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430 = new EveRetrievalService.Candidate(
            p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.id, "PRODUCTION", "Urban Design Expo", "Urban Design Expo", "Expo Centre");
        when(retrievalService.resolveProduction("Urban Design Expo"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430, EveRetrievalService.MatchMethod.EXACT_NAME, "Urban Design Expo"));

        EveRetrievalService.Candidate cp_e32200e4_2da0_4fe6_9d7b_27890036adbc = new EveRetrievalService.Candidate(
            p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id, "PRODUCTION", "EduWorld Convocation", "EduWorld Convocation", "University Auditorium");
        when(retrievalService.resolveProduction("EduWorld Convocation"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_e32200e4_2da0_4fe6_9d7b_27890036adbc, EveRetrievalService.MatchMethod.EXACT_NAME, "EduWorld Convocation"));

        EveRetrievalService.Candidate cp_c561bba7_f103_4d86_a779_baffc289408c = new EveRetrievalService.Candidate(
            p_c561bba7_f103_4d86_a779_baffc289408c.id, "PRODUCTION", "Music Makers Live", "Music Makers Live", "Riverside Arena");
        when(retrievalService.resolveProduction("Music Makers Live"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_c561bba7_f103_4d86_a779_baffc289408c, EveRetrievalService.MatchMethod.EXACT_NAME, "Music Makers Live"));

        EveRetrievalService.Candidate cp_a4c049c4_ad39_4e2b_8afa_e0595b897982 = new EveRetrievalService.Candidate(
            p_a4c049c4_ad39_4e2b_8afa_e0595b897982.id, "PRODUCTION", "BrightKids Annual Function", "BrightKids Annual Function", "School Auditorium");
        when(retrievalService.resolveProduction("BrightKids Annual Function"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_a4c049c4_ad39_4e2b_8afa_e0595b897982, EveRetrievalService.MatchMethod.EXACT_NAME, "BrightKids Annual Function"));

        EveRetrievalService.Candidate cp_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b = new EveRetrievalService.Candidate(
            p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.id, "PRODUCTION", "AutoWorld Dealer Conference", "AutoWorld Dealer Conference", "ITC Grand");
        when(retrievalService.resolveProduction("AutoWorld Dealer Conference"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b, EveRetrievalService.MatchMethod.EXACT_NAME, "AutoWorld Dealer Conference"));

        EveRetrievalService.Candidate cp_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0 = new EveRetrievalService.Candidate(
            p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id, "PRODUCTION", "Rizvi Wedding", "Rizvi Wedding", "Heritage Palace");
        when(retrievalService.resolveProduction("Rizvi Wedding"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0, EveRetrievalService.MatchMethod.EXACT_NAME, "Rizvi Wedding"));

        EveRetrievalService.Candidate cp_cec78c6a_ce4c_47bd_9910_322d3476ea45 = new EveRetrievalService.Candidate(
            p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id, "PRODUCTION", "FutureTech Annual Summit", "FutureTech Annual Summit", "Convention Hall");
        when(retrievalService.resolveProduction("FutureTech Annual Summit"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_cec78c6a_ce4c_47bd_9910_322d3476ea45, EveRetrievalService.MatchMethod.EXACT_NAME, "FutureTech Annual Summit"));

        EveRetrievalService.Candidate cp_c5ecd742_10dd_4a98_9791_c268b04e4619 = new EveRetrievalService.Candidate(
            p_c5ecd742_10dd_4a98_9791_c268b04e4619.id, "PRODUCTION", "Cultural Roots Festival", "Cultural Roots Festival", "City Grounds");
        when(retrievalService.resolveProduction("Cultural Roots Festival"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_c5ecd742_10dd_4a98_9791_c268b04e4619, EveRetrievalService.MatchMethod.EXACT_NAME, "Cultural Roots Festival"));

        EveRetrievalService.Candidate cp_4979f9e1_f045_4e6c_8cc8_bace86255d3c = new EveRetrievalService.Candidate(
            p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.id, "PRODUCTION", "Kapoor Business Forum", "Kapoor Business Forum", "Business Centre");
        when(retrievalService.resolveProduction("Kapoor Business Forum"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_4979f9e1_f045_4e6c_8cc8_bace86255d3c, EveRetrievalService.MatchMethod.EXACT_NAME, "Kapoor Business Forum"));

        EveRetrievalService.Candidate cp_635b561d_269b_4fa0_99ce_0b2d0e4f87f3 = new EveRetrievalService.Candidate(
            p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id, "PRODUCTION", "Spring Fashion Showcase", "Spring Fashion Showcase", "Imperial Ballroom");
        when(retrievalService.resolveProduction("Spring Fashion Showcase"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_635b561d_269b_4fa0_99ce_0b2d0e4f87f3, EveRetrievalService.MatchMethod.EXACT_NAME, "Spring Fashion Showcase"));

        EveRetrievalService.Candidate cp_c1841e09_088d_4b1f_8da5_3cc44b867aa8 = new EveRetrievalService.Candidate(
            p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id, "PRODUCTION", "Mehta Corporate Gala", "Mehta Corporate Gala", "Taj Palace");
        when(retrievalService.resolveProduction("Mehta Corporate Gala"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_c1841e09_088d_4b1f_8da5_3cc44b867aa8, EveRetrievalService.MatchMethod.EXACT_NAME, "Mehta Corporate Gala"));

        EveRetrievalService.Candidate cp_1b43095c_e415_4154_b6e5_493b748c3965 = new EveRetrievalService.Candidate(
            p_1b43095c_e415_4154_b6e5_493b748c3965.id, "PRODUCTION", "Digital India Innovation Expo", "Digital India Innovation Expo", "India Expo Mart");
        when(retrievalService.resolveProduction("Digital India Innovation Expo"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_1b43095c_e415_4154_b6e5_493b748c3965, EveRetrievalService.MatchMethod.EXACT_NAME, "Digital India Innovation Expo"));

        EveRetrievalService.Candidate cp_1efea8db_556f_4645_b986_90c1c8da45c6 = new EveRetrievalService.Candidate(
            p_1efea8db_556f_4645_b986_90c1c8da45c6.id, "PRODUCTION", "Grand Spring Wedding", "Grand Spring Wedding", "The Grand Palace");
        when(retrievalService.resolveProduction("Grand Spring Wedding"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_1efea8db_556f_4645_b986_90c1c8da45c6, EveRetrievalService.MatchMethod.EXACT_NAME, "Grand Spring Wedding"));

        when(productionRepo.findAll()).thenReturn(allProductions);

        // Work Tasks & Crew Mocks
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_84a327f4_5eaf_45c0_8dfd_73d1810035f4.id, e_ac2f6abe_2ed2_4b57_8405_33202ae8ade4.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_82376a08_f232_4029_8f19_3ecae088641a.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_82376a08_f232_4029_8f19_3ecae088641a.id, e_419d1b36_a304_4048_9388_6cf8146192e0.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_82376a08_f232_4029_8f19_3ecae088641a.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_82376a08_f232_4029_8f19_3ecae088641a.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_82376a08_f232_4029_8f19_3ecae088641a.id, e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_82376a08_f232_4029_8f19_3ecae088641a.id, e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id, e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id, e_3c0c72ea_6741_4258_9180_efe189abdb2f.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_df39bfa7_2eaf_4305_b77f_3980ed946a02.id, e_0c483d68_03f5_4afc_97af_97b5eeab7494.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id, e_374e916e_082a_4f73_9bbc_929ba3e68767.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id, e_677c4b84_cd2b_4ad4_87d7_5d402432161b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id, e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4cae2b69_a431_46bb_a4b4_de13ddafd83e.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id, e_489f72ca_cb16_466d_ae5a_6fc258389d8c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bcdd676b_724e_44d8_bfda_48abc2d9ce21.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_14b741e3_4096_46f4_b6ff_c21d14559654.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_14b741e3_4096_46f4_b6ff_c21d14559654.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_14b741e3_4096_46f4_b6ff_c21d14559654.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_14b741e3_4096_46f4_b6ff_c21d14559654.id, e_466339ec_f819_41bd_9ec5_861aadf073a6.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_14b741e3_4096_46f4_b6ff_c21d14559654.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_14b741e3_4096_46f4_b6ff_c21d14559654.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.id, e_419d1b36_a304_4048_9388_6cf8146192e0.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.id, e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.id, e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_f21e84ca_d0fa_4137_ad9e_c438fd37b888.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id, e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id, e_92cad804_b034_4b76_9002_e3df86876889.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_67cf9f86_3641_46ce_ac5a_fd760d4567e5.id, e_6562c77a_ce29_4703_93e2_986cd8128aad.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id, e_46370709_7ade_498e_bb8d_bda4eb872db5.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af0f456b_aa71_422c_8ccc_4e54814a65f4.id, e_34ccd1f5_73d0_4781_9d73_87601c79f38d.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b9d08d9b_e84f_4f27_a720_3a45db5a3876.id, e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_162396c3_000d_43f3_96e0_5ff2942dce0d.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_162396c3_000d_43f3_96e0_5ff2942dce0d.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_162396c3_000d_43f3_96e0_5ff2942dce0d.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_162396c3_000d_43f3_96e0_5ff2942dce0d.id, e_87776c81_5950_4a43_a314_c8a164f06c05.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_162396c3_000d_43f3_96e0_5ff2942dce0d.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_162396c3_000d_43f3_96e0_5ff2942dce0d.id, e_0c483d68_03f5_4afc_97af_97b5eeab7494.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id, e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id, e_489f72ca_cb16_466d_ae5a_6fc258389d8c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_470c4be3_4bbb_483b_b9c8_cf35b513cd6b.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_016c8cd3_5c99_4c48_accb_d950d384963d.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_016c8cd3_5c99_4c48_accb_d950d384963d.id, e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_016c8cd3_5c99_4c48_accb_d950d384963d.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_016c8cd3_5c99_4c48_accb_d950d384963d.id, e_6562c77a_ce29_4703_93e2_986cd8128aad.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_016c8cd3_5c99_4c48_accb_d950d384963d.id, e_466339ec_f819_41bd_9ec5_861aadf073a6.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_016c8cd3_5c99_4c48_accb_d950d384963d.id, e_6d5ce018_5099_44ba_80c3_d8d1de8df0e1.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_947ce209_a660_46fc_8008_c2359d55d334.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_947ce209_a660_46fc_8008_c2359d55d334.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_947ce209_a660_46fc_8008_c2359d55d334.id, e_419d1b36_a304_4048_9388_6cf8146192e0.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_947ce209_a660_46fc_8008_c2359d55d334.id, e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_947ce209_a660_46fc_8008_c2359d55d334.id, e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_3a52884d_60ba_4f84_9056_8165a12f2998.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_3a52884d_60ba_4f84_9056_8165a12f2998.id, e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_3a52884d_60ba_4f84_9056_8165a12f2998.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_3a52884d_60ba_4f84_9056_8165a12f2998.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_3a52884d_60ba_4f84_9056_8165a12f2998.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_3a52884d_60ba_4f84_9056_8165a12f2998.id, e_46370709_7ade_498e_bb8d_bda4eb872db5.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_3a52884d_60ba_4f84_9056_8165a12f2998.id, e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c7a9a4c6_9934_4b86_a691_f4b5e54429fe.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_af3f50b7_b5de_430a_aba3_e4340328148f.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af3f50b7_b5de_430a_aba3_e4340328148f.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af3f50b7_b5de_430a_aba3_e4340328148f.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af3f50b7_b5de_430a_aba3_e4340328148f.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af3f50b7_b5de_430a_aba3_e4340328148f.id, e_3c0c72ea_6741_4258_9180_efe189abdb2f.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_af3f50b7_b5de_430a_aba3_e4340328148f.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_fd637b69_5370_4417_b416_e81dd4e73806.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_fd637b69_5370_4417_b416_e81dd4e73806.id, e_374e916e_082a_4f73_9bbc_929ba3e68767.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_fd637b69_5370_4417_b416_e81dd4e73806.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_fd637b69_5370_4417_b416_e81dd4e73806.id, e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_fd637b69_5370_4417_b416_e81dd4e73806.id, e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_fd637b69_5370_4417_b416_e81dd4e73806.id, e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id, e_3c0c72ea_6741_4258_9180_efe189abdb2f.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id, e_2978aa6d_ffd1_4d6a_b47d_e065f6889fa5.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id, e_87776c81_5950_4a43_a314_c8a164f06c05.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_8b3dde04_42b8_4c21_9355_e05cec9e8f30.id, e_31b07b64_bed1_42d1_8ac6_61e18b97f0fd.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_7bce4b62_fe3d_4653_99c8_af3f6331f662.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_7bce4b62_fe3d_4653_99c8_af3f6331f662.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_7bce4b62_fe3d_4653_99c8_af3f6331f662.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_7bce4b62_fe3d_4653_99c8_af3f6331f662.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_7bce4b62_fe3d_4653_99c8_af3f6331f662.id, e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id, e_466339ec_f819_41bd_9ec5_861aadf073a6.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_200eb30c_93f1_4cad_a1ad_37ddf498abd1.id, e_0c483d68_03f5_4afc_97af_97b5eeab7494.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_0e0d86d5_6751_45d3_be9b_72989057b93e.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_0e0d86d5_6751_45d3_be9b_72989057b93e.id, e_419d1b36_a304_4048_9388_6cf8146192e0.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_0e0d86d5_6751_45d3_be9b_72989057b93e.id, e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_0e0d86d5_6751_45d3_be9b_72989057b93e.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_0e0d86d5_6751_45d3_be9b_72989057b93e.id, e_489f72ca_cb16_466d_ae5a_6fc258389d8c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_0e0d86d5_6751_45d3_be9b_72989057b93e.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id, e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id, e_374e916e_082a_4f73_9bbc_929ba3e68767.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_73d6b776_193a_4346_b64b_2cccf2b9d8fd.id, e_92cad804_b034_4b76_9002_e3df86876889.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_6f7e6aaa_c1c8_49e9_9233_8b9620f771df.id, e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id, e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id, e_46370709_7ade_498e_bb8d_bda4eb872db5.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_69e1a4d4_e6e2_4e8f_bcd5_4e0ac0d0a930.id, e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_b7881ee9_d263_4613_99e2_a04593826120.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b7881ee9_d263_4613_99e2_a04593826120.id, e_374e916e_082a_4f73_9bbc_929ba3e68767.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b7881ee9_d263_4613_99e2_a04593826120.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b7881ee9_d263_4613_99e2_a04593826120.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b7881ee9_d263_4613_99e2_a04593826120.id, e_6562c77a_ce29_4703_93e2_986cd8128aad.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_e5e78614_8b26_4673_a590_3e18c52a270b.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e5e78614_8b26_4673_a590_3e18c52a270b.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e5e78614_8b26_4673_a590_3e18c52a270b.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e5e78614_8b26_4673_a590_3e18c52a270b.id, e_46370709_7ade_498e_bb8d_bda4eb872db5.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e5e78614_8b26_4673_a590_3e18c52a270b.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e5e78614_8b26_4673_a590_3e18c52a270b.id, e_34ccd1f5_73d0_4781_9d73_87601c79f38d.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e5e78614_8b26_4673_a590_3e18c52a270b.id, e_a6476cec_f682_4c4f_a856_e83ddeb7b46b.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_b77b3a24_e78c_4411_ba72_3d0064245676.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b77b3a24_e78c_4411_ba72_3d0064245676.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b77b3a24_e78c_4411_ba72_3d0064245676.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b77b3a24_e78c_4411_ba72_3d0064245676.id, e_466339ec_f819_41bd_9ec5_861aadf073a6.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_b77b3a24_e78c_4411_ba72_3d0064245676.id, e_0c483d68_03f5_4afc_97af_97b5eeab7494.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_93ac8653_be1d_4720_bcac_2036d8aff61a.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_93ac8653_be1d_4720_bcac_2036d8aff61a.id, e_419d1b36_a304_4048_9388_6cf8146192e0.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_93ac8653_be1d_4720_bcac_2036d8aff61a.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_93ac8653_be1d_4720_bcac_2036d8aff61a.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_93ac8653_be1d_4720_bcac_2036d8aff61a.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id, e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id, e_92cad804_b034_4b76_9002_e3df86876889.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_5e28dc02_b2ec_4a34_9113_b1dcb5c575fb.id, e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id, e_6562c77a_ce29_4703_93e2_986cd8128aad.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_07741b03_7ea0_4c5f_8e3a_250f0415770b.id, e_87776c81_5950_4a43_a314_c8a164f06c05.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_44f1b053_6e0a_455d_94e7_90557d375440.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_44f1b053_6e0a_455d_94e7_90557d375440.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_44f1b053_6e0a_455d_94e7_90557d375440.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_44f1b053_6e0a_455d_94e7_90557d375440.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_44f1b053_6e0a_455d_94e7_90557d375440.id, e_489f72ca_cb16_466d_ae5a_6fc258389d8c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_44f1b053_6e0a_455d_94e7_90557d375440.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4db4f7aa_4df3_4f1b_98d2_65ae7ebff7e4.id, e_0c483d68_03f5_4afc_97af_97b5eeab7494.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id, e_46370709_7ade_498e_bb8d_bda4eb872db5.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_2e9268e6_0ee5_43b3_a9f9_46c503706176.id, e_34ccd1f5_73d0_4781_9d73_87601c79f38d.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_35f4203a_a135_4032_88ba_2afc0b474b58.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_35f4203a_a135_4032_88ba_2afc0b474b58.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_35f4203a_a135_4032_88ba_2afc0b474b58.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_35f4203a_a135_4032_88ba_2afc0b474b58.id, e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id, e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id, e_466339ec_f819_41bd_9ec5_861aadf073a6.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5d08cf4_7d07_4b8f_8a1f_4d21fafa5d93.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_728a153a_0303_49ca_9482_47763062cf95.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_728a153a_0303_49ca_9482_47763062cf95.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_728a153a_0303_49ca_9482_47763062cf95.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_728a153a_0303_49ca_9482_47763062cf95.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_728a153a_0303_49ca_9482_47763062cf95.id, e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_728a153a_0303_49ca_9482_47763062cf95.id, e_489f72ca_cb16_466d_ae5a_6fc258389d8c.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.id, e_374e916e_082a_4f73_9bbc_929ba3e68767.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.id, e_92cad804_b034_4b76_9002_e3df86876889.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.id, e_6562c77a_ce29_4703_93e2_986cd8128aad.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_aacf81e1_4dfe_43ff_ba4d_f46c10c2a430.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id, e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e32200e4_2da0_4fe6_9d7b_27890036adbc.id, e_88b562b0_24fe_4f6a_98e6_52b61c71e3a2.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_c561bba7_f103_4d86_a779_baffc289408c.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c561bba7_f103_4d86_a779_baffc289408c.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c561bba7_f103_4d86_a779_baffc289408c.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c561bba7_f103_4d86_a779_baffc289408c.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c561bba7_f103_4d86_a779_baffc289408c.id, e_46370709_7ade_498e_bb8d_bda4eb872db5.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c561bba7_f103_4d86_a779_baffc289408c.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c561bba7_f103_4d86_a779_baffc289408c.id, e_92cad804_b034_4b76_9002_e3df86876889.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_a4c049c4_ad39_4e2b_8afa_e0595b897982.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_a4c049c4_ad39_4e2b_8afa_e0595b897982.id, e_419d1b36_a304_4048_9388_6cf8146192e0.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_a4c049c4_ad39_4e2b_8afa_e0595b897982.id, e_bf8e1a3d_5695_4af1_a1f8_6bb76cd20d68.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_a4c049c4_ad39_4e2b_8afa_e0595b897982.id, e_52116d05_09c6_4880_a3e3_fac7c5d105e1.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_a4c049c4_ad39_4e2b_8afa_e0595b897982.id, e_b05870fa_ab9a_490d_bfc0_2953b5e70f1c.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.id, e_466339ec_f819_41bd_9ec5_861aadf073a6.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_e34d77ee_845f_4148_b0a9_5f2c1a9ed11b.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id, e_6d6d7328_bc92_42b5_82c0_d0aa21ac0280.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_bc03b9aa_b4f8_487e_8ec1_b9e3d585c0f0.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id, e_87776c81_5950_4a43_a314_c8a164f06c05.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_cec78c6a_ce4c_47bd_9910_322d3476ea45.id, e_0c483d68_03f5_4afc_97af_97b5eeab7494.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_c5ecd742_10dd_4a98_9791_c268b04e4619.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5ecd742_10dd_4a98_9791_c268b04e4619.id, e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5ecd742_10dd_4a98_9791_c268b04e4619.id, e_92cad804_b034_4b76_9002_e3df86876889.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5ecd742_10dd_4a98_9791_c268b04e4619.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5ecd742_10dd_4a98_9791_c268b04e4619.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c5ecd742_10dd_4a98_9791_c268b04e4619.id, e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.id, e_374e916e_082a_4f73_9bbc_929ba3e68767.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_4979f9e1_f045_4e6c_8cc8_bace86255d3c.id, e_0c483d68_03f5_4afc_97af_97b5eeab7494.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id, e_e9ce13f2_13bb_4a55_bb68_b09d4b6ca614.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id, e_46370709_7ade_498e_bb8d_bda4eb872db5.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_635b561d_269b_4fa0_99ce_0b2d0e4f87f3.id, e_34ccd1f5_73d0_4781_9d73_87601c79f38d.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id, e_213b479d_d3f4_42bf_a5b1_f0e7f46f0313.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id, e_b9736af3_cc70_4160_a98c_ce2aa403d099.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id, e_a3a90e44_ddd3_4080_9806_56d71edc53ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_c1841e09_088d_4b1f_8da5_3cc44b867aa8.id, e_ef9abad4_2c31_49f7_b4f1_c6c94a4647be.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_1b43095c_e415_4154_b6e5_493b748c3965.id), any())).thenReturn(3L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1b43095c_e415_4154_b6e5_493b748c3965.id, e_e1d5967b_b53a_4609_90a2_4078a406ae2b.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1b43095c_e415_4154_b6e5_493b748c3965.id, e_68f0f5a7_a293_449c_83ad_3b1c99ab69ee.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1b43095c_e415_4154_b6e5_493b748c3965.id, e_d11fda00_248e_4d83_acb5_03179f8ab04c.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1b43095c_e415_4154_b6e5_493b748c3965.id, e_6562c77a_ce29_4703_93e2_986cd8128aad.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1b43095c_e415_4154_b6e5_493b748c3965.id, e_466339ec_f819_41bd_9ec5_861aadf073a6.id)).thenReturn(true);
        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_1efea8db_556f_4645_b986_90c1c8da45c6.id), any())).thenReturn(4L);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1efea8db_556f_4645_b986_90c1c8da45c6.id, e_11c6c16d_8800_48e7_828a_4955e3f470a9.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1efea8db_556f_4645_b986_90c1c8da45c6.id, e_ee1cbfc7_bd4e_485f_af31_23ebc8fefb95.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1efea8db_556f_4645_b986_90c1c8da45c6.id, e_7cea4b78_21a7_4ee5_bacf_30914d8a2606.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1efea8db_556f_4645_b986_90c1c8da45c6.id, e_2a1c06f1_8e47_49b4_9e2a_7e354c7fbd84.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1efea8db_556f_4645_b986_90c1c8da45c6.id, e_9969d4d9_d4dd_4b99_8a55_724ae73c9d69.id)).thenReturn(true);
        when(memberRepo.existsByProductionIdAndEmployeeId(p_1efea8db_556f_4645_b986_90c1c8da45c6.id, e_3020eff2_fa63_411f_8225_8826b4e16b0e.id)).thenReturn(true);

    }

    @AfterAll
    void tearDownAll() {
        if (qwenProvider != null) {
            qwenProvider.destroy();
        }
    }

    @Test
    @Order(1)
    void evaluate100Questions() throws Exception {
        String[] questions = new String[] {
            "Kabir Khan ka role kya hai?",
            "Production department me kaun kaun hai?",
            "Camera team me kitne log hain?",
            "Kaun kaun Sound department me kaam karta hai?",
            "Rohan Sharma ki joining kab hui thi?",
            "Neha Bhatia ka salary kitna hai?",
            "Kaunse employees contract basis pe kaam kar rahe hain?",
            "Kaun kaun abhi leave pe hai?",
            "Part-time employees kaun hain?",
            "Lighting department me sabse senior kaun hai?",
            "Kis employee ka role Equipment Manager hai?",
            "Kabir aur Rahul dono ka role kya hai?",
            "Kya Nikhil Arora abhi available hai?",
            "Accounts aur Client Services me kaun kaun hai?",
            "Kis employee ka salary 70,000 hai?",
            "Sharma Wedding kab hai?",
            "Sharma Wedding ka client kaun hai?",
            "Sharma Wedding kahan ho raha hai?",
            "Sharma Wedding me kaunsa equipment chahiye?",
            "Royal Fashion Night ka venue kya hai?",
            "MIPS wala event kis client ka hai?",
            "Arora Corporate Summit kab start hota hai?",
            "Kapoor Product Launch ki priority kya hai?",
            "Kaunsa event Noida me hai?",
            "Bengaluru me kaun kaunse productions hain?",
            "Kaunsa production 8 October ko hai?",
            "Kaunse events HIGH priority wale hain?",
            "Sabse bada contract kis production ka hai?",
            "Kaunse event ka contract 3 lakh se kam hai?",
            "Sabse late khatam hone wala production kaunsa hai?",
            "Sharma Wedding me kaun kaun assigned hai?",
            "Royal Fashion Night ka crew dikhao.",
            "Mehta Family Wedding me kitne log kaam kar rahe hain?",
            "Kabir Khan kin kin productions me assigned hai?",
            "Rohan Sharma kitne events me hai?",
            "Faisal Mirza kaunse productions cover kar raha hai?",
            "Sharma Family ke aur kaunse events hain?",
            "Sara Khan sabse zyada kis type ke events me assigned hai?",
            "Kaunse productions me Imran Hussain hai?",
            "Mujhe aise events dikhao jahan Priya Nair aur Sara Khan dono hain.",
            "Kaunse event me 6 crew members hain?",
            "Kis production me sabse zyada crew hai?",
            "Sharma Wedding me abhi kya pending hai?",
            "Royal Fashion Night ke open tasks kya hain?",
            "Kaunse production me sabse zyada pending tasks hain?",
            "Kis event ka workload sabse zyada lag raha hai?",
            "Sharma Wedding ka camera setup task pending hai kya?",
            "Kaunse events me sound check ka task hai?",
            "Kaunse productions me LED test karna hai?",
            "Mujhe woh events batao jahan rehearsal ka task hai.",
            "Kis production me venue inspection karni hai?",
            "Kaunse events me equipment delivery ka kaam hai?",
            "Sabhi productions me total kitne operational tasks listed hain?",
            "Aisa kaunsa production hai jisme sabse kam open work hai?",
            "Sharma Wedding ke liye kitne cameras chahiye?",
            "Royal Fashion Night me kitni lights chahiye?",
            "Kaunse event me LED Wall chahiye?",
            "Sharma Wedding me audio setup hai kya?",
            "5 cameras wale saare productions kaunse hain?",
            "Sabse zyada lights kis production ke liye chahiye?",
            "Kin events me Audio Kit aur LED Wall dono hain?",
            "Mujhe 4 cameras aur 6 lights wale events batao.",
            "Kaunse production me sirf 2 cameras hain?",
            "Kis event ko sabse heavy equipment setup chahiye?",
            "Gaffer Tape ka kitna stock available hai?",
            "Kabir Khan ko kitna dena hai?",
            "Rohan Sharma ka outstanding kitna hai?",
            "Sharma Wedding ka contract kitne ka hai?",
            "Sharma Wedding me kitna advance aa chuka hai?",
            "Sharma Wedding ka abhi kitna balance outstanding hai?",
            "Kaunse production ka advance 2 lakh se zyada hai?",
            "Sabse bada contract kis client ka hai?",
            "Kin productions me aadha contract advance me aa chuka hai?",
            "Sharma Family ke saare events mila ke contract value kitni hai?",
            "Kis event ka received amount sabse kam hai?",
            "Agla event kaunsa hai?",
            "Agle hafte kaun kaunse productions hain?",
            "October ke events kaunse hain?",
            "Next month kitne events scheduled hain?",
            "Is month ka sabse high priority event kaunsa hai?"
        };
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (int i = 0; i < questions.length; i++) {
            System.out.println("Evaluating " + (i+1) + "/100: " + questions[i]);
            long start = System.currentTimeMillis();
            
            var resp = cognitiveRuntime.execute(questions[i], sessionId, "", null, qwenProvider);
            
            long latency = System.currentTimeMillis() - start;
            
            Map<String, Object> r = new HashMap<>();
            r.put("question_number", i + 1);
            r.put("question_text", questions[i]);
            r.put("latency_ms", latency);
            r.put("status", resp.status());
            r.put("answer", resp.answer());
            r.put("model_invoked", latency > 5000);
            
            List<String> reasoning = new ArrayList<>();
            if (resp.reasoningSteps() != null) {
                for (var step : resp.reasoningSteps()) {
                    reasoning.add(step.stage() + " (" + step.relatedTool() + "): " + step.summary());
                }
            }
            r.put("reasoning_steps", reasoning);
            
            List<String> evidence = new ArrayList<>();
            if (resp.evidence() != null) {
                for (var ev : resp.evidence()) {
                    evidence.add(ev.domain() + ": " + ev.label() + " - " + ev.value());
                }
            }
            r.put("evidence", evidence);
            
            results.add(r);
            
            // Write intermediate results in case of crash
            writeResults(results);
        }
    }
    
    private void writeResults(List<Map<String, Object>> results) {
        try {
            File d = new File("eve");
            if(!d.exists()) d.mkdirs();
            FileWriter fw = new FileWriter("eve/eve_100_question_results.json");
            
            fw.write("[\n");
            for(int i=0; i<results.size(); i++) {
                Map<String,Object> r = results.get(i);
                fw.write("  {\n");
                fw.write("    \"question_number\": " + r.get("question_number") + ",\n");
                fw.write("    \"question_text\": \"" + r.get("question_text").toString().replace("\"", "\\\"") + "\",\n");
                fw.write("    \"latency_ms\": " + r.get("latency_ms") + ",\n");
                fw.write("    \"status\": \"" + r.get("status") + "\",\n");
                if (r.get("answer") != null) {
                    fw.write("    \"answer\": \"" + r.get("answer").toString().replace("\"", "\\\"").replace("\n", " ") + "\",\n");
                } else {
                    fw.write("    \"answer\": null,\n");
                }
                fw.write("    \"model_invoked\": " + r.get("model_invoked") + ",\n");
                
                fw.write("    \"reasoning_steps\": [");
                List<String> rs = (List<String>) r.get("reasoning_steps");
                for(int j=0; j<rs.size(); j++) {
                    fw.write("\"" + rs.get(j).replace("\"", "\\\"").replace("\n", " ") + "\"");
                    if (j < rs.size() - 1) fw.write(", ");
                }
                fw.write("],\n");
                
                fw.write("    \"evidence\": [");
                List<String> ev = (List<String>) r.get("evidence");
                for(int j=0; j<ev.size(); j++) {
                    fw.write("\"" + ev.get(j).replace("\"", "\\\"").replace("\n", " ") + "\"");
                    if (j < ev.size() - 1) fw.write(", ");
                }
                fw.write("]\n");
                
                fw.write("  }");
                if (i < results.size() - 1) fw.write(",\n");
            }
            fw.write("\n]\n");
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
