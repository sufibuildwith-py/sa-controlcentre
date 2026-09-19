package com.saproduction.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker=true)
class PostgresMigrationTest {
  @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
  @BeforeAll static void migrate(){Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").load().migrate();}
  @Test void attendanceIsUniquePerEmployeeAndDate() throws Exception {
    try(Connection c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())){
      UUIDs ids=insertOwnerAndEmployee(c);insertAttendance(c,ids.employee());
      assertThatThrownBy(()->insertAttendance(c,ids.employee())).isInstanceOf(SQLException.class);
    }
  }
  private UUIDs insertOwnerAndEmployee(Connection c)throws Exception{String user=java.util.UUID.randomUUID().toString(),employee=java.util.UUID.randomUUID().toString();try(PreparedStatement p=c.prepareStatement("insert into users(id,email,password_hash,display_name,role) values (?::uuid,'test@sa.local','hash','Owner','OWNER')")){p.setString(1,user);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("insert into employees(id,employee_code,first_name,display_name,phone,role_title,department,employment_type,joining_date,base_salary_minor,salary_currency,status) values (?::uuid,'SA-T1','Test','Test','+919000000000','Editor','Post','FULL_TIME',current_date,4200000,'INR','ACTIVE')")){p.setString(1,employee);p.executeUpdate();}return new UUIDs(user,employee);}
  private void insertAttendance(Connection c,String employee)throws Exception{try(PreparedStatement p=c.prepareStatement("insert into attendance_records(employee_id,attendance_date,status) values (?::uuid,current_date,'PRESENT')")){p.setString(1,employee);p.executeUpdate();}}
  record UUIDs(String owner,String employee){}
}

