package com.saproduction.command.meeting;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MeetingNoteRepository extends JpaRepository<MeetingNote,UUID>{List<MeetingNote> findAllByMeetingIdOrderByCreatedAtDesc(UUID id);}
