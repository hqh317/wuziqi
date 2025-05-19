package org.example.wuzi5.demos.mapper;

import org.example.wuzi5.demos.entity.GameRecord;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface GameRecordMapper extends BaseMapper<GameRecord> {
    // 移除原有的方法，使用 MyBatis-Plus 通用功能
}