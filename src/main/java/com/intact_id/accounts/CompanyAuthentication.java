package com.intact_id.accounts;

import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Custom authentication object for API key authenticated companies.
 * Grants ROLE_COMPANY authority so @PreAuthorize checks can match API key clients.
 */
@Getter
public class CompanyAuthentication implements Authentication {

    // The COMPANY role plus every permission an API key holds (Permissions.API_KEY)
    private static final List<GrantedAuthority> COMPANY_AUTHORITIES = java.util.stream.Stream.concat(
            java.util.stream.Stream.of("ROLE_COMPANY", "kyc:read", "kyc:write"),
            Permissions.API_KEY.stream())
            .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(name))
            .toList();

    private final Company company;
    private boolean authenticated = true;

    public CompanyAuthentication(Company company) {
        this.company = company;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return COMPANY_AUTHORITIES;
    }

    @Override
    public Object getCredentials() {
        return null; // We don't expose credentials
    }

    @Override
    public Object getDetails() {
        return company;
    }

    @Override
    public Object getPrincipal() {
        return company;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return company.getLegalName();
    }
}
