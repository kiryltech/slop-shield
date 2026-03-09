package ai.slopshield.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Tests for the [CachedTypeRegistry] to ensure robust reflection and generic type resolution.
 */
class CachedTypeRegistryTest {

    interface SingleParam<T>
    interface MultiParam<T, U, V>
    interface NoParam

    class SimpleImpl : SingleParam<String>
    class MultiImpl : MultiParam<Int, String, Double>

    open class Base : SingleParam<Long>
    class InheritedImpl : Base()

    /**
     * Verifies it can resolve a single generic argument from an interface.
     */
    @Test
    fun `test resolves single generic type argument`() {
        val types = CachedTypeRegistry.get(SimpleImpl::class, SingleParam::class)
        assertEquals(1, types.size)
        assertEquals(String::class, types[0])
    }

    /**
     * Verifies it can resolve multiple generic arguments simultaneously.
     */
    @Test
    fun `test resolves multiple generic type arguments`() {
        val types = CachedTypeRegistry.get(MultiImpl::class, MultiParam::class)
        assertEquals(3, types.size)
        assertEquals(Int::class, types[0])
        assertEquals(String::class, types[1])
        assertEquals(Double::class, types[2])
    }

    /**
     * Verifies resolution works correctly even through class inheritance hierarchies.
     */
    @Test
    fun `test resolves generic type through inheritance hierarchy`() {
        val types = CachedTypeRegistry.get(InheritedImpl::class, SingleParam::class)
        assertEquals(1, types.size)
        assertEquals(Long::class, types[0])
    }

    /**
     * Verifies that resolving an interface with no generics safely returns an empty list.
     */
    @Test
    fun `test returns empty list for interface without generic parameters`() {
        class NoParamImpl : NoParam

        val types = CachedTypeRegistry.get(NoParamImpl::class, NoParam::class)
        assertEquals(0, types.size)
    }

    /**
     * Verifies that attempting to resolve an unimplemented interface fails predictably.
     */
    @Test
    fun `test throws exception when interface is not implemented`() {
        assertFailsWith<IllegalArgumentException> {
            CachedTypeRegistry.get(SimpleImpl::class, MultiParam::class)
        }
    }

    /**
     * Verifies the caching mechanism works, returning the exact same instance on subsequent calls.
     */
    @Test
    fun `test caching returns same list instance`() {
        val first = CachedTypeRegistry.get(SimpleImpl::class, SingleParam::class)
        val second = CachedTypeRegistry.get(SimpleImpl::class, SingleParam::class)
        assertSame(first, second, "Should return cached instance")
    }
}
