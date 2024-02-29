package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ivo10
 * @since 2024-01-15
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
//                this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 互斥锁解决缓存击穿
     *
     * @param id
     * @return Shop
     */
    public Shop queryWithMutex(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //情况1：查到缓存
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //情况2：查到空缓存（防止缓存穿透）
        if (shopJson != null) {
            return null;
        }

        // 未命中: 实现缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 情况3：其他线程进行缓存重建，休眠重试
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 情况4：获取锁成功，当前线程进行缓存重建
            shop = getById(id);
            // 模拟重建延迟
            Thread.sleep(200);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",
                        RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return shop;
    }

    /**
     * 获取锁
     *
     * @param key
     * @return boolean
     * @author Ivo10
     * @since 2024.01.15
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 删除锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //没有设置TTL，永久有效
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 是否需要根据坐标查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        String key = SystemConstants.SHOP_GEO_KEY + typeId;
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().limit(end));// 搜索当前位置(x, y)5km范围内的店铺
        if (results == null) {
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        List<Long> ids = new ArrayList<>();
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            Distance distance = result.getDistance();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, distance);
        });
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id,)" + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
