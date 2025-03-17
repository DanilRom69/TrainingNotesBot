CREATE TABLE users (
                       id SERIAL PRIMARY KEY,
                       chat_id BIGINT UNIQUE NOT NULL,
                       name VARCHAR(255) NOT NULL,
                       registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE exercises (
                           id SERIAL PRIMARY KEY,
                           chat_id BIGINT NOT NULL REFERENCES users(chat_id),
                           exercise_name VARCHAR(255) NOT NULL,
                           weight INT NOT NULL,
                           repetitions INT NOT NULL,
                           sets_count INT DEFAULT 0,
                           rest_time INT NOT NULL,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE body_parameters (
                                 id SERIAL PRIMARY KEY,
                                 chat_id BIGINT NOT NULL REFERENCES users(chat_id),
                                 date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 height INT NOT NULL,
                                 weight INT NOT NULL,
                                 biceps INT NOT NULL,
                                 chest INT NOT NULL,
                                 waist INT NOT NULL,
                                 hips INT NOT NULL,
                                 thighs INT NOT NULL,
                                 calves INT NOT NULL,
                                 shoulders INT NOT NULL,
                                 buttocks INT NOT NULL
);