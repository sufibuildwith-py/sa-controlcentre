import csv
import json
import uuid

def generate_java():
    with open('people_data.txt', 'r', encoding='utf-8') as f:
        people_lines = f.readlines()
    with open('production_data.txt', 'r', encoding='utf-8') as f:
        prod_lines = f.readlines()
    with open('saved 100-question evaluation file.txt', 'r', encoding='utf-8') as f:
        q_lines = f.readlines()
        
    questions = []
    for line in q_lines:
        line = line.strip()
        if not line or line.startswith(('#', 'A.', 'B.', 'C.', 'D.', 'E.', 'F.', 'G.', 'H.', 'I.', 'Casual', '---')): 
            continue
        # Extract the question text (e.g. "1. Kabir Khan ka role kya hai?")
        if '. ' in line:
            parts = line.split('. ', 1)
            if parts[0].isdigit():
                q_text = parts[1]
                questions.append(q_text)
        elif line:
            questions.append(line)
            
    # filter questions to 80 ERP + 20 casual as per rules.
    # From prompt: "Use questions 1-80 from the ERP set (categories A through first 5 of temporal G) + all 20 casual questions = 100 total."
    erp_questions = questions[:80]
    # Find casual questions in the original file text:
    casual_started = False
    casual_qs = []
    for line in q_lines:
        line = line.strip()
        if "Casual" in line or "101." in line:
            casual_started = True
        if casual_started and '. ' in line:
            parts = line.split('. ', 1)
            if parts[0].isdigit() and int(parts[0]) >= 101:
                casual_qs.append(parts[1])
                
    final_questions = erp_questions + casual_qs[:20]
    if len(final_questions) > 100:
        final_questions = final_questions[:100]

    # Parse People
    people = []
    for line in people_lines:
        if not line.startswith('|') or 'Employee code' in line or '---' in line:
            continue
        parts = [p.strip() for p in line.split('|')]
        if len(parts) > 15:
            # | # | Employee code | Joining date | First name | Last name | Display name | Role title | Department | Employment type | Full time | Part time | Contract | Phone | WhatsApp phone | Email | Status | Base salary (₹) | Currency | Notes |
            emp = {
                'id': str(uuid.uuid4()),
                'code': parts[2],
                'first_name': parts[4],
                'last_name': parts[5],
                'display_name': parts[6],
                'role': parts[7],
                'department': parts[8],
                'status': parts[16],
                'salary': parts[17]
            }
            people.append(emp)

    # Parse Productions
    productions = []
    for line in prod_lines:
        if not line.startswith('|') or 'Program' in line or '---' in line:
            continue
        parts = [p.strip() for p in line.split('|')]
        if len(parts) > 10:
            # | # | Program | Client | Date | Priority | Venue | Address | Equipment Needed | Contract (₹) | Advance (₹) | Start–End | Crew | Operational Tasks | Notes |
            date_str = parts[4]
            # convert dd-mm-yyyy to yyyy-mm-dd
            d_parts = date_str.split('-')
            if len(d_parts) == 3:
                iso_date = f"{d_parts[2]}-{d_parts[1]}-{d_parts[0]}"
            else:
                iso_date = "2026-10-01"
            
            p = {
                'id': str(uuid.uuid4()),
                'title': parts[2],
                'client': parts[3],
                'date': iso_date,
                'priority': parts[5],
                'venue': parts[6],
                'equipment': parts[8],
                'crew': parts[12],
                'tasks': parts[13]
            }
            productions.append(p)
            
    # Generate Java Code
    java_code = """package com.saproduction.command.eve.evaluation;

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
"""
    
    java_code += "        // Employees\n"
    for emp in people:
        status_val = "Employee.Status.ACTIVE"
        if "leave" in emp['status'].lower():
            status_val = "Employee.Status.ON_LEAVE"
        elif "inactive" in emp['status'].lower():
            status_val = "Employee.Status.INACTIVE"
            
        # extract numeric salary
        salary_str = emp['salary'].replace(',', '')
        if not salary_str.isdigit():
            salary_str = "0"
            
        java_code += f"""
        Employee e_{emp['id'].replace('-', '_')} = new Employee();
        e_{emp['id'].replace('-', '_')}.id = UUID.fromString("{emp['id']}");
        e_{emp['id'].replace('-', '_')}.employeeCode = "{emp['code']}";
        e_{emp['id'].replace('-', '_')}.firstName = "{emp['first_name']}";
        e_{emp['id'].replace('-', '_')}.lastName = "{emp['last_name']}";
        e_{emp['id'].replace('-', '_')}.displayName = "{emp['display_name']}";
        e_{emp['id'].replace('-', '_')}.roleTitle = "{emp['role']}";
        e_{emp['id'].replace('-', '_')}.department = "{emp['department']}";
        e_{emp['id'].replace('-', '_')}.status = {status_val};
        e_{emp['id'].replace('-', '_')}.baseSalaryMinor = {salary_str}L * 100L;
        allEmployees.add(e_{emp['id'].replace('-', '_')});
        when(employeeRepo.findById(e_{emp['id'].replace('-', '_')}.id)).thenReturn(Optional.of(e_{emp['id'].replace('-', '_')}));
"""

    java_code += "\n        // Employee Resolvers\n"
    for emp in people:
        java_code += f"""
        EveRetrievalService.Candidate c_{emp['id'].replace('-', '_')} = new EveRetrievalService.Candidate(
            e_{emp['id'].replace('-', '_')}.id, "EMPLOYEE", "{emp['display_name']}", "{emp['code']}", "{emp['role']}");
        when(retrievalService.resolveEmployee("{emp['first_name']}"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(c_{emp['id'].replace('-', '_')}, EveRetrievalService.MatchMethod.EXACT_NAME, "{emp['first_name']}"));
"""

    java_code += "\n        when(employeeRepo.findAll()).thenReturn(allEmployees);\n"
        
    java_code += "\n        // Productions\n"
    for p in productions:
        priority_val = "Production.Priority.NORMAL"
        p_val = p['priority'].upper()
        if "HIGH" in p_val: priority_val = "Production.Priority.HIGH"
        elif "LOW" in p_val: priority_val = "Production.Priority.LOW"
        elif "URGENT" in p_val: priority_val = "Production.Priority.URGENT"
        
        java_code += f"""
        Production p_{p['id'].replace('-', '_')} = new Production();
        p_{p['id'].replace('-', '_')}.id = UUID.fromString("{p['id']}");
        p_{p['id'].replace('-', '_')}.title = "{p['title']}";
        p_{p['id'].replace('-', '_')}.clientName = "{p['client']}";
        p_{p['id'].replace('-', '_')}.eventDate = LocalDate.parse("{p['date']}");
        p_{p['id'].replace('-', '_')}.venueName = "{p['venue']}";
        p_{p['id'].replace('-', '_')}.status = Production.Status.PRODUCTION;
        p_{p['id'].replace('-', '_')}.priority = {priority_val};
        p_{p['id'].replace('-', '_')}.description = "Equipment Needed: {p['equipment']}";
        allProductions.add(p_{p['id'].replace('-', '_')});
        when(productionRepo.findById(p_{p['id'].replace('-', '_')}.id)).thenReturn(Optional.of(p_{p['id'].replace('-', '_')}));
"""

    java_code += "\n        // Production Resolvers\n"
    for p in productions:
        java_code += f"""
        EveRetrievalService.Candidate cp_{p['id'].replace('-', '_')} = new EveRetrievalService.Candidate(
            p_{p['id'].replace('-', '_')}.id, "PRODUCTION", "{p['title']}", "{p['title']}", "{p['venue']}");
        when(retrievalService.resolveProduction("{p['title']}"))
            .thenReturn(EveRetrievalService.ResolutionResult.resolved(cp_{p['id'].replace('-', '_')}, EveRetrievalService.MatchMethod.EXACT_NAME, "{p['title']}"));
"""

    java_code += "\n        when(productionRepo.findAll()).thenReturn(allProductions);\n"

    java_code += "\n        // Work Tasks & Crew Mocks\n"
    for p in productions:
        num_tasks = len([x for x in p['tasks'].split(';') if x.strip()])
        java_code += f'        when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p_{p["id"].replace("-", "_")}.id), any())).thenReturn({num_tasks}L);\n'
        
        # for crew, just checking contains in the string for exists query?
        # The existing mock: when(memberRepo.existsByProductionIdAndEmployeeId(sharmaWedding.id, kabir.id)).thenReturn(true);
        crew_names = [x.strip() for x in p['crew'].split(',')]
        for name in crew_names:
            if not name: continue
            # find employee with this name
            matched_emp = next((e for e in people if e['display_name'].lower() == name.lower() or e['first_name'].lower() == name.lower()), None)
            if matched_emp:
                java_code += f'        when(memberRepo.existsByProductionIdAndEmployeeId(p_{p["id"].replace("-", "_")}.id, e_{matched_emp["id"].replace("-", "_")}.id)).thenReturn(true);\n'

    java_code += """
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
"""
    for i, q in enumerate(final_questions):
        q = q.replace('"', '\\"')
        java_code += f'            "{q}"{"," if i < len(final_questions)-1 else ""}\n'
        
    java_code += """        };
        
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
                    reasoning.add(step.stage() + " (" + step.relatedTool() + "): " + step.details());
                }
            }
            r.put("reasoning_steps", reasoning);
            
            List<String> evidence = new ArrayList<>();
            if (resp.evidence() != null) {
                for (var ev : resp.evidence()) {
                    evidence.add(ev.domain() + ": " + ev.label() + " - " + ev.summary());
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
            
            fw.write("[\\n");
            for(int i=0; i<results.size(); i++) {
                Map<String,Object> r = results.get(i);
                fw.write("  {\\n");
                fw.write("    \\"question_number\\": " + r.get("question_number") + ",\\n");
                fw.write("    \\"question_text\\": \\"" + r.get("question_text").toString().replace("\\"", "\\\\\\"") + "\\",\\n");
                fw.write("    \\"latency_ms\\": " + r.get("latency_ms") + ",\\n");
                fw.write("    \\"status\\": \\"" + r.get("status") + "\\",\\n");
                if (r.get("answer") != null) {
                    fw.write("    \\"answer\\": \\"" + r.get("answer").toString().replace("\\"", "\\\\\\"").replace("\\n", " ") + "\\",\\n");
                } else {
                    fw.write("    \\"answer\\": null,\\n");
                }
                fw.write("    \\"model_invoked\\": " + r.get("model_invoked") + ",\\n");
                
                fw.write("    \\"reasoning_steps\\": [");
                List<String> rs = (List<String>) r.get("reasoning_steps");
                for(int j=0; j<rs.size(); j++) {
                    fw.write("\\"" + rs.get(j).replace("\\"", "\\\\\\\"").replace("\\n", " ") + "\\"");
                    if (j < rs.size() - 1) fw.write(", ");
                }
                fw.write("],\\n");
                
                fw.write("    \\"evidence\\": [");
                List<String> ev = (List<String>) r.get("evidence");
                for(int j=0; j<ev.size(); j++) {
                    fw.write("\\"" + ev.get(j).replace("\\"", "\\\\\\\"").replace("\\n", " ") + "\\"");
                    if (j < ev.size() - 1) fw.write(", ");
                }
                fw.write("]\\n");
                
                fw.write("  }");
                if (i < results.size() - 1) fw.write(",\\n");
            }
            fw.write("\\n]\\n");
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
"""
    with open(r'apps\backend\src\test\java\com\saproduction\command\eve\evaluation\Eve100QuestionEvaluationTest.java', 'w', encoding='utf-8') as out:
        out.write(java_code)

if __name__ == '__main__':
    generate_java()
