alter table car_type add constraint car_type_name_normalized check (name = upper(trim(name)));
