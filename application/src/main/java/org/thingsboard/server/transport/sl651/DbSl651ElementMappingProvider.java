package org.thingsboard.server.transport.sl651;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SL651 要素映射的数据库实现（单体内嵌模式）。
 *
 * 直查 {@code sl_element_mapping} 表——与网关（sl-gateway）共用同一张表、同一个库：
 *  - 表由网关的要素映射管理页维护（增删改/启停/导入导出），本类只读；
 *  - 网关下线后表与数据仍在 TB 库中，映射管理能力后续可迁入 TB UI。
 *
 * 缓存策略：整表载入内存，TTL 到期后惰性刷新（首次访问触发加载）；
 * 表不存在（网关从未在本库建表）时自动降级——遥测键保持原始标识符，不影响上行。
 */
@Slf4j
@Component
public class DbSl651ElementMappingProvider implements Sl651ElementMappingProvider {

    private static final String SELECT_SQL =
            "SELECT guide_byte, pollutant_code FROM sl_element_mapping WHERE enabled = true";

    private final JdbcTemplate jdbcTemplate;
    private final long cacheTtlMs;

    private final Map<Integer, String> cache = new ConcurrentHashMap<>();
    private volatile long loadedAt = 0;
    /** 表缺失时置 true，后续直接回退原始键，不再反复打库 */
    private volatile boolean tableUnavailable = false;
    private volatile boolean warnedOnce = false;

    @Autowired
    public DbSl651ElementMappingProvider(JdbcTemplate jdbcTemplate,
                                         @Value("${transport.sl651.mapping.cache_ttl_seconds:60}") long cacheTtlSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheTtlMs = Math.max(5, cacheTtlSeconds) * 1000L;
    }

    @Override
    public String getPollutantCode(int guideByte) {
        if (tableUnavailable) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - loadedAt > cacheTtlMs) {
            refresh();
        }
        return cache.get(guideByte);
    }

    /**
     * 惰性整表刷新。任何异常都不向遥测链路抛出——映射失败只意味着回退原始键。
     */
    private synchronized void refresh() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < cacheTtlMs || tableUnavailable) {
            return;
        }
        try {
            Map<Integer, String> fresh = new ConcurrentHashMap<>();
            jdbcTemplate.query(SELECT_SQL, rs -> {
                fresh.put(rs.getInt("guide_byte"), rs.getString("pollutant_code"));
            });
            cache.clear();
            cache.putAll(fresh);
            loadedAt = now;
            if (!warnedOnce) {
                log.info("SL651 要素映射已加载: {} 条（表 sl_element_mapping，TTL {}s）",
                        fresh.size(), cacheTtlMs / 1000);
                warnedOnce = true;
            } else {
                log.debug("SL651 要素映射已刷新: {} 条", fresh.size());
            }
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("does not exist") || msg.contains("不存在")) {
                tableUnavailable = true;
                log.warn("表 sl_element_mapping 不存在（本库可能从未部署网关），要素键将保持原始标识符");
            } else {
                // 保留旧缓存继续服务，避免 DB 抖动导致键全部回退
                log.error("SL651 要素映射刷新失败（沿用旧缓存 {} 条）: {}", cache.size(), msg);
            }
        }
    }
}
