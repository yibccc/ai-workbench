package com.aiworkbench.service;

import com.aiworkbench.dto.focus.FocusModels.*;
import java.util.List;
import java.util.UUID;

public interface FocusService {
    Capabilities capabilities();
    List<Routine> routines();
    Routine createRoutine(SaveRoutine request);
    Routine updateRoutine(UUID id, SaveRoutine request);
    Routine enableRoutine(UUID id, Version request, boolean enabled);
    FillToday fillToday();
    Session current();
    Session get(UUID id);
    Session start(Start request);
    Session checkpoint(UUID id, Checkpoint request);
    Session transition(UUID id, Transition request);
    Session end(UUID id, Version request);
    Session progress(UUID id, Progress request);
    Today today();
}
