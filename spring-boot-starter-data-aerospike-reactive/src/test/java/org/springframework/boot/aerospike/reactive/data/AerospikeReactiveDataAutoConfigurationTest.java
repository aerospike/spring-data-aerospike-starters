/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.aerospike.reactive.data;

import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.reactor.IAerospikeReactorClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.aerospike.reactive.data.city.City;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aerospike.AerospikeAutoConfiguration;
import org.springframework.boot.autoconfigure.aerospike.AerospikeProperties;
import org.springframework.boot.autoconfigure.data.aerospike.AerospikeDataProperties;
import org.springframework.boot.client.autoconfigure.AerospikeClientConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.aerospike.convert.AerospikeConverter;
import org.springframework.data.aerospike.convert.AerospikeTypeAliasAccessor;
import org.springframework.data.aerospike.convert.MappingAerospikeConverter;
import org.springframework.data.aerospike.core.AerospikeTemplate;
import org.springframework.data.aerospike.core.ReactiveAerospikeTemplate;
import org.springframework.data.aerospike.mapping.AerospikeMappingContext;
import org.springframework.data.core.TypeInformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.aerospike.reactive.data.TestUtils.getField;
import static org.springframework.data.aerospike.config.AerospikeDataConfigurationSupport.CONFIG_PREFIX_CONNECTION;
import static org.springframework.data.aerospike.config.AerospikeDataConfigurationSupport.CONFIG_PREFIX_DATA;

/**
 * Tests for {@link AerospikeReactiveDataAutoConfiguration}.
 *
 * @author Igor Ermolenko
 * @author Anastasiia Smirnova
 */
public class AerospikeReactiveDataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AerospikeAutoConfiguration.class,
                    AerospikeReactiveDataAutoConfiguration.class, AerospikeClientConfiguration.class));

    @Test
    public void aerospikeTemplateAndClientAreNotSetupWhenNoHostsPropertyGiven() {
        assertThatThrownBy(() -> contextRunner.run(context -> context.getBean(AerospikeTemplate.class)))
                .cause()
                .hasMessageContaining("Required property 'spring.aerospike.hosts' is missing");
    }

    @Test
    public void entityScanShouldSetInitialEntitySet() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .withUserConfiguration(AerospikeTestConfigurations.EntityScanConfiguration.class)
                .run(context -> {
                    AerospikeMappingContext mappingContext = context.getBean(AerospikeMappingContext.class);
                    assertThat(mappingContext.getManagedTypes()).containsOnly(TypeInformation.of(City.class));
                });
    }

    @Test
    public void classKeyDefault() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .run(context -> {
                    AerospikeTypeAliasAccessor aliasAccessor = context.getBean(AerospikeTypeAliasAccessor.class);
                    String classKey = getField(aliasAccessor, "classKey");

                    assertThat(classKey).isEqualTo(AerospikeConverter.CLASS_KEY_DEFAULT);
                });
    }

    @Test
    public void classKeyCanBeCustomized() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".class-key=++amazing++")
                .run((context) -> {
                    AerospikeTypeAliasAccessor aliasAccessor = context.getBean(AerospikeTypeAliasAccessor.class);
                    String classKey = getField(aliasAccessor, "classKey");

                    assertThat(classKey).isEqualTo("++amazing++");
                });
    }

    @Test
    public void legacyTypeKeyCanBeUsed() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".type-key=@legacy_type")
                .run((context) -> {
                    AerospikeTypeAliasAccessor aliasAccessor = context.getBean(AerospikeTypeAliasAccessor.class);
                    String classKey = getField(aliasAccessor, "classKey");

                    assertThat(classKey).isEqualTo("@legacy_type");
                });
    }

    @Test
    public void legacyTypeKeyTakesPrecedenceOverClassKey() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".class-key=@new_class_key")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".type-key=@legacy_type_key")
                .run((context) -> {
                    AerospikeTypeAliasAccessor aliasAccessor = context.getBean(AerospikeTypeAliasAccessor.class);
                    String classKey = getField(aliasAccessor, "classKey");

                    // typeKey takes precedence for backward compatibility
                    assertThat(classKey).isEqualTo("@legacy_type_key");
                });
    }

    @Test
    public void classKeyUsedWhenLegacyTypeKeyIsEmpty() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".class-key=@my_class_key")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".type-key=")
                .run((context) -> {
                    AerospikeTypeAliasAccessor aliasAccessor = context.getBean(AerospikeTypeAliasAccessor.class);
                    String classKey = getField(aliasAccessor, "classKey");

                    // Empty typeKey falls back to classKey
                    assertThat(classKey).isEqualTo("@my_class_key");
                });
    }

    @Test
    public void classKeyUsedWhenLegacyTypeKeyNotSet() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".class-key=@my_class_key")
                .run((context) -> {
                    AerospikeTypeAliasAccessor aliasAccessor = context.getBean(AerospikeTypeAliasAccessor.class);
                    String classKey = getField(aliasAccessor, "classKey");

                    assertThat(classKey).isEqualTo("@my_class_key");
                });
    }

    @Test
    public void customConversions() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .withUserConfiguration(AerospikeTestConfigurations.CustomConversionsConfig.class)
                .run(context -> {
                    MappingAerospikeConverter converter = context.getBean(MappingAerospikeConverter.class);
                    assertThat(converter.getConversionService().canConvert(City.class, String.class)).isTrue();
                });
    }

    @Test
    public void runClientOnlyWhenNoNamespacePropertyGiven() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReactiveAerospikeTemplate.class);
                    assertThat(context).doesNotHaveBean(AerospikeTemplate.class);
                    assertThat(context).doesNotHaveBean(AerospikeDataProperties.class);
                    assertThat(context).doesNotHaveBean(AerospikeMappingContext.class);
                    assertThat(context).hasSingleBean(AerospikeProperties.class);
                    assertThat(context).hasSingleBean(IAerospikeClient.class);
                });
    }

    @Test
    public void configurationIsApplied() {
        contextRunner
                .withPropertyValues(CONFIG_PREFIX_CONNECTION + ".hosts=localhost:3000")
                .withPropertyValues(CONFIG_PREFIX_DATA + ".namespace=TEST")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AerospikeTemplate.class);
                    assertThat(context).hasSingleBean(ReactiveAerospikeTemplate.class);
                    assertThat(context).hasSingleBean(IAerospikeClient.class);
                    assertThat(context).hasSingleBean(IAerospikeReactorClient.class);
                    assertThat(context).hasSingleBean(AerospikeProperties.class);
                    assertThat(context).hasSingleBean(AerospikeDataProperties.class);
                    assertThat(context).hasSingleBean(AerospikeMappingContext.class);
                });
    }
}
