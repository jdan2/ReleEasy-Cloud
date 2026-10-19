package com.releasy.infrastructure.dynamodb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.domain.app.App;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;
import java.util.Map;

/**
 * Elemento de la tabla {@code apps}.
 *
 * <p>Clave: partición {@code appId}. Índices globales:
 * <ul>
 *   <li>{@value #TENANT_INDEX}: partición {@code tenantId}, para listar las apps de un cliente;</li>
 *   <li>{@value #REPOSITORY_INDEX}: partición {@code repository}, para que el webhook encuentre la app.</li>
 * </ul>
 *
 * <p>Las opciones y el uso estimado se guardan como texto JSON: su forma depende de la plantilla y
 * nunca se consultan por separado.
 */
@DynamoDbBean
public class AppItem {

    public static final String TENANT_INDEX = "tenantId-index";
    public static final String REPOSITORY_INDEX = "repository-index";

    private static final ObjectMapper JSON = new ObjectMapper();

    private String appId;
    private String tenantId;
    private String name;
    private String repository;
    private String templateId;
    private String branch;
    private String optionsJson;
    private String usageJson;
    private String url;
    private Instant createdAt;
    private Instant updatedAt;

    public static AppItem from(App a) {
        AppItem item = new AppItem();
        item.appId = a.id();
        item.tenantId = a.tenantId();
        item.name = a.name();
        item.repository = a.repository();
        item.templateId = a.templateId();
        item.branch = a.branch();
        item.optionsJson = write(a.options());
        item.usageJson = write(a.usage());
        item.url = a.url();
        item.createdAt = a.createdAt();
        item.updatedAt = a.updatedAt();
        return item;
    }

    public App toDomain() {
        return new App(appId, tenantId, name, templateId, repository, branch,
                read(optionsJson, new TypeReference<Map<String, Object>>() { }),
                read(usageJson, new TypeReference<Map<String, Double>>() { }), url, createdAt, updatedAt);
    }

    private static String write(Map<String, ?> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar a JSON", e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json == null ? "{}" : json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON no válido en la tabla apps", e);
        }
    }

    @DynamoDbPartitionKey
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    @DynamoDbSecondaryPartitionKey(indexNames = TENANT_INDEX)
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @DynamoDbSecondaryPartitionKey(indexNames = REPOSITORY_INDEX)
    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }

    public String getUsageJson() { return usageJson; }
    public void setUsageJson(String usageJson) { this.usageJson = usageJson; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
