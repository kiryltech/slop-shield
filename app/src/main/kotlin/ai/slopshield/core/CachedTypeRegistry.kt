package ai.slopshield.core

import java.lang.reflect.ParameterizedType
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Registry to cache event types for SlopHandlers to avoid expensive reflection.
 */
object CachedTypeRegistry {
    private val cache = ConcurrentHashMap<KClass<*>, KClass<*>>()

    fun get(clazz: KClass<*>): KClass<*> {
        return cache.getOrPut(clazz) {
            val type = clazz.java.genericInterfaces
                .filterIsInstance<ParameterizedType>()
                .first { it.rawType == SlopHandler::class.java }
                .actualTypeArguments[0]
            
            (type as Class<*>).kotlin
        }
    }
}
