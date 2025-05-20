package org.example.wuzi5.demos;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.wuzi5.demos.entity.Task;
import org.example.wuzi5.demos.entity.User;
import org.example.wuzi5.demos.mapper.TaskMapper;
import org.example.wuzi5.demos.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping("/admin")
    public String adminPage(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = authentication.getName();
        User user = userMapper.findUserByUsername(username);
        if (user == null || !user.getRole().equals("ADMIN")) {
            return "redirect:/board";
        }

        model.addAttribute("username", username);
        model.addAttribute("isAdmin", true);
        return "admin";
    }



    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<User> users = userMapper.selectList(null);
        List<Map<String, Object>> response = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("role", user.getRole());
            response.add(userData);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/deleteUser/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        int deleted = userMapper.deleteById(userId);
        if (deleted > 0) {
            response.put("success", true);
            response.put("message", "用户删除成功");
        } else {
            response.put("success", false);
            response.put("message", "用户删除失败");
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/createTask")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        String description = request.get("description");
        String deadlineStr = request.get("deadline");
        Long userId = Long.parseLong(request.get("userId"));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setLenient(false);
        Timestamp deadline;
        try {
            deadline = new Timestamp(sdf.parse(deadlineStr).getTime());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "无效的截止时间格式，应为 YYYY-MM-DD HH:mm:ss，例如 2025-05-20 19:08:00");
            return ResponseEntity.ok(response);
        }

        Task task = new Task();
        task.setDescription(description);
        task.setUserId(userId);
        task.setStatus("PENDING");
        task.setDeadline(deadline);
        task.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        taskMapper.insert(task);
        rabbitTemplate.convertAndSend("task-notifications", "Task created: " + task.getId() + " for userId: " + userId);

        response.put("success", true);
        return ResponseEntity.ok(response);
    }



    @GetMapping("/allTasks")
    public ResponseEntity<List<Map<String, Object>>> listAllTasks() {
        List<Task> tasks = taskMapper.selectList(null);
        List<Map<String, Object>> response = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("id", task.getId());
            taskData.put("description", task.getDescription());
            taskData.put("username", userMapper.selectById(task.getUserId()).getUsername());
            taskData.put("status", task.getStatus());
            taskData.put("deadline", task.getDeadline().toString());
            response.add(taskData);
        }
        return ResponseEntity.ok(response);
    }


}