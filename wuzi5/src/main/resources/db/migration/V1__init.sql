-- Creating users table
CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       username VARCHAR(255) UNIQUE NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       role VARCHAR(50) NOT NULL
);

-- Creating game_record1 table
CREATE TABLE game_record1 (
                              id INT AUTO_INCREMENT PRIMARY KEY,
                              user_id INT NOT NULL,
                              board_state TEXT NOT NULL,
                              current_player INT NOT NULL,
                              game_over BOOLEAN NOT NULL,
                              ai_mode BOOLEAN NOT NULL,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);