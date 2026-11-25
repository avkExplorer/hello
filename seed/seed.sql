-- Synthetic data for stg only (loaded by `avk seed hello`). Safe to run more than once.
insert into greeting (message)
select 'hello from the stg seed' where not exists (select 1 from greeting where message = 'hello from the stg seed');
