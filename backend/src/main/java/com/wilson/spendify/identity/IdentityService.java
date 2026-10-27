package com.wilson.spendify.identity;

import com.wilson.spendify.finance.AccountType;
import com.wilson.spendify.finance.FinancialAccount;
import com.wilson.spendify.finance.FinancialAccountRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class IdentityService implements UserDetailsService {
    private final AppUserRepository users;
    private final FinancialAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(AppUserRepository users, FinancialAccountRepository accounts,
                           PasswordEncoder passwordEncoder) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser register(String email, String displayName, String password) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account already exists for this email.");
        }
        AppUser user = users.save(new AppUser(email, displayName, passwordEncoder.encode(password)));
        accounts.save(new FinancialAccount(user.getId(), "PUBLIC_BANK", "Public Bank", AccountType.BANK));
        accounts.save(new FinancialAccount(user.getId(), "TNG", "TNG eWallet", AccountType.EWALLET));
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public SpendifyPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
        return new SpendifyPrincipal(user.getId(), user.getEmail(), user.getDisplayName(), user.getPasswordHash());
    }

    public AppUser requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }
}

