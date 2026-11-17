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

    private final Company company;
    // What the caller may do: the COMPANY role plus the permissions of the key's access level
    private final List<GrantedAuthority> authorities;
    private boolean authenticated = true;

    /** A full-access caller: a full key, or a widget session the company started. */
    public CompanyAuthentication(Company company) {
        this(company, Permissions.ApiAccess.FULL);
    }

    public CompanyAuthentication(Company company, Permissions.ApiAccess access) {
        this.company = company;
        this.authorities = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("ROLE_COMPANY", "kyc:read", "kyc:write"),
                        Permissions.forApiKey(access).stream())
                .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(name))
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
