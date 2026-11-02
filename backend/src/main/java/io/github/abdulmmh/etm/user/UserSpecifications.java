package io.github.abdulmmh.etm.user;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> matching(String search, Role role, Boolean active) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                String escaped = search.strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("fullName")), pattern, '\\'),
                    builder.like(builder.lower(root.get("email")), pattern, '\\')));
            }
            if (role != null) {
                predicates.add(builder.equal(root.get("role"), role));
            }
            if (active != null) {
                predicates.add(builder.equal(root.get("active"), active));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
