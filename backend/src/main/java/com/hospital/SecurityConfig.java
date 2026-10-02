package com.hospital;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain security(HttpSecurity http, HospitalService service) throws Exception {
        return http.csrf(c -> c.disable()) // No cookies: only explicit bearer tokens authenticate API requests.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/health", "/api/auth/login", "/api/auth/register", "/api/auth/claim").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/doctors", "/api/doctors/*", "/api/schedules").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/appointments").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/staff/**").hasAnyRole("ADMIN", "RECEPTIONIST")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> r.sendError(401))
                .accessDeniedHandler((q,r,x) -> r.sendError(403)))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                    String header = req.getHeader("Authorization");
                    if (header != null && header.startsWith("Bearer ")) {
                        Models.Account account = service.authenticate(header.substring(7));
                        if (account != null) SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(account.id, null, List.of(new SimpleGrantedAuthority("ROLE_" + account.role))));
                    }
                    chain.doFilter(req, res);
                }
            }, UsernamePasswordAuthenticationFilter.class).build();
    }
}
