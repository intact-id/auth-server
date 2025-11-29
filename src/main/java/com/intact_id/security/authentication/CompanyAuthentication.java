package com.intact_id.security.authentication;

import com.intact_id.db.model.Company;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom authentication object for API key authenticated companies
 */
@Getter
public class CompanyAuthentication implements Authentication {

    private final Company company;
    private boolean authenticated = true;

    public CompanyAuthentication(Company company) {
        this.company = company;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
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
