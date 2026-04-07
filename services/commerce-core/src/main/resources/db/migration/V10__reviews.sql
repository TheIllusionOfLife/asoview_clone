CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    product_id UUID NOT NULL REFERENCES products(id),
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    language VARCHAR(8) NOT NULL DEFAULT 'ja',
    status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    helpful_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(128),
    updated_by VARCHAR(128)
);

CREATE UNIQUE INDEX reviews_user_product_uq ON reviews(user_id, product_id);
CREATE INDEX reviews_product_status_idx ON reviews(product_id, status);

CREATE TABLE review_helpful_votes (
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    voted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (review_id, user_id)
);

CREATE TABLE product_review_aggregates (
    product_id UUID PRIMARY KEY REFERENCES products(id),
    average_rating NUMERIC(3, 2) NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
