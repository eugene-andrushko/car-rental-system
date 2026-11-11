delete from reservation;

update car_type
set car_count = case name when 'SEDAN' then 3 when 'SUV' then 2 when 'VAN' then 1 end;

update car_availability
set reserved_cars = 0;
