# Fanghu database migrations

Flyway is the only supported schema entry point for the application. Do not run
ad-hoc SQL files or create tables manually. The application migrations live in
`backend/src/main/resources/db/migration` and are applied in version order.

This directory contains the RuoYi compatibility migrations only. They are not
the migration source used by the standalone `backend` service.

For a new environment, provision the RuoYi system tables with the checked-in
`script/sql/ry_vue.sql`, then start the application. Flyway creates and upgrades
the Fanghu tables automatically.
