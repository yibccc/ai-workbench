package com.aiworkbench.service.impl;

import com.aiworkbench.dto.focus.FocusModels.*;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.dto.record.WorkRecordResponse;
import com.aiworkbench.dto.task.TaskResponse;
import com.aiworkbench.mapper.FocusStore;
import com.aiworkbench.mapper.TaskMapper;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.service.FocusService;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.service.WorkRecordService;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FocusServiceImpl implements FocusService {
    private static final long BREAK_MS=15_000;
    private final FocusStore store;
    private final TaskMapper tasks;
    private final ProjectService projects;
    private final WorkRecordService records;
    private final WorkbenchEventHub events;
    private final ZoneId zone;
    private final Clock clock;
    private final boolean writeEnabled;

    @Autowired
    public FocusServiceImpl(FocusStore store,TaskMapper tasks,ProjectService projects,WorkRecordService records,WorkbenchEventHub events,
                            @Value("${workbench.zone-id:Asia/Shanghai}") String zone,
                            @Value("${workbench.focus.write-enabled:false}") boolean writeEnabled) {
        this(store,tasks,projects,records,events,ZoneId.of(zone),Clock.systemUTC(),writeEnabled);
    }
    FocusServiceImpl(FocusStore store,TaskMapper tasks,ProjectService projects,WorkRecordService records,WorkbenchEventHub events,
                     ZoneId zone,Clock clock,boolean writeEnabled) {
        this.store=store;this.tasks=tasks;this.projects=projects;this.records=records;this.events=events;this.zone=zone;this.clock=clock;
        this.writeEnabled=writeEnabled;
    }
    @Override public Capabilities capabilities(){return new Capabilities(writeEnabled);}
    private void requireWritable(){if(!writeEnabled)throw conflict("专注写入尚未开放");}
    private void changed(String kind,UUID id,String state){events.publishAfterCommit(owner(),kind,id,state);}
    private UUID owner() { return CurrentUser.requireId(); }
    private Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    private Instant logicalNow(State x) { Instant current=now();return current.isBefore(x.anchor)?x.anchor:current; }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT,text); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"专注资源不存在"); }
    private Routine requireRoutine(UUID id) { var r=store.routine(owner(),id);if(r==null)throw missing();return r.toResponse(); }
    private Session requireSession(UUID id,boolean lock) { Session s=store.session(owner(),id,lock);if(s==null)throw missing();return s; }
    private void version(long actual,Long given) {
        if (given == null) throw bad("缺少版本号");
        if (actual != given) throw conflict("状态已更新，请刷新后重试");
    }
    private String weekdays(List<Integer> values) {
        if(values==null||values.isEmpty()||values.size()>7||values.stream().anyMatch(v->v==null||v<1||v>7))throw bad("重复星期无效");
        return String.join(",",new TreeSet<>(values).stream().map(String::valueOf).toList());
    }
    @Override @Transactional(readOnly=true) public List<Routine> routines(){return store.routines(owner()).stream().map(FocusStore.RoutineRow::toResponse).toList();}
    @Override @Transactional public Routine createRoutine(SaveRoutine request){
        requireWritable();
        if(request.projectId()!=null)projects.requireActive(request.projectId());
        UUID id=UUID.randomUUID();
        store.insertRoutine(owner(),id,request.title().trim(),request.projectId(),weekdays(request.weekdays()),request.defaultDurationMinutes());
        changed("FOCUS_ROUTINE",id,"CREATED");
        return requireRoutine(id);
    }
    @Override @Transactional public Routine updateRoutine(UUID id,SaveRoutine request){
        requireWritable();
        Routine old=requireRoutine(id);version(old.version(),request.version());
        if(request.projectId()!=null&&!request.projectId().equals(old.projectId()))projects.requireActive(request.projectId());
        int n=store.updateRoutine(owner(),id,request.title().trim(),request.projectId(),weekdays(request.weekdays()),
                request.defaultDurationMinutes(),request.enabled()==null?old.enabled():request.enabled(),now(),request.version());
        if(n!=1)throw conflict("重复规则已更新");changed("FOCUS_ROUTINE",id,"UPDATED");return requireRoutine(id);
    }
    @Override @Transactional public Routine enableRoutine(UUID id,Version request,boolean enabled){
        requireWritable();
        Routine old=requireRoutine(id);version(old.version(),request.version());
        int n=store.toggleRoutine(owner(),id,enabled,now(),request.version());
        if(n!=1)throw conflict("重复规则已更新");changed("FOCUS_ROUTINE",id,enabled?"ENABLED":"DISABLED");return requireRoutine(id);
    }
    @Override @Transactional public FillToday fillToday(){
        requireWritable();
        UUID user=owner();LocalDate date=LocalDate.now(clock.withZone(zone));
        List<TaskResponse> created=new ArrayList<>();List<Blocked> blocked=new ArrayList<>();
        for(Routine r:store.routines(user).stream().map(FocusStore.RoutineRow::toResponse).toList()){
            if(!r.enabled()||!r.weekdays().contains(date.getDayOfWeek().getValue()))continue;
            if(r.projectId()!=null){
                try{projects.requireActive(r.projectId());}
                catch(ResponseStatusException ex){blocked.add(new Blocked(r.id(),"项目已归档或不可用"));continue;}
            }
            UUID id=UUID.randomUUID();
            int n=store.insertOccurrence(id,user,r.projectId(),r.title(),r.id(),date,r.defaultDurationMinutes());
            if(n==1){created.add(tasks.findById(user,id).orElseThrow().toResponse());changed("TASK",id,"CREATED");}
        }
        return new FillToday(date,created,blocked);
    }
    @Override @Transactional(readOnly=true) public Session current(){return store.current(owner());}
    @Override @Transactional(readOnly=true) public Session get(UUID id){return requireSession(id,false);}
    @Override @Transactional public Session start(Start request){
        requireWritable();
        UUID user=owner();Session prior=store.byRequest(user,request.requestId());if(prior!=null)return prior;
        if(store.current(user)!=null)throw conflict("已有未结束的专注会话");
        UUID projectId=request.projectId();
        if(request.taskId()!=null){
            var task=tasks.findById(user,request.taskId()).orElseThrow(this::missing);
            if(projectId!=null&&!projectId.equals(task.projectId()))throw bad("待办与项目不一致");
            projectId=task.projectId();
        }
        if(projectId!=null)projects.requireActive(projectId);
        Instant t=now();UUID id=UUID.randomUUID();long interval=(request.intervalMinutes()==null?10L:request.intervalMinutes())*60_000;
        int inserted=store.insertSession(id,user,request.requestId(),request.taskId(),projectId,request.title().trim(),
                request.targetMinutes()*60_000L,interval,zone.getId(),t);
        if(inserted==0){Session duplicate=store.byRequest(user,request.requestId());
            if(duplicate!=null)return duplicate;
            throw conflict("已有未结束的专注会话");}
        changed("FOCUS_SESSION",id,"RUNNING");
        return requireSession(id,false);
    }

    private static final class State {
        final Session source;String phase,resumePhase;long focus,rest,pause,next,breakRemaining,controllerGeneration;
        int ordinal;boolean dismissed;Instant anchor,ended,controllerExpires;UUID controllerId;
        State(Session s){source=s;phase=s.phase();resumePhase=s.resumePhase();focus=s.focusMs();rest=s.breakMs();pause=s.pauseMs();
            next=s.nextBreakAtMs();breakRemaining=s.breakRemainingMs();ordinal=s.reminderOrdinal();dismissed=s.remindersDismissed();
            anchor=s.anchorAt();ended=s.endedAt();
            controllerId=s.controllerId();controllerGeneration=s.controllerGeneration();controllerExpires=s.controllerExpiresAt();}
        Session snapshot(){return new Session(source.id(),source.requestId(),source.title(),source.taskId(),source.projectId(),
                source.targetMs(),source.intervalMs(),source.zoneId(),phase,source.version(),source.startedAt(),anchor,ended,
                focus,rest,pause,resumePhase,breakRemaining,next,dismissed,ordinal,
                controllerId,controllerGeneration,controllerExpires,source.progress());}
    }
    private void save(State x,Instant t){
        int n=store.updateSession(owner(),x.snapshot(),t);
        if(n!=1)throw conflict("状态已更新，请刷新后重试");
        changed("FOCUS_SESSION",x.source.id(),x.phase);
    }
    private void credit(State x,Instant from,Instant to){
        if(!to.isAfter(from))return;
        Instant cursor=from;
        while(cursor.isBefore(to)&&!"ENDED".equals(x.phase)){
            if("RUNNING".equals(x.phase)){
                long remaining=x.source.targetMs()-x.focus;
                if(remaining<=0){x.phase="ENDED";x.ended=cursor;break;}
                long ms=Math.min(Duration.between(cursor,to).toMillis(),remaining);
                if(ms<=0)break;
                Instant end=cursor.plusMillis(ms);store.insertInterval(owner(),x.source.id(),"FOCUS",cursor,end,true);x.focus+=ms;cursor=end;
                if(x.focus>=x.source.targetMs()){x.phase="ENDED";x.ended=cursor;break;}
            } else if("MICRO_BREAK".equals(x.phase)){
                long ms=Math.min(Duration.between(cursor,to).toMillis(),x.breakRemaining);
                if(ms<=0)break;
                Instant end=cursor.plusMillis(ms);store.insertInterval(owner(),x.source.id(),"BREAK",cursor,end,true);x.rest+=ms;
                x.breakRemaining-=ms;cursor=end;
                if(x.breakRemaining==0){x.phase="RUNNING";advanceNextBreak(x);}
            } else if("PAUSED".equals(x.phase)){
                store.insertInterval(owner(),x.source.id(),"PAUSE",cursor,to,true);x.pause+=Duration.between(cursor,to).toMillis();cursor=to;
            } else break;
        }
        x.anchor=to;
    }
    private void advanceNextBreak(State x){
        while(x.next<=x.focus)x.next+=x.source.intervalMs();
    }
    private boolean advance(State x,Instant t){
        if("ENDED".equals(x.phase))return false;
        if(!t.isAfter(x.anchor))return false;
        credit(x,x.anchor,t);return true;
    }
    private void lease(State x,Checkpoint request,Instant t){
        if(request.controllerId()==null)return;
        if(x.controllerId==null||x.controllerExpires==null||!x.controllerExpires.isAfter(t)){
            x.controllerId=request.controllerId();x.controllerGeneration++;x.controllerExpires=t.plusSeconds(60);
        }else if(x.controllerId.equals(request.controllerId())&&Objects.equals(request.controllerGeneration(),x.controllerGeneration)){
            x.controllerExpires=t.plusSeconds(60);
        }
    }
    @Override @Transactional public Session checkpoint(UUID id,Checkpoint request){
        Session s=requireSession(id,true);version(s.version(),request.version());State x=new State(s);Instant t=logicalNow(x);
        if(!"ENDED".equals(x.phase)){advance(x,t);lease(x,request,t);save(x,t);if("ENDED".equals(x.phase))settle(x);}
        return requireSession(id,false);
    }
    @Override @Transactional public Session transition(UUID id,Transition request){
        Session s=requireSession(id,true);version(s.version(),request.version());State x=new State(s);Instant t=logicalNow(x);
        if("ENDED".equals(x.phase))return s;
        advance(x,t);
        if("ENDED".equals(x.phase)){save(x,t);settle(x);return requireSession(id,false);}
        switch(request.action()){
            case PAUSE -> {if(!x.phase.equals("RUNNING")&&!x.phase.equals("MICRO_BREAK"))throw conflict("当前不能暂停");
                x.resumePhase=x.phase;x.phase="PAUSED";}
            case RESUME -> {if(!x.phase.equals("PAUSED"))throw conflict("当前未暂停");x.phase=x.resumePhase;x.resumePhase=null;}
            case BREAK_DUE -> {
                if(!x.phase.equals("RUNNING")||x.dismissed||x.focus<x.next||x.focus>=s.targetMs())
                    throw conflict("休息尚未到期");
                x.phase="MICRO_BREAK";x.breakRemaining=BREAK_MS;x.ordinal++;
            }
            case BREAK_DONE -> {
                if(!s.phase().equals("MICRO_BREAK")||!x.phase.equals("RUNNING"))throw conflict("休息尚未结束");
            }
            case SKIP_BREAK -> {
                if(!s.phase().equals("MICRO_BREAK"))throw conflict("当前不是休息阶段");
                if(x.phase.equals("MICRO_BREAK")){x.phase="RUNNING";x.breakRemaining=0;advanceNextBreak(x);}
            }
            case DISMISS_REMINDERS -> {x.dismissed=true;if(x.phase.equals("MICRO_BREAK")){x.phase="RUNNING";x.breakRemaining=0;advanceNextBreak(x);}}
        }
        x.anchor=t;save(x,t);return requireSession(id,false);
    }
    @Override @Transactional public Session end(UUID id,Version request){
        Session s=requireSession(id,true);if(s.phase().equals("ENDED"))return s;version(s.version(),request.version());
        State x=new State(s);Instant t=logicalNow(x);
        advance(x,t);
        if(!x.phase.equals("ENDED")){x.phase="ENDED";x.ended=t;x.anchor=t;}
        save(x,t);settle(x);return requireSession(id,false);
    }
    private void settle(State x){
        Session s=x.source;Map<LocalDate,DaySlice> byDay=new TreeMap<>();
        for(FocusStore.Segment segment:store.segments(owner(),s.id())){
            Instant cursor=segment.start();
            while(cursor.isBefore(segment.end())){
                LocalDate day=cursor.atZone(ZoneId.of(s.zoneId())).toLocalDate();
                Instant boundary=day.plusDays(1).atStartOfDay(ZoneId.of(s.zoneId())).toInstant();
                Instant end=boundary.isBefore(segment.end())?boundary:segment.end();long ms=Duration.between(cursor,end).toMillis();
                DaySlice sums=byDay.computeIfAbsent(day,d->new DaySlice());
                if(segment.focusMs()>0)sums.focus+=ms;else sums.rest+=ms;
                if(sums.start==null||cursor.isBefore(sums.start))sums.start=cursor;
                if(sums.end==null||end.isAfter(sums.end))sums.end=end;
                cursor=end;
            }
        }
        for(var entry:byDay.entrySet()){
            DaySlice slice=entry.getValue();long focus=slice.focus,rest=slice.rest;if(focus==0)continue;
            LocalDate day=entry.getKey();Instant start=slice.start,end=slice.end;
            store.insertFocusRecord(UUID.randomUUID(),owner(),s.projectId(),s.taskId(),s.id(),
                    "专注投入："+s.title(),day,focus,rest,start,end);
        }
    }
    private static final class DaySlice { long focus,rest;Instant start,end; }
    @Override @Transactional public Session progress(UUID id,Progress request){
        Session s=requireSession(id,true);if(!s.phase().equals("ENDED"))throw conflict("结束后才能补充进展");version(s.version(),request.version());
        String value=request.progress().trim();Instant t=now();
        int n=store.updateProgress(owner(),id,value,t,request.version());if(n!=1)throw conflict("进展已更新");
        store.updateRecordProgress(owner(),id,value,t);changed("FOCUS_SESSION",id,"PROGRESS_UPDATED");
        return requireSession(id,false);
    }
    @Override @Transactional(readOnly=true) public Today today(){
        LocalDate date=LocalDate.now(clock.withZone(zone));var totals=store.todayTotals(owner(),date);
        List<WorkRecordResponse> rows=records.list(date).stream().filter(r->r.sessionId()!=null).toList();
        return new Today(date,totals.focusMs(),totals.breakMs(),totals.sessionCount(),rows);
    }
}
