-- Übersetzungen von Titel und Teaser, auf Abruf erzeugt und hier zwischengespeichert:
-- jede Übersetzung wird nur einmal beim Anbieter angefragt.

create table article_translation (
    article_id  bigint      not null references article (id) on delete cascade,
    lang        char(2)     not null,               -- Zielsprache: de, en, es
    title       text        not null,
    teaser      text,
    source_lang text,                               -- vom Anbieter erkannte Ausgangssprache
    provider    text        not null,               -- z.B. deepl, später ein lokales LLM
    created_at  timestamptz not null default now(),
    primary key (article_id, lang)
);
