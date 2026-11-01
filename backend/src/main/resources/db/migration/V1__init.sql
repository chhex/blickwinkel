-- Blickwinkel: Quellen, Artikel, Stories (Cluster)

create extension if not exists vector;

create type source_section as enum ('NEWS', 'OPINION');
create type article_status as enum ('NEW', 'EMBEDDED', 'CLUSTERED', 'FAILED');

create table source (
    id              bigserial primary key,
    code            text           not null unique,
    name            text           not null,
    country         char(2)        not null,
    language        char(2)        not null,
    section         source_section not null default 'NEWS',
    feed_url        text           not null unique,
    camp            text,                       -- eigenes Lager-Tag, bewusst frei
    active          boolean        not null default true,
    last_fetched_at timestamptz,
    last_error      text
);

-- Eine Story = ein Ereignis, gebündelt über Quellen hinweg.
-- Dimension muss zum Embedding-Modell passen (bge-m3: 1024).
create table story (
    id            bigserial primary key,
    centroid      vector(1024) not null,
    article_count integer      not null,
    first_seen_at timestamptz  not null,
    last_seen_at  timestamptz  not null
);

create index story_last_seen_idx on story (last_seen_at);

create table article (
    id           bigserial primary key,
    source_id    bigint         not null references source (id),
    story_id     bigint         references story (id),
    url          text           not null,
    url_hash     char(64)       not null unique,  -- SHA-256 der normalisierten URL
    title        text           not null,
    teaser       text,
    published_at timestamptz,
    fetched_at   timestamptz    not null default now(),
    language     char(2)        not null,
    embedding    vector(1024),
    similarity   real,                            -- Cosine-Ähnlichkeit zum Story-Centroid beim Zuordnen
    status       article_status not null default 'NEW',
    error        text
);

create index article_status_idx on article (status);
create index article_story_idx on article (story_id);
create index article_published_idx on article (published_at);

-- Bewusst KEIN HNSW-Index auf story.centroid:
-- Das Zeitfenster (48h) hält die Kandidaten klein, eine exakte Suche ist
-- schnell genug und vor allem deterministisch (HNSW ist approximativ und
-- filtert bei WHERE-Bedingungen erst nach der Suche).
