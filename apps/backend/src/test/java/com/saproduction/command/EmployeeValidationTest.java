package com.saproduction.command;

import static org.assertj.core.api.Assertions.assertThat;
import com.saproduction.command.employee.*;
import jakarta.validation.Validation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EmployeeValidationTest {
  private final jakarta.validation.Validator validator=Validation.buildDefaultValidatorFactory().getValidator();
  @Test void rejectsNegativeMinorUnitSalaryAndInvalidPhone(){
    var input=new EmployeeDtos.Input("SA-900","Test",null,"Test Person","bad",null,null,"Editor","Post Production","FULL_TIME",LocalDate.now(),-1,"INR",Employee.Status.ACTIVE,null,null);
    var fields=validator.validate(input).stream().map(v->v.getPropertyPath().toString()).toList();
    assertThat(fields).contains("phone","baseSalaryMinor");
  }
  @Test void acceptsIntegerMinorUnitSalary(){
    var input=new EmployeeDtos.Input("SA-901","Test",null,"Test Person","+91 90000 00000","+91 90000 00000","test@sa.local","Editor","Post Production","FULL_TIME",LocalDate.now(),4200000,"INR",Employee.Status.ACTIVE,null,null);
    assertThat(validator.validate(input)).isEmpty();
  }
}

