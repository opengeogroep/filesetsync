set session authorization filesetsync;
create table client_startup(client_id varchar, start_time timestamp, report json, primary key(client_id, start_time));

create table client_state(client_id varchar, report_time timestamp, ip varchar,current_state json, primary key(client_id));


