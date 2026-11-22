-- PostgreSQL에서 애플리케이션 배포 전에 실행합니다.
BEGIN;

ALTER TABLE categories ADD COLUMN IF NOT EXISTS display_order integer;

WITH ranked AS (
    SELECT id, row_number() OVER (ORDER BY id)::integer AS position
    FROM categories
)
UPDATE categories AS category
SET display_order = ranked.position
FROM ranked
WHERE category.id = ranked.id AND category.display_order IS NULL;

ALTER TABLE categories
    ALTER COLUMN display_order SET DEFAULT 2147483647,
    ALTER COLUMN display_order SET NOT NULL;

DO $$
DECLARE
    category_fk record;
BEGIN
    SELECT conname, confdeltype INTO category_fk
    FROM pg_constraint
    WHERE contype = 'f'
      AND conrelid = 'reviews'::regclass
      AND confrelid = 'categories'::regclass
      AND conkey = ARRAY[(
          SELECT attnum FROM pg_attribute
          WHERE attrelid = 'reviews'::regclass AND attname = 'category_id'
      )]
      AND confkey = ARRAY[(
          SELECT attnum FROM pg_attribute
          WHERE attrelid = 'categories'::regclass AND attname = 'id'
      )];

    IF NOT FOUND THEN
        ALTER TABLE reviews ADD CONSTRAINT fk_reviews_category
            FOREIGN KEY (category_id) REFERENCES categories(id);
    ELSIF category_fk.confdeltype NOT IN ('a', 'r') THEN
        RAISE EXCEPTION 'reviews.category_id foreign key must restrict category deletion';
    ELSIF category_fk.conname <> 'fk_reviews_category' THEN
        EXECUTE format('ALTER TABLE reviews RENAME CONSTRAINT %I TO fk_reviews_category', category_fk.conname);
    END IF;
END $$;

COMMIT;
