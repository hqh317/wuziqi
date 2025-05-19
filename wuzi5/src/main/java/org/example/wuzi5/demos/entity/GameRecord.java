package org.example.wuzi5.demos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.sql.Timestamp;

@TableName("game_records")
public class GameRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String boardState;
    private Integer currentPlayer;
    private Boolean gameOver;
    private Boolean aiMode;
    @TableField(value = "created_at")
    private Timestamp createdAt;

    public GameRecord() {
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public GameRecord(Long userId, String boardState, Integer currentPlayer, Boolean gameOver, Boolean aiMode) {
        this.userId = userId;
        this.boardState = boardState;
        this.currentPlayer = currentPlayer;
        this.gameOver = gameOver;
        this.aiMode = aiMode;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBoardState() { return boardState; }
    public void setBoardState(String boardState) { this.boardState = boardState; }
    public Integer getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(Integer currentPlayer) { this.currentPlayer = currentPlayer; }
    public Boolean isGameOver() { return gameOver; }
    public void setGameOver(Boolean gameOver) { this.gameOver = gameOver; }
    public Boolean isAiMode() { return aiMode; }
    public void setAiMode(Boolean aiMode) { this.aiMode = aiMode; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}