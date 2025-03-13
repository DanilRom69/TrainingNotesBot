CREATE TABLE users (
                       id SERIAL PRIMARY KEY,
                       chat_id BIGINT UNIQUE NOT NULL,
                       name VARCHAR(255) NOT NULL,
                       registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
