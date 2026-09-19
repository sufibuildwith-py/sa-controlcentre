package com.saproduction.command.meeting;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MeetingRepository extends JpaRepository<Meeting,UUID>{List<Meeting> findAllByOrderByStartsAtDesc();}
