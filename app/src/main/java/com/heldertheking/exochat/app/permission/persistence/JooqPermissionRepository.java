package com.heldertheking.exochat.app.permission.persistence;

import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT;
import static com.heldertheking.exochat.db.schema.Tables.ACCOUNT_RULE;

import java.util.EnumMap;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.heldertheking.exochat.app.permission.PermissionRepository;
import com.heldertheking.exochat.app.permission.Rule;
import com.heldertheking.exochat.app.permission.RuleSet;
import com.heldertheking.exochat.app.permission.RuleState;
import com.heldertheking.exochat.app.permission.Standing;

/// Loads the platform layer from the `account` and `account_rule` tables. Values the application does not know (a
/// rule that was removed, a standing from a newer version) are ignored and logged, never an error.
@Repository
class JooqPermissionRepository implements PermissionRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JooqPermissionRepository.class);

    private final DSLContext dsl;

    JooqPermissionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<Standing> standing(UUID accountId) {
        return dsl.select(ACCOUNT.STANDING)
                .from(ACCOUNT)
                .where(ACCOUNT.ID.eq(accountId))
                .fetchOptional(ACCOUNT.STANDING)
                .map(value -> {
                    try {
                        return Standing.valueOf(value);
                    } catch (IllegalArgumentException e) {
                        // an unknown standing must not widen the rights of the account: take the most limited one
                        LOG.warn(
                                "account {} has the unknown standing '{}', treating it as SUSPENDED", accountId, value);
                        return Standing.SUSPENDED;
                    }
                });
    }

    @Override
    public RuleSet accountOverrides(UUID accountId) {
        var states = new EnumMap<Rule, RuleState>(Rule.class);
        dsl.select(ACCOUNT_RULE.RULE, ACCOUNT_RULE.STATE)
                .from(ACCOUNT_RULE)
                .where(ACCOUNT_RULE.ACCOUNT_ID.eq(accountId))
                .fetch()
                .forEach(row -> {
                    try {
                        states.put(Rule.valueOf(row.value1()), RuleState.valueOf(row.value2()));
                    } catch (IllegalArgumentException e) {
                        LOG.warn(
                                "ignoring account rule '{}'='{}' of account {}", row.value1(), row.value2(), accountId);
                    }
                });
        return RuleSet.of(states);
    }
}
