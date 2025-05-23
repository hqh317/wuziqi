package org.example.wuzi5.demos.config;

import org.example.wuzi5.demos.BoardController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import javax.sql.DataSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.context.ApplicationContext;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final ApplicationContext applicationContext;

    // 构造函数注入 ApplicationContext
    public SecurityConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests()
                .antMatchers("/", "/register", "/login", "/generateCaptcha", "/css/**", "/js/**", "/makeMove", "/toggleAIMode","/board", "/resetGame").permitAll()
                .antMatchers("/saveGame", "/loadGame", "/listGames", "/deleteGame").authenticated()
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/board", true)
                .permitAll()
                .and()
                .logout((logout) -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            String username = authentication != null ? authentication.getName() : null;
                            BoardController boardController = applicationContext.getBean(BoardController.class);
                            boardController.logoutCleanup(username);
                            request.getSession().invalidate();
                            SecurityContextHolder.clearContext(); // 清理 Security 上下文
                            response.sendRedirect("/login");
                        })
                        .invalidateHttpSession(true) // 确保注销时清理会话
                        .deleteCookies("JSESSIONID") // 删除会话 cookie
                        .permitAll()
                );
       //http.sessionManagement((session) -> session
                //.maximumSessions(1)
                //.maxSessionsPreventsLogin(true)
        //);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {
        JdbcUserDetailsManager users = new JdbcUserDetailsManager(dataSource);
        users.setUsersByUsernameQuery("SELECT username, password, 1 as enabled FROM users WHERE username = ?");
        users.setAuthoritiesByUsernameQuery("SELECT username, role FROM users WHERE username = ?");
        return users;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}