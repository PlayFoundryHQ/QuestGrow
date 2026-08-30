"""Versioned SQL migrations.

Each ``NNNN_name.sql`` file is applied once, in filename order, inside a
transaction, and recorded in ``schema_migrations``. Plain SQL, portable across
SQLite and PostgreSQL — see ``migrate.run``.
"""
