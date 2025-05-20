package org.example.wuzi5.demos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.wuzi5.demos.entity.User;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id, username, password, role, created_at FROM users WHERE username = #{username}")
    User findUserByUsername(String username);

    @Select("SELECT id FROM users WHERE username = #{username}")
    Long findUserIdByUsername(String username);
}