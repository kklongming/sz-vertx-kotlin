package sz.scaffold.cache.redis

import com.github.benmanes.caffeine.cache.Cache
import redis.clients.jedis.JedisPubSub
import sz.scaffold.tools.logger.Logger

//
// Created by kk on 2018/10/18.
//
class L2CachePubSub(private val jedisPool: JRedisPool, private val localCache: Cache<String, String>) : JedisPubSub() {

    override fun onPMessage(pattern: String, channel: String, message: String) {
        Logger.debug("onPMessage: [pattern: $pattern] [channel: $channel] [message: $message]")
        val key = channel.split("__:").last()
        when(message) {
            in setOf("expire", "del", "evicted") -> {
                Logger.debug("remove key: '$key' from local level 2 cache")
                localCache.invalidate(key)
            }
            else -> {
                // reload from Redis
                jedisPool.jedis().use { jedis ->
                    try {
                        val value = jedis.get(key)
                        if (value != null) {
                            localCache.asMap()[key] = value
                        }
                    } catch (ex: Exception) {
                        // 获取最新的出现错误, 为了避免脏数据, 所以把 local cache 中的也删除掉
                        localCache.invalidate(key)
                    }
                }
            }
        }
    }


}