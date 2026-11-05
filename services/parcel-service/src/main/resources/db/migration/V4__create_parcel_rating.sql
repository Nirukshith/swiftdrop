-- M2 delivery rating (CU-07, parcel-read.yaml rateParcel / getParcelRating).
-- See docs/tracking-design.md section 9. parcel_id is the primary key, so a parcel can be
-- rated only once, also when two requests race. Ids are VARCHAR(36) like the parcels table.
CREATE TABLE IF NOT EXISTS parcel_rating (
    parcel_id   VARCHAR(36)              PRIMARY KEY,
    customer_id VARCHAR(36)              NOT NULL,
    score       SMALLINT                 NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
