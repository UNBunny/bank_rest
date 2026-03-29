INSERT INTO users (id, email, password, role)
VALUES (gen_random_uuid(),
        '${ADMIN_EMAIL}',
        '${ADMIN_PASSWORD_HASH}',
        'ADMIN');