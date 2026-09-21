import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
String host=System.getenv().getOrDefault("POSTGRES_HOST","localhost"),port=System.getenv().getOrDefault("POSTGRES_PORT","5432"),db=System.getenv().getOrDefault("POSTGRES_DB","ai_workbench"),user=System.getenv().getOrDefault("POSTGRES_USER","ai_workbench"),pass=System.getenv().getOrDefault("POSTGRES_PASSWORD","local_dev_only");
Connection connection=DriverManager.getConnection("jdbc:postgresql://"+host+":"+port+"/"+db,user,pass);connection.setAutoCommit(false);connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
String uuidSuffix=".*-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
String predicate="p.status='ACTIVE' AND p.name ~ '"+uuidSuffix+"' AND NOT EXISTS (SELECT 1 FROM todo_items t LEFT JOIN capture_inputs ci ON ci.id=t.capture_input_id WHERE t.project_id=p.id AND (ci.client_request_id IS NULL OR ci.client_request_id NOT LIKE 'mixed-%')) AND NOT EXISTS (SELECT 1 FROM work_records w LEFT JOIN capture_inputs ci ON ci.id=w.capture_input_id WHERE w.project_id=p.id AND (ci.client_request_id IS NULL OR ci.client_request_id NOT LIKE 'mixed-%'))";
long count;try(var q=connection.createStatement().executeQuery("SELECT count(*) FROM projects p WHERE "+predicate)){q.next();count=q.getLong(1);}int limit=(int)(count/2);
List<String> ids=new ArrayList<>(),backup=new ArrayList<>();backup.add("D9 confirmed integration-test project backup; restore after verifying IDs.");
try(var ps=connection.prepareStatement("SELECT p.id,p.name,p.created_at,p.updated_at FROM projects p WHERE "+predicate+" ORDER BY p.created_at,p.id LIMIT ?")){ps.setInt(1,limit);try(var q=ps.executeQuery()){while(q.next()){String id=q.getString(1);ids.add(id);backup.add("PROJECT|"+id+"|"+q.getString(2)+"|created="+q.getTimestamp(3)+"|restore=UPDATE projects SET status='ACTIVE',archived_at=NULL,updated_at='"+q.getTimestamp(4)+"' WHERE id='"+id+"' AND status='ARCHIVED';");}}}
Path path=Path.of(".local-backups","d9-test-project-cleanup-20260920.txt");Files.createDirectories(path.getParent());Files.write(path,backup,StandardCharsets.UTF_8);
int changed=0;try(var ps=connection.prepareStatement("UPDATE projects SET status='ARCHIVED',archived_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=CAST(? AS uuid) AND status='ACTIVE'")){for(String id:ids){ps.setString(1,id);changed+=ps.executeUpdate();}}
if(changed!=limit){connection.rollback();throw new IllegalStateException("project cleanup candidates changed; rolled back");}connection.commit();connection.close();System.out.println("confirmedProjects="+count+",archived="+changed+",backup="+path);
/exit
