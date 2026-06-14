package com.meant.api.module.merchant.service.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class UcpServiceDefinitionListDeserializer extends ValueDeserializer<List<UcpServiceDefinition>> {

    @Override
    public List<UcpServiceDefinition> deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_ARRAY) {
            return deserializeArray(parser, context);
        }
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            return deserializeObject(parser, context);
        }

        parser.skipChildren();
        return List.of();
    }

    private List<UcpServiceDefinition> deserializeArray(JsonParser parser, DeserializationContext context) throws JacksonException {
        List<UcpServiceDefinition> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.START_OBJECT) {
                values.add(context.readValue(parser, UcpServiceDefinition.class));
            } else {
                parser.skipChildren();
            }
        }
        return values;
    }

    private List<UcpServiceDefinition> deserializeObject(JsonParser parser, DeserializationContext context) throws JacksonException {
        String id = null;
        String version = null;
        UcpResourceReference spec = null;
        String transport = null;
        String endpoint = null;
        UcpResourceReference schema = null;
        List<NestedTransport> nestedTransports = new ArrayList<>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "id" -> id = stringValue(parser);
                case "version" -> version = stringValue(parser);
                case "spec" -> spec = context.readValue(parser, UcpResourceReference.class);
                case "transport" -> transport = stringValue(parser);
                case "endpoint" -> endpoint = stringValue(parser);
                case "schema" -> schema = context.readValue(parser, UcpResourceReference.class);
                case "rest", "mcp", "embedded" -> nestedTransports.add(readNestedTransport(fieldName, parser, context));
                default -> parser.skipChildren();
            }
        }

        if (!nestedTransports.isEmpty()) {
            List<UcpServiceDefinition> values = new ArrayList<>();
            for (NestedTransport nestedTransport : nestedTransports) {
                values.add(new UcpServiceDefinition(
                        id,
                        version,
                        spec,
                        nestedTransport.transport(),
                        nestedTransport.endpoint(),
                        nestedTransport.schema() == null ? schema : nestedTransport.schema()
                ));
            }
            return values;
        }

        return List.of(new UcpServiceDefinition(id, version, spec, transport, endpoint, schema));
    }

    private NestedTransport readNestedTransport(
            String transport,
            JsonParser parser,
            DeserializationContext context
    ) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren();
            return new NestedTransport(transport, null, null);
        }

        String endpoint = null;
        UcpResourceReference schema = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if ("endpoint".equals(fieldName)) {
                endpoint = stringValue(parser);
            } else if ("schema".equals(fieldName)) {
                schema = context.readValue(parser, UcpResourceReference.class);
            } else {
                parser.skipChildren();
            }
        }
        return new NestedTransport(transport, endpoint, schema);
    }

    private String stringValue(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return parser.getValueAsString();
        }
        parser.skipChildren();
        return null;
    }

    private record NestedTransport(
            String transport,
            String endpoint,
            UcpResourceReference schema
    ) {
    }
}
