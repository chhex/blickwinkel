-- Einordnung einer Quelle: Finanzierung, politische Ausrichtung und woher diese stammt.
-- Werte kommen aus sources.yml (SourceSync); die Spalten sind nur deren Abbild.

create type source_ownership as enum ('PRIVAT', 'OEFFENTLICH', 'STAATLICH', 'PARTEIEN');

-- KEINE: Einordnung bewusst ohne Richtung (z.B. Staatsmedien, Parteigeld).
-- null:  noch nicht eingeordnet.
create type source_lean as enum ('LINKS', 'MITTE_LINKS', 'MITTE', 'MITTE_RECHTS', 'RECHTS', 'KEINE');

alter table source
    add column ownership   source_ownership not null default 'PRIVAT',
    add column lean        source_lean,
    add column lean_source text check (lean_source in ('ALLSIDES', 'EIGENE')),
    add column note        text;
