alter table reservation rename column starts_at to pick_up_at;
alter table reservation rename column ends_at to return_at;
update reservation set status = 'CONFIRMED' where status = 'ACTIVE';
alter table car_availability rename column free_cars to available_cars;
