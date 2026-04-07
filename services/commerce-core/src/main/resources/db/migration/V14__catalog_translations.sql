-- i18n: products and categories carry a translations JSONB column so the
-- frontend (PR 3d/3e) can flip locale via next-intl without a separate
-- content rewrite. Default to an empty JSON object so existing rows stay
-- valid. Per locale shape: { "ja": { "name": "...", "description": "..." },
--                            "en": { "name": "...", "description": "..." } }
-- Asoview is JP-first; en is opt-in.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS translations JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS translations JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS products_translations_gin ON products USING gin (translations);
CREATE INDEX IF NOT EXISTS categories_translations_gin ON categories USING gin (translations);
