package com.saproduction.command;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker=true)
class PostgresApplicationTest {
  @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url",postgres::getJdbcUrl);
    properties.add("spring.datasource.username",postgres::getUsername);
    properties.add("spring.datasource.password",postgres::getPassword);
    properties.add("app.demo-seed",()->true);
  }
  @Autowired JdbcTemplate jdbc;
  @Autowired @Qualifier("demoData") CommandLineRunner seed;
  @Autowired ObjectMapper json;
  @LocalServerPort int port;

  @Test void realApplicationValidatesSchemaAndBootstrapIsIdempotent() throws Exception {
    assertSeed();
    seed.run();
    assertSeed();
    assertThat(jdbc.queryForObject("select count(*) from employees where salary_currency='INR'",Integer.class)).isEqualTo(12);
    verifyHttpWorkflows();
  }
  private void verifyHttpWorkflows() throws Exception {
    var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    call(client,"POST","/auth/login","{\"email\":\"owner@saproduction.local\",\"password\":\"SADemo!2026\"}");
    assertThat(call(client,"GET","/auth/me",null).path("email").asText()).isEqualTo("owner@saproduction.local");
    String today=LocalDate.now().toString();
    var day=call(client,"GET","/attendance?date="+today,null);
    assertThat(day.path("rows").size()).isEqualTo(12);
    for(var row:day.path("rows")) assertThat(row.path("record").path("employeeId").asText()).isEqualTo(row.path("employee").path("id").asText());
    var seededLeave=call(client,"GET","/leave-requests",null).get(0);
    assertThat(seededLeave.path("employeeName").asText()).isEqualTo("Farhan");
    String employee="""
      {"employeeCode":"SA-HTTP","firstName":"Release","displayName":"Release Check","phone":"+919000000999","roleTitle":"Editor","department":"Post Production","employmentType":"FULL_TIME","joiningDate":"2026-01-01","baseSalaryMinor":4200000,"salaryCurrency":"INR","status":"ACTIVE"}
      """;
    String id=call(client,"POST","/employees",employee).path("id").asText();
    call(client,"PATCH","/employees/"+id,employee.replace("\"Editor\"","\"Senior Editor\""));
    assertThat(call(client,"GET","/employees/"+id,null).path("roleTitle").asText()).isEqualTo("Senior Editor");
    call(client,"PUT","/attendance/"+id+"/"+today,"{\"status\":\"ABSENT\"}");
    assertThat(call(client,"GET","/employees/"+id+"/attendance",null).path("records").get(0).path("status").asText()).isEqualTo("ABSENT");
    String leave="{\"employeeId\":\""+id+"\",\"startDate\":\""+today+"\",\"endDate\":\""+today+"\",\"leaveType\":\"Personal\",\"reason\":\"Release verification\"}";
    String approved=call(client,"POST","/leave-requests",leave).path("id").asText();
    assertThat(call(client,"POST","/leave-requests/"+approved+"/approve","{}").path("status").asText()).isEqualTo("APPROVED");
    assertThat(call(client,"GET","/employees/"+id+"/attendance",null).path("records").get(0).path("status").asText()).isEqualTo("LEAVE");
    assertThat(call(client,"GET","/employees/"+id,null).path("status").asText()).isEqualTo("ON_LEAVE");
    String rejected=call(client,"POST","/leave-requests",leave).path("id").asText();
    assertThat(call(client,"POST","/leave-requests/"+rejected+"/reject","{}").path("status").asText()).isEqualTo("REJECTED");
    assertThat(jdbc.queryForObject("select count(*) from audit_logs",Integer.class)).isGreaterThanOrEqualTo(7);
  }
  private JsonNode call(HttpClient client,String method,String path,String body) throws Exception {
    var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).header("Content-Type","application/json")
      .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
    var response=client.send(request,HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(method+" "+path+": "+response.body()).isEqualTo(200);
    return json.readTree(response.body()).path("data");
  }
  private void assertSeed() {
    assertThat(jdbc.queryForObject("select count(*) from users",Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from employees",Integer.class)).isEqualTo(12);
    assertThat(jdbc.queryForObject("select count(*) from attendance_records",Integer.class)).isEqualTo(12);
    assertThat(jdbc.queryForObject("select count(*) from leave_requests",Integer.class)).isEqualTo(1);
  }
}
