package ai.slopshield.core

import java.lang.reflect.ParameterizedType
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * A specialized registry for caching resolved generic type arguments of core domain components.
 * 
 * In highly dynamic, reflection-based architectures like SlopShield's orchestration layer, 
 * resolving generic types (e.g., determining which event a [SlopHandler] handles) is a 
 * frequent and expensive operation. 
 * 
 * This registry eliminates the overhead by performing a one-time resolution and storing 
 * the result in a [ConcurrentHashMap]. It is designed to be thread-safe and to traverse
 * the interface hierarchy to find the requested type.
 */
object CachedTypeRegistry {
    private val cache = ConcurrentHashMap<Pair<KClass<*>, KClass<*>>, List<KClass<*>>>()

    /**
     * Resolves and returns all generic type arguments of an interface implemented by [clazz].
     * 
     * @param clazz The implementation class to inspect.
     * @param extendedClazz The interface (e.g., [SlopHandler]) whose type arguments are requested.
     * @return A list of resolved [KClass] objects for all generic type arguments.
     * @throws IllegalArgumentException if the requested interface is not found.
     */
    fun get(clazz: KClass<*>, extendedClazz: KClass<*>): List<KClass<*>> {
        return cache.getOrPut(clazz to extendedClazz) {
            val type = findGenericInterface(clazz.java, extendedClazz.java)
                ?: throw IllegalArgumentException("Class ${clazz.simpleName} does not implement ${extendedClazz.simpleName}")
            
            val typeArgs = (type as? ParameterizedType)?.actualTypeArguments
                ?: emptyArray()
            
            typeArgs.map { (it as Class<*>).kotlin }
        }
    }

    private fun findGenericInterface(target: Class<*>, search: Class<*>): java.lang.reflect.Type? {
        return target.genericInterfaces.find { 
            val rawType = (it as? ParameterizedType)?.rawType ?: it
            rawType == search 
        } ?: target.superclass?.let { findGenericInterface(it, search) }
    }
}
