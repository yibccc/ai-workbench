package com.aiworkbench.controller;

import com.aiworkbench.dto.focus.FocusModels.*;
import com.aiworkbench.service.FocusService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/focus")
public class FocusController {
    private final FocusService service;
    public FocusController(FocusService service) { this.service=service; }
    @GetMapping("/routines") public List<Routine> routines() { return service.routines(); }
    @PostMapping("/routines") @ResponseStatus(HttpStatus.CREATED)
    public Routine createRoutine(@Valid @RequestBody SaveRoutine request) { return service.createRoutine(request); }
    @PutMapping("/routines/{id}")
    public Routine updateRoutine(@PathVariable UUID id,@Valid @RequestBody SaveRoutine request) { return service.updateRoutine(id,request); }
    @PostMapping("/routines/{id}/enable")
    public Routine enable(@PathVariable UUID id,@Valid @RequestBody Version request) { return service.enableRoutine(id,request,true); }
    @PostMapping("/routines/{id}/disable")
    public Routine disable(@PathVariable UUID id,@Valid @RequestBody Version request) { return service.enableRoutine(id,request,false); }
    @PostMapping("/routines/fill-today") public FillToday fillToday() { return service.fillToday(); }
    @GetMapping("/current") public Session current() { return service.current(); }
    @GetMapping("/today") public Today today() { return service.today(); }
    @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED)
    public Session start(@Valid @RequestBody Start request) { return service.start(request); }
    @GetMapping("/sessions/{id}") public Session get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping("/sessions/{id}/checkpoint")
    public Session checkpoint(@PathVariable UUID id,@Valid @RequestBody Checkpoint request) { return service.checkpoint(id,request); }
    @PostMapping("/sessions/{id}/transition")
    public Session transition(@PathVariable UUID id,@Valid @RequestBody Transition request) { return service.transition(id,request); }
    @PostMapping("/sessions/{id}/end")
    public Session end(@PathVariable UUID id,@Valid @RequestBody Version request) { return service.end(id,request); }
    @PutMapping("/sessions/{id}/progress")
    public Session progress(@PathVariable UUID id,@Valid @RequestBody Progress request) { return service.progress(id,request); }
}
