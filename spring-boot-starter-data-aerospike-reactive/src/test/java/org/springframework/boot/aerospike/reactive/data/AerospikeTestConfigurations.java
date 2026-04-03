package org.springframework.boot.aerospike.reactive.data;

import org.springframework.boot.aerospike.reactive.data.city.City;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.aerospike.convert.AerospikeCustomConverters;

import java.util.List;

public class AerospikeTestConfigurations {

    @AutoConfiguration
    @EntityScan("org.springframework.boot.autoconfigure.data.aerospike.city")
    public static class EntityScanConfiguration {

    }

    @AutoConfiguration
    public static class CustomConversionsConfig {

        @Bean
        AerospikeCustomConverters myCustomConversions() {
            return new AerospikeCustomConverters(List.of(new CityToStringConverter()));
        }
    }

    public static class CityToStringConverter implements Converter<City, String> {

        @Override
        public String convert(City value) {
            return value.getName();
        }
    }
}
