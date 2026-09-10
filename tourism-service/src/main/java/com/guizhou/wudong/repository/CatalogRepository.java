package com.guizhou.wudong.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> query(String sql, Object... parameters) {
        return jdbc.queryForList(sql, parameters);
    }

    public Optional<Map<String, Object>> queryOne(String sql, Object... parameters) {
        return query(sql, parameters).stream().findFirst();
    }

    public int update(String sql, Object... parameters) {
        return jdbc.update(sql, parameters);
    }
}
