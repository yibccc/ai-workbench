import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
String host=System.getenv().getOrDefault("POSTGRES_HOST","localhost");
String port=System.getenv().getOrDefault("POSTGRES_PORT","5432");
String db=System.getenv().getOrDefault("POSTGRES_DB","ai_workbench");
String user=System.getenv().getOrDefault("POSTGRES_USER","ai_workbench");
String pass=System.getenv().getOrDefault("POSTGRES_PASSWORD","local_dev_only");
Connection connection=DriverManager.getConnection("jdbc:postgresql://"+host+":"+port+"/"+db,user,pass);
connection.setAutoCommit(false);
connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
String projectPredicate="p.name LIKE 'AI捕获项目-%' AND p.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM todo_items t LEFT JOIN capture_inputs ci ON ci.id=t.capture_input_id WHERE t.project_id=p.id AND (ci.client_request_id IS NULL OR ci.client_request_id NOT LIKE 'mixed-%' OR t.version<>0 OR t.deleted_at IS NOT NULL)) AND NOT EXISTS (SELECT 1 FROM work_records w LEFT JOIN capture_inputs ci ON ci.id=w.capture_input_id WHERE w.project_id=p.id AND (ci.client_request_id IS NULL OR ci.client_request_id NOT LIKE 'mixed-%'))";
String taskPredicate="t.deleted_at IS NULL AND t.version=0 AND (ci.client_request_id LIKE 'mixed-%' OR ci.client_request_id LIKE 'task-only-%') AND NOT EXISTS (SELECT 1 FROM task_events e WHERE e.todo_id=t.id) AND NOT EXISTS (SELECT 1 FROM work_records w WHERE w.todo_id=t.id)";
long projectCount; try(var q=connection.createStatement().executeQuery("SELECT count(*) FROM projects p WHERE "+projectPredicate)){q.next();projectCount=q.getLong(1);}
long taskCount; try(var q=connection.createStatement().executeQuery("SELECT count(*) FROM todo_items t JOIN capture_inputs ci ON ci.id=t.capture_input_id WHERE "+taskPredicate)){q.next();taskCount=q.getLong(1);}
int projectLimit=(int)(projectCount/2), taskLimit=(int)(taskCount/2);
List<String> projectIds=new ArrayList<>(), taskIds=new ArrayList<>(), backup=new ArrayList<>();
backup.add("D9 confirmed test-data backup; restore inside one transaction after verifying IDs.");
try(var ps=connection.prepareStatement("SELECT p.id,p.name,p.created_at,p.updated_at FROM projects p WHERE "+projectPredicate+" ORDER BY p.created_at,p.id LIMIT ?")){ps.setInt(1,projectLimit);try(var q=ps.executeQuery()){while(q.next()){String id=q.getString(1);projectIds.add(id);backup.add("PROJECT|"+id+"|"+q.getString(2)+"|created="+q.getTimestamp(3)+"|restore=UPDATE projects SET status='ACTIVE',archived_at=NULL,updated_at='"+q.getTimestamp(4)+"' WHERE id='"+id+"' AND status='ARCHIVED';");}}}
try(var ps=connection.prepareStatement("SELECT t.id,t.title,t.created_at,t.updated_at,ci.client_request_id FROM todo_items t JOIN capture_inputs ci ON ci.id=t.capture_input_id WHERE "+taskPredicate+" ORDER BY t.created_at,t.id LIMIT ?")){ps.setInt(1,taskLimit);try(var q=ps.executeQuery()){while(q.next()){String id=q.getString(1);taskIds.add(id);backup.add("TASK|"+id+"|"+q.getString(2)+"|created="+q.getTimestamp(3)+"|request="+q.getString(5)+"|restore=UPDATE todo_items SET deleted_at=NULL,version=0,updated_at='"+q.getTimestamp(4)+"' WHERE id='"+id+"' AND version=1 AND deleted_at IS NOT NULL;");}}}
Path backupPath=Path.of(".local-backups","d9-test-cleanup-20260920.txt");Files.createDirectories(backupPath.getParent());Files.write(backupPath,backup,StandardCharsets.UTF_8);
int archived=0; try(var ps=connection.prepareStatement("UPDATE projects SET status='ARCHIVED',archived_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=CAST(? AS uuid) AND status='ACTIVE'")){for(String id:projectIds){ps.setString(1,id);archived+=ps.executeUpdate();}}
int deleted=0; try(var ps=connection.prepareStatement("UPDATE todo_items SET deleted_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=CAST(? AS uuid) AND version=0 AND deleted_at IS NULL")){for(String id:taskIds){ps.setString(1,id);deleted+=ps.executeUpdate();}}
if(archived!=projectLimit||deleted!=taskLimit){connection.rollback();throw new IllegalStateException("conditional cleanup count changed; rolled back");}
connection.commit();connection.close();
System.out.println("confirmedProjects="+projectCount+",archived="+archived+",confirmedTasks="+taskCount+",softDeleted="+deleted+",backup="+backupPath);
/exit
