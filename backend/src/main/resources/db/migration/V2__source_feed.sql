-- Eine Quelle (Redaktion) kann mehrere Feeds haben, z.B. "World" und "Startseite".
-- Ein Artikel gehört weiterhin zur Quelle; feed_id merkt sich zusätzlich,
-- über welchen Feed er zuerst hereinkam.

create table source_feed (
    id              bigserial primary key,
    source_id       bigint  not null references source (id),
    url             text    not null unique,
    active          boolean not null default true,
    last_fetched_at timestamptz,
    last_error      text
);

create index source_feed_source_idx on source_feed (source_id);

-- Bestehende Feeds übernehmen (bisher genau einer pro Quelle)
insert into source_feed (source_id, url, active, last_fetched_at, last_error)
select id, feed_url, active, last_fetched_at, last_error
from source;

-- Herkunfts-Feed pro Artikel. Nullable: historische Artikel ohne eindeutige Zuordnung
-- sind möglich, neue Artikel bekommen immer einen Wert.
alter table article add column feed_id bigint references source_feed (id);

update article a
set feed_id = f.id
from source_feed f
where f.source_id = a.source_id;

create index article_feed_idx on article (feed_id);

-- Feed-bezogene Spalten wandern von source nach source_feed
alter table source drop column feed_url;
alter table source drop column last_fetched_at;
alter table source drop column last_error;
