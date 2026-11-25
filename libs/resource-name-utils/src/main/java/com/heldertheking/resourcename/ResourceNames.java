package com.heldertheking.resourcename;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.google.api.pathtemplate.ValidationException;

/// Parses a resource name string into one of several resource name types, instead of guessing the type from the
/// string (for example with `contains("/employees/")`).
///
/// The callers pass the `fromName` methods of the resource names as method references:
/// ``````
///
/// PoolName pool = ResourceNames.match(recipient.getName(), PoolName.class)
///         .when(EmployeeName::fromName, EmployeeName::getParent)
///         .when(AgencyName::fromName, AgencyName::getParent)
///         .orElseThrow();
///
/// Name name = ResourceNames.match(recipient.getName())
///         .when(EmployeeName::fromName)
///         .when(AgencyName::fromName)
///         .orElseThrow();
/// ```
/// ```
///
/// The types are tried in order and the first one that parses wins. Wildcard names such as
/// `employees/-` parse as well; use [ResourceName#isWildcard()] in a mapping if they must be rejected.
public final class ResourceNames {

    private ResourceNames() {}

    /// Tries resource name types in order. A branch without a mapping returns the parsed name.
    public static Matcher<Name> match(String name) {
        return new Matcher<>(name);
    }

    /// Like [#match(String)], for a result that is not a plain name (a parent, an id, ...).
    public static <R> Matcher<R> match(String name, @NonNull Class<R> resultType) {
        return new Matcher<>(name);
    }

    /// Single use: build it, evaluate it, and drop it.
    ///
    /// @param <R> the type of the result
    public static final class Matcher<R> {
        private final String name;
        private final List<IllegalArgumentException> failures = new ArrayList<>();
        private boolean matched;
        private @Nullable R result;

        private Matcher(String name) {
            this.name = Objects.requireNonNull(name);
        }

        /// The parsed name is the result, so it must be an R (checked by the compiler).
        public <N extends R> Matcher<R> when(Function<String, N> parser) {
            return when(parser, Function.identity());
        }

        /// Tries to parse the name. If that succeeds, and no earlier branch matched, `then` maps the
        /// parsed name to the result. Only an [IllegalArgumentException] (for example a
        /// [ValidationException] or a [NumberFormatException]) of the parser counts as "does not
        /// parse"; an exception of the mapping, or any other exception of the parser, propagates.
        public <N> Matcher<R> when(Function<String, N> parser, Function<? super N, ? extends R> then) {
            if (matched) {
                return this;
            }
            N parsed;
            try {
                parsed = parser.apply(name);
            } catch (IllegalArgumentException e) { // ValidationException, NumberFormatException
                failures.add(e);
                return this;
            }
            // outside the try: an exception in the mapping is a bug, not "try the next type"
            result = then.apply(parsed);
            matched = true;
            return this;
        }

        /// The result of the first matching branch, or empty if none matched (or its mapping returned null).
        public Optional<R> result() {
            return matched ? Optional.ofNullable(result) : Optional.empty();
        }

        /// The result of the first matching branch.
        ///
        /// @throws ValidationException if no branch matched; the failures of all branches are
        ///         suppressed exceptions
        public R orElseThrow() {
            if (matched) {
                return Objects.requireNonNull(result, "the mapping of the matching branch returned null");
            }
            var e = new ValidationException("'%s' matches none of the %d resource name types", name, failures.size());
            failures.forEach(e::addSuppressed);
            throw e;
        }
    }
}
