package com.fanghu.platform.config;
import cn.dev33.satoken.interceptor.SaInterceptor; import cn.dev33.satoken.router.SaRouter; import cn.dev33.satoken.stp.*; import com.fanghu.platform.mapper.SysUserMapper; import org.springframework.context.annotation.*; import org.springframework.web.servlet.config.annotation.*; import java.util.List;
@Configuration public class SecurityConfig implements WebMvcConfigurer {
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new SaInterceptor(handle -> SaRouter.match("/api/**").notMatch("/api/auth/**","/api/health","/api/homepage","/api/topics","/api/topics/page","/api/topics/*","/api/articles","/api/articles/page","/api/articles/*","/api/categories/**","/api/solutions","/api/search","/api/storage/public/**").check(r -> StpUtil.checkLogin()))).addPathPatterns("/**");}
 @Bean public StpInterface stpInterface(SysUserMapper users){return new StpInterface(){public List<String> getPermissionList(Object id,String type){return users.permissions(Long.valueOf(id.toString()));} public List<String> getRoleList(Object id,String type){return users.roles(Long.valueOf(id.toString()));}};}
}
