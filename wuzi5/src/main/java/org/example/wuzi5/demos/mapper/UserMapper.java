package org.example.wuzi5.demos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.example.wuzi5.demos.entity.User;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id FROM users WHERE username = #{username}")
    Long findUserIdByUsername(String username);
}