package com.smartwealth.ai.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class OssConfig {

    @Bean
    @ConfigurationProperties(prefix = "aliyun.oss")
    public OssProperties ossProperties() {
        return new OssProperties();
    }

    @Bean
    @Lazy
    @ConditionalOnProperty(name = "aliyun.oss.access-key-id")
    public OSS ossClient(OssProperties properties) {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }

    public static class OssProperties {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }
}
