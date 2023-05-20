package com.heima.schedule.test;

import com.heima.model.schedule.dtos.Task;
import com.heima.schedule.ScheduleApplication;
import com.heima.schedule.service.TaskService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@SpringBootTest(classes = ScheduleApplication.class)
@RunWith(SpringRunner.class)
public class TaskServiceImpl {
    @Autowired
    private TaskService taskService;
    @Test
    public void addTask(){
        Task task=new Task();
        task.setTaskType(100);
        task.setPriority(50);
        task.setParameters("task test2".getBytes(StandardCharsets.UTF_8));
        task.setExecuteTime(new Date().getTime());
        long l = taskService.addTask(task);
        System.out.println(l);

    }
    @Test
    public void cancelTest(){
        taskService.cancelTask(1658782583963521025L);
    }

}
