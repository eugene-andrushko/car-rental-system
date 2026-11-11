alter table car_availability add column reserved_cars int default 0 not null;

update car_availability
set reserved_cars = (select car_type.car_count from car_type where car_type.id = car_availability.car_type_id) - available_cars;

alter table car_availability alter column reserved_cars drop default;
alter table car_availability drop column available_cars;
alter table car_availability add constraint car_availability_reserved_cars_not_negative check (reserved_cars >= 0);
