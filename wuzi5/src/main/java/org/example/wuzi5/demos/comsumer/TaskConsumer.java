package org.example.wuzi5.demos.comsumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.wuzi5.demos.entity.Task;
import org.example.wuzi5.demos.mapper.TaskMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
public class TaskConsumer {

    @Autowired
    private TaskMapper taskMapper;

    @RabbitListener(queues = "task-queue")
    public void processTask(String message) {
        System.out.println("Received task message: " + message);
        // Extract taskId from message (simplified for this example)
        Long taskId = Long.parseLong(message.split(": ")[1].split(",")[0]);
        Task task = taskMapper.selectById(taskId);
        if (task != null) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            if (now.after(task.getDeadline()) && task.getStatus().equals("PENDING")) {
                task.setStatus("FAILED");
                taskMapper.updateById(task);
                System.out.println("Task " + taskId + " marked as FAILED due to deadline.");
            }
        }
    }
}