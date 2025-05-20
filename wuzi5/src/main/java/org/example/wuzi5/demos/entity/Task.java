package org.example.wuzi5.demos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.sql.Timestamp;

@TableName("tasks")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String description;
    private String status; // e.g., "PENDING", "COMPLETED", "FAILED"
    @TableField(value = "created_at")
    private Timestamp createdAt;
    @TableField(value = "deadline")
    private Timestamp deadline;

    public Task() {
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public Task(Long userId, String description, String status, Timestamp deadline) {
        this.userId = userId;
        this.description = description;
        this.status = status;
        this.deadline = deadline;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getDeadline() { return deadline; }
    public void setDeadline(Timestamp deadline) { this.deadline = deadline; }
}