    package com.intact_id.util.converter;
    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.core.type.TypeReference;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import jakarta.persistence.AttributeConverter;
    import jakarta.persistence.Converter;
    import lombok.AllArgsConstructor;
    import lombok.SneakyThrows;
    import lombok.extern.slf4j.Slf4j;

    import java.util.HashMap;
    import java.util.Map;


    /**
     * @Author frank
     * email frank.mwangi@dadanadagroup.com
     * Created on 16/11/2025 17:29
     */



    @Slf4j
    @Converter
    public class PgJsonbToMapConverter implements AttributeConverter<Map<String, Object>, String> {

        private final ObjectMapper objectMapper=new ObjectMapper();


        @Override
        @SneakyThrows
        public String convertToDatabaseColumn(Map<String, Object> attribute) {
            if (attribute == null || attribute.isEmpty()) {
                return "{}";
            }
            try {
                return objectMapper.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize : {}", attribute, e);
                return "{}";
            }
        }

        @Override
        public Map<String, Object> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.trim().isEmpty() || dbData.equals("null")) {
                return new HashMap<>();
            }
            try {
                return objectMapper.readValue(dbData, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize  JSON: {}", dbData, e);
                return new HashMap<>();
            }
        }
    }
