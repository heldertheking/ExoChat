package com.heldertheking.resourcename;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.api.pathtemplate.PathTemplate;
import com.google.common.collect.ImmutableMap;

class ResourceNotFoundExceptionTest {

    @Nested
    class When_constructed_with_Name {

        @Test
        void message_should_contain_the_resource_name_string() {
            var name = new TestPoolName(42);

            var exception = new ResourceNotFoundException(name);

            assertThat(exception.getMessage()).isEqualTo("object pools/42 has not been found");
        }

        @Test
        void message_should_contain_the_full_path_for_a_nested_resource_name() {
            var name = TestPetName.of(15, 1);

            var exception = new ResourceNotFoundException(name);

            assertThat(exception.getMessage()).isEqualTo("object pools/15/pets/1 has not been found");
        }

        @Test
        void getResourceName_should_return_the_provided_name() {
            var name = new TestPoolName(42);

            var exception = new ResourceNotFoundException(name);

            assertThat(exception.getResourceName()).isEqualTo(name);
        }

        @Test
        void should_be_a_RuntimeException() {
            var exception = new ResourceNotFoundException(new TestPoolName(1));

            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class When_constructed_with_message_and_ResourceName {

        @Test
        void message_should_combine_custom_message_and_resource_name_string() {
            var name = new TestPoolName(7);

            var exception = new ResourceNotFoundException("Pool is archived", name);

            assertThat(exception.getMessage()).isEqualTo("Pool is archived (resource: pools/7)");
        }

        @Test
        void getResourceName_should_return_the_provided_resource_name() {
            var name = new TestPoolName(7);

            var exception = new ResourceNotFoundException("Pool is archived", name);

            assertThat(exception.getResourceName()).isEqualTo(name);
        }

        @Test
        void should_be_a_RuntimeException() {
            var exception = new ResourceNotFoundException("some message", new TestPoolName(1));

            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }

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

        private TestPetName(TestPoolName parent, Long id) {
            super(parent, id);
        }

        static TestPetName of(long poolId, long petId) {
            return new TestPetName(new TestPoolName(poolId), petId);
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
}
