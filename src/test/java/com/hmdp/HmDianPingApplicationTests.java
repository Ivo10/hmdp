package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SystemConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testSaveShop() {
        for (long i = 1L; i <= 14L; i++) {
            shopService.saveShop2Redis(i, 10L);
        }
    }

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id=" + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time=" + (end - begin));
    }

    @Test
    public void loadShopData() {
        //查询店铺信息
        List<Shop> list = shopService.list();
        //把店铺信息按照店铺类型（type_id）分组，type_id一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //分批写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = SystemConstants.SHOP_GEO_KEY + typeId;
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
//            for (Shop shop : shops) {
//                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
//            }
        }
    }
}
