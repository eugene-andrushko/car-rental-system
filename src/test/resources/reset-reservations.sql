delete from reservation;

update car_availability
set free_cars = (select car_type.car_count from car_type where car_type.id = car_availability.car_type_id);
