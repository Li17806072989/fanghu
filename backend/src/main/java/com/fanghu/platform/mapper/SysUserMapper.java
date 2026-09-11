package com.fanghu.platform.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fanghu.platform.domain.SysUser; import org.apache.ibatis.annotations.*; import java.util.List;
public interface SysUserMapper extends BaseMapper<SysUser> {
 @Select("SELECT DISTINCT r.role_key FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.role_id WHERE ur.user_id=#{userId} AND r.status='0'") List<String> roles(Long userId);
 @Select("SELECT DISTINCT m.perms FROM sys_menu m JOIN sys_role_menu rm ON rm.menu_id=m.menu_id JOIN sys_user_role ur ON ur.role_id=rm.role_id WHERE ur.user_id=#{userId} AND m.perms IS NOT NULL AND m.perms<>''") List<String> permissions(Long userId);
 @Select("SELECT role_id FROM sys_role WHERE role_key=#{roleKey} AND status='0' LIMIT 1") Long roleId(String roleKey);
 @Insert("INSERT INTO sys_user_role(user_id,role_id) VALUES(#{userId},#{roleId})") int assignRole(Long userId, Long roleId);
}
