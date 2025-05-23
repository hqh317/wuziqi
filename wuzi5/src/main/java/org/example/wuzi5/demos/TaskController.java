package org.example.wuzi5.demos;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.wuzi5.demos.entity.Task;
import org.example.wuzi5.demos.entity.User;
import org.springframework.cache.annotation.CacheEvict;
import org.example.wuzi5.demos.mapper.TaskMapper;
import org.example.wuzi5.demos.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.*;

@Controller
public class TaskController {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // List tasks for the current user
    @GetMapping("/listTasks")
    @Cacheable(value = "userTasks", key = "#authentication.name")
    public ResponseEntity<List<Map<String, Object>>> listTasks(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Collections.emptyList());
        }

        String username = authentication.getName();
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Task> tasks = taskMapper.selectList(
                new QueryWrapper<Task>().eq("user_id", userId).orderByDesc("created_at")
        );

        List<Map<String, Object>> response = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("id", task.getId());
            taskData.put("description", task.getDescription());
            taskData.put("status", task.getStatus());
            taskData.put("createdAt", task.getCreatedAt().toString());
            taskData.put("deadline", task.getDeadline().toString());
            response.add(taskData);
        }
        return ResponseEntity.ok(response);
    }

    // Mark a task as completed
    @PostMapping("/completeTask")
    @CacheEvict(value = "userTasks", key = "#authentication.name")
    public ResponseEntity<Map<String, Object>> completeTask(@RequestBody Map<String, String> request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(401).body(response);
        }

        String username = authentication.getName();
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.ok(response);
        }

        String taskId = request.get("taskId");
        Task task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "任务不存在或无权限");
            return ResponseEntity.ok(response);
        }

        if (!task.getStatus().equals("PENDING")) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "任务已完成或已失效");
            return ResponseEntity.ok(response);
        }

        task.setStatus("COMPLETED");
        taskMapper.updateById(task);

        // Notify via RabbitMQ
        rabbitTemplate.convertAndSend("task-notifications", "Task completed: " + taskId + " by user: " + username);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }


    @Cacheable(value = "userIds", key = "#username")
    public Long getUserIdByUsername(String username) {
        try {
            Long userId = userMapper.findUserIdByUsername(username);
            if (userId == null) {
                System.out.println("User not found in users table: " + username);
            }
            return userId;
        } catch (Exception e) {
            System.out.println("Error fetching user_id for username: " + username);
            return null;
        }
    }
}