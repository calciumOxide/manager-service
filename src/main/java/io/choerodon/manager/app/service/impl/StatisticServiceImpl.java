package io.choerodon.manager.app.service.impl;

import io.choerodon.base.enums.ResourceType;
import io.choerodon.core.exception.CommonException;
import io.choerodon.manager.api.dto.MenuClickDTO;
import io.choerodon.manager.app.service.ApiService;
import io.choerodon.manager.app.service.StatisticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author superlee
 */
@Service
public class StatisticServiceImpl implements StatisticService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticServiceImpl.class);

    private StringRedisTemplate redisTemplate;

    private ApiService apiService;

    private static final String COLON = ":";

    public StatisticServiceImpl(StringRedisTemplate redisTemplate, ApiService apiService) {
        this.redisTemplate = redisTemplate;
        this.apiService = apiService;
    }

    @Override
    public void saveMenuClick(List<MenuClickDTO> menuClickList) {
        menuClickList.forEach(menuClickDTO -> assertLevel(menuClickDTO.getLevel()));
        LocalDate localDate = LocalDate.now();
        menuClickList.forEach(menuClickDTO -> {
            String level = menuClickDTO.getLevel();
            List<MenuClickDTO.Menu> menus = menuClickDTO.getMenus();
            StringBuilder builder = new StringBuilder();
            builder.append(localDate.toString()).append(COLON).append("zSet").append(COLON).append(level);
            String key = builder.toString();

            cache2Redis(menus, key);
        });
    }

    @Override
    public Map<String, Object> queryMenuClick(String beginDate, String endDate, String level) {
        assertLevel(level);
        return apiService.queryInvokeCount(beginDate, endDate, level, "menu", Collections.emptySet());
    }

    private void cache2Redis(List<MenuClickDTO.Menu> menus, String key) {
        if (redisTemplate.hasKey(key)) {
            menus.forEach(menu -> {
                int count = menu.getCount() == null ? 0 : menu.getCount();
                String value = getMenuValue(menu);
                redisTemplate.opsForZSet().incrementScore(key, value, count);
            });
        } else {
            menus.forEach(menu -> {
                int count = menu.getCount() == null ? 0 : menu.getCount();
                String value = getMenuValue(menu);
                redisTemplate.opsForZSet().add(key, value, count);
            });
            redisTemplate.expire(key, 31, TimeUnit.DAYS);
        }
    }

    private String getMenuValue(MenuClickDTO.Menu menu) {
        String code = menu.getCode();
        String name = menu.getName();
        StringBuilder builder = new StringBuilder();
        builder.append(code).append(COLON).append(name);
        return builder.toString();
    }

    private void assertLevel(String level) {
        if (StringUtils.isEmpty(level)) {
            throw new CommonException("error.menuClick.level.empty");
        }
        validateLevel(level);
    }

    private void validateLevel(String type) {
        Set<String> types = new HashSet<>();
        for (ResourceType resourceType : ResourceType.values()) {
            types.add(resourceType.value());
        }
        if (!types.contains(type)) {
            LOGGER.warn("menu level validata error {}", type);
            throw new CommonException("error.menuClick.illegal.level");
        }
    }
}