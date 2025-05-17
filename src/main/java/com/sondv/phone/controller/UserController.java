package com.sondv.phone.controller;

import com.sondv.phone.dto.UpdateUserRequestDTO;
import com.sondv.phone.dto.UserResponseDTO;
import com.sondv.phone.entity.Customer;
import com.sondv.phone.entity.User;
import com.sondv.phone.repository.CustomerRepository;
import com.sondv.phone.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.info("Authentication: {}", auth);

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            logger.warn("No authenticated user found");
            return ResponseEntity.ok(null); // ✅ Trả về null thay vì lỗi 403
        }

        String email = ((User) auth.getPrincipal()).getEmail();
        logger.info("Fetching user with email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.error("User not found for email: {}", email);
                    return new RuntimeException("Người dùng không tồn tại!");
                });

        UserResponseDTO userDTO = mapToUserResponseDTO(user);
        logger.info("UserDTO: {}", userDTO);
        return ResponseEntity.ok(userDTO);
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(403).body("Chưa đăng nhập!");
        }

        String email = ((User) auth.getPrincipal()).getEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource contentsAsResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", contentsAsResource);
            body.add("upload_preset", "Phone_Store");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String uploadUrl = "https://api.cloudinary.com/v1_1/dxopjponu/image/upload";
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String imageUrl = (String) response.getBody().get("secure_url");
                user.setAvatarUrl(imageUrl);
                userRepository.save(user);
                return ResponseEntity.ok(imageUrl);
            } else {
                return ResponseEntity.status(500).body("Không thể upload avatar!");
            }
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Lỗi upload: " + e.getMessage());
        }
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateCurrentUser(
            @Valid @RequestBody UpdateUserRequestDTO request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(403).build();
        }

        String email = ((User) auth.getPrincipal()).getEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));

        user.setFullName(request.getFullName());
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new RuntimeException("Số điện thoại đã được sử dụng!");
            }
            user.setPhone(request.getPhone());
        }
        user.setAddress(request.getAddress());
        user.setAvatarUrl(request.getAvatarUrl());
        userRepository.save(user);

        UserResponseDTO userDTO = mapToUserResponseDTO(user);
        return ResponseEntity.ok(userDTO);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Page<UserResponseDTO>> getUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Pageable pageable = PageRequest.of(page, size);

        Page<User> users;
        if (keyword.isBlank()) {
            users = sortDir.equalsIgnoreCase("asc")
                    ? userRepository.findAllByOrderByIdAsc(pageable)
                    : userRepository.findAllByOrderByIdDesc(pageable);
        } else {
            users = userRepository.searchUsers(keyword, pageable);
        }

        Page<UserResponseDTO> userDTOs = users.map(this::mapToUserResponseDTO);
        return ResponseEntity.ok(userDTOs);
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Page<Customer>> getCustomers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Customer> customers;
        if (keyword.isBlank()) {
            // Sử dụng phương thức sắp xếp từ repository
            customers = sortDir.equalsIgnoreCase("asc")
                    ? customerRepository.findAllByOrderByIdAsc(pageable)
                    : customerRepository.findAllByOrderByIdDesc(pageable);
        } else {
            // Tìm kiếm với từ khóa, không cần thay đổi vì repository đã có searchCustomers
            customers = customerRepository.searchCustomers(keyword, pageable);
        }

        return ResponseEntity.ok(customers);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/customers/{id}/loyalty-points")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Customer> updateLoyaltyPoints(@PathVariable Long id, @RequestBody Integer points) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khách hàng không tồn tại!"));
        customer.setLoyaltyPoints(points);
        customerRepository.save(customer);
        return ResponseEntity.ok(customer);
    }

    @DeleteMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khách hàng không tồn tại!"));
        customerRepository.delete(customer);
        return ResponseEntity.noContent().build();
    }

    private UserResponseDTO mapToUserResponseDTO(User user) {
        UserResponseDTO userDTO = new UserResponseDTO();
        userDTO.setId(user.getId());
        userDTO.setFullName(user.getFullName());
        userDTO.setEmail(user.getEmail());
        userDTO.setPhone(user.getPhone());
        userDTO.setAddress(user.getAddress());
        userDTO.setAvatarUrl(user.getAvatarUrl());
        userDTO.setProvider(user.getProvider().name());
        userDTO.setCreatedAt(user.getCreatedAt());
        userDTO.setRoles(user.getRoles().stream().map(Enum::name).collect(Collectors.toList()));
        userDTO.setVerified(user.isVerified());
        return userDTO;
    }
}