import java.sql.*;
String host=System.getenv().getOrDefault("POSTGRES_HOST","localhost");
String port=System.getenv().getOrDefault("POSTGRES_PORT","5432");
String db=System.getenv().getOrDefault("POSTGRES_DB","ai_workbench");
String user=System.getenv().getOrDefault("POSTGRES_USER","ai_workbench");
String pass=System.getenv().getOrDefault("POSTGRES_PASSWORD","local_dev_only");
Connection connection=DriverManager.getConnection("jdbc:postgresql://"+host+":"+port+"/"+db,user,pass);
Statement statement=connection.createStatement();
ResultSet projects=statement.executeQuery("SELECT p.id,p.name,p.status,p.created_at,(SELECT count(*) FROM todo_items t WHERE t.project_id=p.id) task_refs,(SELECT count(*) FROM work_records w WHERE w.project_id=p.id) record_refs,(SELECT count(*) FROM report_sources rs WHERE rs.project_id=p.id) snapshot_refs FROM projects p ORDER BY p.created_at,p.id");
while(projects.next()) System.out.println("PROJECT|"+projects.getString(1)+"|"+projects.getString(2)+"|"+projects.getString(3)+"|"+projects.getTimestamp(4)+"|tasks="+projects.getLong(5)+"|records="+projects.getLong(6)+"|snapshots="+projects.getLong(7));
ResultSet tasks=statement.executeQuery("SELECT t.id,t.title,t.status,t.created_at,t.capture_input_id,ci.client_request_id,t.deleted_at,t.version,(SELECT count(*) FROM task_events e WHERE e.todo_id=t.id) events,(SELECT count(*) FROM work_records w WHERE w.todo_id=t.id) records,(SELECT count(*) FROM report_sources rs WHERE rs.entity_id=t.id) snapshots FROM todo_items t LEFT JOIN capture_inputs ci ON ci.id=t.capture_input_id ORDER BY t.created_at,t.id");
while(tasks.next()) System.out.println("TASK|"+tasks.getString(1)+"|"+tasks.getString(2)+"|"+tasks.getString(3)+"|"+tasks.getTimestamp(4)+"|capture="+tasks.getString(5)+"|request="+tasks.getString(6)+"|deleted="+tasks.getTimestamp(7)+"|version="+tasks.getLong(8)+"|events="+tasks.getLong(9)+"|records="+tasks.getLong(10)+"|snapshots="+tasks.getLong(11));
connection.close();
/exit
