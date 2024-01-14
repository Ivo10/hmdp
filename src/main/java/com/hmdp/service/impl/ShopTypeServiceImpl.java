package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ivo10
 * @since 2024-01-10
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getList() {
        //先查缓存
        String shopTypeListStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE);
        if (StrUtil.isNotBlank(shopTypeListStr)) {
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListStr, ShopType.class);
            shopTypeList.sort(Comparator.comparingInt(ShopType::getSort));
            return Result.ok(shopTypeList);
        }

        //缓存未查到，查MySQL，写入Redis
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE, JSONUtil.toJsonStr(shopTypeList));

        return Result.ok(shopTypeList);
    }
}
