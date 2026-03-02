#!/bin/bash
docker exec -i amp-postgres psql -U amp -d amp < "$(dirname "$0")/db-seed.sql"
echo "Seed data applied."
