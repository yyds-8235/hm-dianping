package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

import static com.hmdp.constants.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;
        List<String> typeListString = stringRedisTemplate.opsForList().range(key, 0, -1);
        List<ShopType> typeList;
        if (CollUtil.isNotEmpty(typeListString)) {
            typeList = typeListString.stream().map(typeString -> {
                String[] split = typeString.split(",");
                ShopType shopType = new ShopType();
                shopType.setId(Long.valueOf(split[0]));
                shopType.setIcon(split[1]);
                shopType.setName(split[2]);
                shopType.setSort(Integer.valueOf(split[3]));
                return shopType;
            }).sorted(Comparator.comparingInt(ShopType::getSort)).toList();
            return Result.ok(typeList);
        }
        typeList = query().orderByAsc("sort").list();
        if (CollUtil.isEmpty(typeList)) {
            return Result.fail("无店铺类型");
        }
        typeListString = typeList.stream()
                .map(type ->
                        type.getId() + "," + type.getIcon() + "," + type.getName() + "," + type.getSort())
                .toList();
        stringRedisTemplate.opsForList().leftPushAll(key, typeListString);
        return Result.ok(typeList);
    }
}
