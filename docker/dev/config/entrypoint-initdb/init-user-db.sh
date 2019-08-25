#!/bin/bash

set -e

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" <<-EOSQL
	CREATE DATABASE "test-gem";
	CREATE ROLE gem SUPERUSER LOGIN CREATEDB PASSWORD 'gem';
EOSQL