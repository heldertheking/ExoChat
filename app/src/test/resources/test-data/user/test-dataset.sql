DELETE FROM user;

INSERT INTO user (id, email, password_hash) VALUES
    ('0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a01', 'alice@example.com', 'hash-alice'),
    ('0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a02', 'bob@example.com', 'hash-bob');
