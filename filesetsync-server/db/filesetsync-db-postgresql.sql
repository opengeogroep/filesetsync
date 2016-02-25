set session authorization filesetsync;
create table client_startup(client_id varchar, machine_id varchar, start_time timestamp, report json, primary key(client_id, machine_id, start_time));

create table client_state(client_id varchar,  machine_id varchar, report_time timestamp, ip varchar,current_state json, primary key(client_id, machine_id));
create or replace view v_client_state as 
select *, (current_state->>'mode')::varchar as mode, (current_state->>'mode_details')::varchar as mode_details, to_timestamp((current_state->>'mode_since')::bigint) as mode_since
from client_state;

create table client_job_log(client_id varchar, machine_id varchar, job varchar, start_time timestamp, state varchar, summary varchar, log json, primary key(client_id, machine_id, job, start_time));

