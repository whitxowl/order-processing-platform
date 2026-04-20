package com.whitxowl.productservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.math.BigDecimal;
import java.util.List;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new BigDecimalToDoubleConverter(),
                new DoubleToBigDecimalConverter()
        ));
    }

    @WritingConverter
    static class BigDecimalToDoubleConverter implements Converter<BigDecimal, Double> {
        @Override
        public Double convert(BigDecimal source) {
            return source.doubleValue();
        }
    }

    @ReadingConverter
    static class DoubleToBigDecimalConverter implements Converter<Double, BigDecimal> {
        @Override
        public BigDecimal convert(Double source) {
            return BigDecimal.valueOf(source);
        }
    }
}