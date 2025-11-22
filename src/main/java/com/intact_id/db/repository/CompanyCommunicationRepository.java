package com.intact_id.db.repository;

import com.intact_id.db.model.CompanyCommunicationDetails;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 14:16
 */

public interface CompanyCommunicationRepository extends JpaRepository<CompanyCommunicationDetails,String> {
}
