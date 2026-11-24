package com.heldertheking.resourcename;

import com.google.api.pathtemplate.PathTemplate;
import com.google.common.collect.ImmutableMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceNameTest {

    @Nested
    class SubResource {
        @Test
        void toString_should_instantiateFullPath_when_idPresent() {
            var name = TestPetName.of(7L, 42L);

            assertThat(name).hasToString("pools/7/pets/42");
            assertThat(name.getPersistenceId()).isEqualTo(42L);
            assertThat(name.isWildcard()).isFalse();
            assertThat(name.getParent().getPersistenceId()).isEqualTo(7L);
        }

        @Test
        void getTemplateVariables_should_mergeParentVariables() {
            assertThat(TestPetName.of(7L, 42L).getTemplateVariables())
                    .isEqualTo(Map.of("pool", "7", "pet", "42"));
        }

        @Test
        void isWildcard_should_beTrue_when_idMissing() {
            var name = TestPetName.wildcard(7L);

            assertThat(name.isWildcard()).isTrue();
            assertThat(name).hasToString("pools/7/pets/-");
            assertThatThrownBy(name::getPersistenceId).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void equals_and_hashCode_should_dependOnParentAndId() {
            assertThat(TestPetName.of(7L, 42L))
                    .isEqualTo(TestPetName.of(7L, 42L))
                    .hasSameHashCodeAs(TestPetName.of(7L, 42L))
                    .isNotEqualTo(TestPetName.of(7L, 43L))
                    .isNotEqualTo(TestPetName.of(8L, 42L));
        }
    }

    @Nested
    class ChildResource {
        @Test
        void toString_should_useParentVariables() {
            var settings = new TestPetSettingsName(TestPetName.of(7L, 42L));

            assertThat(settings).hasToString("pools/7/pets/42/settings");
            assertThat(settings.getParent().getPersistenceId()).isEqualTo(42L);
        }

        @Test
        void equals_should_dependOnParent() {
            assertThat(new TestPetSettingsName(TestPetName.of(7L, 42L)))
                    .isEqualTo(new TestPetSettingsName(TestPetName.of(7L, 42L)))
                    .isNotEqualTo(new TestPetSettingsName(TestPetName.of(7L, 43L)));
        }
    }

    // --- minimal concrete implementations exercising the reusable core ---

    static final class TestPoolName implements ResourceName<Long> {
        private static final PathTemplate TEMPLATE = PathTemplate.create("pools/{pool}");
        private final long id;

        TestPoolName(long id) {
            this.id = id;
        }

        @Override
        public Long getPersistenceId() {
            return id;
        }

        @Override
        public boolean isWildcard() {
            return false;
        }

        @Override
        public ImmutableMap<String, String> getTemplateVariables() {
            return ImmutableMap.of("pool", String.valueOf(id));
        }

        @Override
        public String toString() {
            return TEMPLATE.instantiate(getTemplateVariables());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestPoolName that && this.id == that.id;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(id);
        }
    }

    static final class TestPetName extends AbstractSubResourceName<TestPoolName, Long, Long> {
        private static final PathTemplate TEMPLATE = PathTemplate.create("pools/{pool}/pets/{pet}");

        private TestPetName(TestPoolName parent, @Nullable Long id) {
            super(parent, id);
        }

        static TestPetName of(long poolId, long petId) {
            return new TestPetName(new TestPoolName(poolId), petId);
        }

        static TestPetName wildcard(long poolId) {
            return new TestPetName(new TestPoolName(poolId), null);
        }

        @Override
        protected PathTemplate pathTemplate() {
            return TEMPLATE;
        }

        @Override
        protected String variableName() {
            return "pet";
        }
    }

    static final class TestPetSettingsName extends AbstractChildResourceName<TestPetName> {
        private static final PathTemplate TEMPLATE = PathTemplate.create("pools/{pool}/pets/{pet}/settings");

        TestPetSettingsName(TestPetName parent) {
            super(parent);
        }

        @Override
        protected PathTemplate pathTemplate() {
            return TEMPLATE;
        }
    }
}
