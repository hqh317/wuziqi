package org.example.wuzi5.demos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.Random;
import java.net.URLEncoder;

@Controller
public class UserAuthController {
    private static final Logger logger = LoggerFactory.getLogger(UserAuthController.class);
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public RedirectView registerUser(@RequestParam("username") String username,
                                     @RequestParam("password") String password,
                                     @RequestParam("captcha") String captcha,
                                     HttpServletRequest requestObj) {
        logger.info("Registration attempt: username={}, captcha={}", username, captcha);
        if (!isValidCaptcha(captcha, requestObj)) {
            logger.warn("Invalid captcha for username: {}", username);
            return new RedirectView("/register?error=captcha");
        }
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Empty username");
            return new RedirectView("/register?error=username_empty");
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            logger.warn("Invalid username format: {}", username);
            return new RedirectView("/register?error=username_invalid");
        }

        try {
            logger.debug("Checking if username {} exists", username);
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE username = ?", Long.class, username);
            if (count > 0) {
                logger.warn("Username already exists: {}", username);
                return new RedirectView("/register?error=username_exists");
            }

            logger.debug("Inserting new user: {}", username);
            String encodedPassword = passwordEncoder.encode(password);
            jdbcTemplate.update("INSERT INTO users (username, password, role) VALUES (?, ?, ?)",
                    username, encodedPassword, "user");

            logger.info("User registered successfully: {}", username);
            HttpSession session = requestObj.getSession();
            session.removeAttribute("captcha");
            return new RedirectView("/login");

        } catch (Exception e) {
            logger.error("Registration failed for username {}: {}", username, e.getMessage(), e);
            try {
                String errorDetails = URLEncoder.encode(e.getMessage(), "UTF-8");
                return new RedirectView("/register?error=database&details=" + errorDetails);
            } catch (java.io.UnsupportedEncodingException ex) {
                logger.error("Encoding error: {}", ex.getMessage(), ex);
                return new RedirectView("/register?error=database");
            }
        }
    }

    @GetMapping(value = "/generateCaptcha", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> generateCaptchaImage(HttpServletRequest request) throws IOException {
        String captchaCode = generateCaptcha();
        HttpSession session = request.getSession();
        session.setAttribute("captcha", captchaCode);

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = image.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        g2d.setColor(java.awt.Color.BLACK);
        g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 24));
        g2d.drawString(captchaCode, 20, 30);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        byte[] imageBytes = baos.toByteArray();

        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imageBytes);
    }

    private boolean isValidCaptcha(String captcha, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        String storedCaptcha = (String) session.getAttribute("captcha");
        return captcha != null && captcha.equals(storedCaptcha);
    }

    private String generateCaptcha() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder captcha = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            captcha.append(chars.charAt(random.nextInt(chars.length())));
        }
        return captcha.toString();
    }

    @GetMapping("/register")
    public String showRegistrationForm() {
        return "register";
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    @PostMapping("/login")
    public RedirectView loginUser(@RequestParam("username") String username,
                                  @RequestParam("password") String password,
                                  HttpServletRequest request) {
        try {
            logger.info("Login attempt for username: {}", username);
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            HttpSession session = request.getSession();
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            logger.info("Login successful for username: {}", username);
            return new RedirectView("/board");
        } catch (AuthenticationException e) {
            logger.warn("Login failed for username {}: {}", username, e.getMessage());
            return new RedirectView("/login?error=invalid_credentials");
        }
    }
}