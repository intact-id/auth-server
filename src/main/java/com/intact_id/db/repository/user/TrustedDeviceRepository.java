package com.intact_id.db.repository.user;

import com.intact_id.db.model.user.TrustedDevice;
import com.intact_id.db.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, String> {

    Optional<TrustedDevice> findByDeviceTokenHash(String deviceTokenHash);

    List<TrustedDevice> findByUserAndRevokedFalseOrderByLastUsedAtDesc(User user);
}
