-- Übersetzte Titel für die Story-Übersicht. Getrennt von article_translation, weil dort
-- immer Titel und Teaser zusammen stehen; die Übersicht braucht nur den Titel.
-- Gehört zum Artikel, nicht zur Story: Der Story-Titel ist der Titel des Artikels nahe
-- am Centroid und kann wechseln, wenn die Story wächst.
create table title_translation (
    article_id  bigint      not null references article (id) on delete cascade,
    lang        char(2)     not null,
    title       text        not null,
    source_lang char(2),
    provider    text        not null,
    created_at  timestamptz not null default now(),
    primary key (article_id, lang)
);
