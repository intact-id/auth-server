package com.intact_id.db.repository.user;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndCompany_Id(String email, String companyId);

    boolean existsByEmailAndCompanyIsNull(String email);

    Page<User> findByStatus(GlobalStatuses status, Pageable pageable);

    @Query(value = "SELECT * FROM users u WHERE " +
            "(:companyId IS NULL OR u.company_id = :companyId) AND " +
            "(:status IS NULL OR u.status = :status) AND " +
            "(:search IS NULL OR (" +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%'))))",
            countQuery = "SELECT COUNT(*) FROM users u WHERE " +
                    "(:companyId IS NULL OR u.company_id = :companyId) AND " +
                    "(:status IS NULL OR u.status = :status) AND " +
                    "(:search IS NULL OR (" +
                    "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                    "LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%'))))",
            nativeQuery = true)
    Page<User> findByFilters(
            @Param("companyId") String companyId,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);
}
