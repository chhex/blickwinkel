-- Wortsuche über Titel und Teaser.
--
-- Bewusst Teilwort-Suche mit Trigramm-Index statt klassischer Postgres-Volltextsuche:
--  * deutsche Komposita: "Armee" soll "Armeebudget" finden
--  * vier Sprachen gemischt: kein sprachabhängiges Stemming nötig
--  * Akzente egal: "Zurich" findet "Zürich", "Bogota" findet "Bogotá"

create extension if not exists unaccent;
create extension if not exists pg_trgm;

-- unaccent() ist nicht als IMMUTABLE deklariert und darf deshalb nicht direkt in einen
-- Index. Diese Hülle legt das Wörterbuch fest und ist damit unveränderlich.
create function blickwinkel_norm(text) returns text
    language sql immutable parallel safe strict
    as $$ select lower(public.unaccent('public.unaccent'::regdictionary, $1)) $$;

-- Derselbe Ausdruck muss in der Abfrage stehen, damit der Index greift.
create index article_search_idx on article
    using gin (blickwinkel_norm(title || ' ' || coalesce(teaser, '')) gin_trgm_ops);
