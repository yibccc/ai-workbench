import java.sql.*;
String host=System.getenv().getOrDefault("POSTGRES_HOST","localhost"),port=System.getenv().getOrDefault("POSTGRES_PORT","5432"),db=System.getenv().getOrDefault("POSTGRES_DB","ai_workbench"),user=System.getenv().getOrDefault("POSTGRES_USER","ai_workbench"),pass=System.getenv().getOrDefault("POSTGRES_PASSWORD","local_dev_only");
Connection c=DriverManager.getConnection("jdbc:postgresql://"+host+":"+port+"/"+db,user,pass);Statement s=c.createStatement();
ResultSet q=s.executeQuery("SELECT count(*) FILTER (WHERE status='ARCHIVED'),count(*) FILTER (WHERE status='ACTIVE') FROM projects WHERE name ~ '.*-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'");q.next();System.out.println("testProjectsArchived="+q.getLong(1)+",active="+q.getLong(2));
q=s.executeQuery("SELECT count(*) FILTER (WHERE t.deleted_at IS NOT NULL),count(*) FILTER (WHERE t.deleted_at IS NULL) FROM todo_items t JOIN capture_inputs ci ON ci.id=t.capture_input_id WHERE ci.client_request_id LIKE 'mixed-%' OR ci.client_request_id LIKE 'task-only-%'");q.next();System.out.println("testTasksSoftDeleted="+q.getLong(1)+",active="+q.getLong(2));c.close();
/exit
